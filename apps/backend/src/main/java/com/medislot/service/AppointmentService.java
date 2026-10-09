package com.medislot.service;

import com.medislot.dto.AppointmentForm;
import com.medislot.entity.Appointment;
import com.medislot.entity.AppointmentStatus;
import com.medislot.entity.Doctor;
import com.medislot.entity.Schedule;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.repository.AppointmentRepository;
import com.medislot.repository.DoctorRepository;
import com.medislot.repository.ScheduleRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约业务：提交、我的预约、取消，以及医生端的状态流转。
 */
@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final DoctorRepository doctorRepository;
    private final UserService userService;
    private final PaymentService paymentService;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              ScheduleRepository scheduleRepository,
                              DoctorRepository doctorRepository,
                              UserService userService,
                              PaymentService paymentService) {
        this.appointmentRepository = appointmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.doctorRepository = doctorRepository;
        this.userService = userService;
        this.paymentService = paymentService;
    }

    /**
     * 提交预约。校验、扣号源、写记录在同一事务内完成。
     */
    @Transactional
    public Appointment create(AppointmentForm form, String patientPhone) {
        User patient = userService.findByPhone(patientPhone);
        Schedule schedule = scheduleRepository.findById(form.getScheduleId())
                .orElseThrow(() -> new BusinessException("排班不存在"));

        LocalDateTime slotStart = LocalDateTime.of(schedule.getDate(), schedule.getStartTime());
        if (slotStart.isBefore(LocalDateTime.now())) {
            throw new BusinessException("该时段已过期，请选择其他时段");
        }
        if (!schedule.isAvailable()) {
            throw new BusinessException("手慢了，该号源已被约满");
        }
        boolean duplicated = appointmentRepository.existsByPatientIdAndScheduleIdAndStatusNot(
                patient.getId(), schedule.getId(), AppointmentStatus.CANCELLED);
        if (duplicated) {
            throw new BusinessException("您已预约该时段，请勿重复预约");
        }

        // 扣号源：@Version 乐观锁，冲突时抛 OptimisticLockingFailureException
        schedule.setBookedCount(schedule.getBookedCount() + 1);
        try {
            scheduleRepository.saveAndFlush(schedule);
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException("手慢了，该号源已被其他患者抢走");
        }

        String no = generateAppointmentNo(schedule.getDate());
        Appointment appointment = new Appointment(no, schedule, patient, form.getReason());
        // 挂号费快照
        appointment.setRegistrationFee(schedule.getDoctor().getRegistrationFee());
        Appointment saved = appointmentRepository.save(appointment);
        // 创建待支付订单（30 分钟内未支付则作废、释放号源）
        paymentService.createForAppointment(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Appointment> listMine(String patientPhone) {
        User patient = userService.findByPhone(patientPhone);
        return appointmentRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId());
    }

    @Transactional(readOnly = true)
    public Appointment get(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("预约不存在"));
    }

    @Transactional
    public void cancel(Long id, String patientPhone) {
        Appointment appointment = get(id);
        if (!appointment.getPatient().getPhone().equals(patientPhone)) {
            throw new BusinessException("无权操作他人的预约");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BusinessException("当前状态不可取消");
        }
        appointment.setStatus(AppointmentStatus.CANCELLED);
        // 取消后把号源放回去
        Schedule schedule = appointment.getSchedule();
        if (schedule.getBookedCount() > 0) {
            schedule.setBookedCount(schedule.getBookedCount() - 1);
            scheduleRepository.save(schedule);
        }
        appointmentRepository.save(appointment);
        // 已支付→自动退款；未支付→订单作废
        paymentService.voidOrRefund(appointment);
    }

    // ===== 医生端 =====

    @Transactional(readOnly = true)
    public List<Appointment> listDoctorToday(String doctorPhone) {
        Doctor doctor = resolveDoctor(doctorPhone);
        return appointmentRepository.findByScheduleDoctorIdAndScheduleDateOrderByScheduleStartTimeAsc(
                doctor.getId(), LocalDate.now());
    }

    @Transactional
    public void checkIn(Long id, String doctorPhone) {
        Appointment appointment = get(id);
        assertOwnedByDoctor(appointment, doctorPhone);
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BusinessException("只有“待就诊”的预约才能标记为已到诊");
        }
        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointmentRepository.save(appointment);
    }

    @Transactional
    public void complete(Long id, String doctorPhone, String diagnosisNote) {
        Appointment appointment = get(id);
        assertOwnedByDoctor(appointment, doctorPhone);
        if (appointment.getStatus() != AppointmentStatus.CHECKED_IN) {
            throw new BusinessException("只有“就诊中”的预约才能标记为已完成");
        }
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setDiagnosisNote(diagnosisNote);
        appointmentRepository.save(appointment);
    }

    private Doctor resolveDoctor(String doctorPhone) {
        User user = userService.findByPhone(doctorPhone);
        return doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("当前账号不是医生"));
    }

    private void assertOwnedByDoctor(Appointment appointment, String doctorPhone) {
        String ownerPhone = appointment.getSchedule().getDoctor().getUser().getPhone();
        if (!ownerPhone.equals(doctorPhone)) {
            throw new BusinessException("无权操作他人的预约");
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong APPOINTMENT_SEQ =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() % 1_000_000);

    private String generateAppointmentNo(LocalDate date) {
        String day = date.toString().replace("-", "");
        long seq = APPOINTMENT_SEQ.incrementAndGet() % 1_000_000;
        return "A" + day + "-" + String.format("%06d", seq);
    }
}
