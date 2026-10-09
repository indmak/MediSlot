package com.medislot.payment;

import com.medislot.dto.AppointmentForm;
import com.medislot.entity.Appointment;
import com.medislot.entity.AppointmentStatus;
import com.medislot.entity.Department;
import com.medislot.entity.Doctor;
import com.medislot.entity.Payment;
import com.medislot.entity.PaymentStatus;
import com.medislot.entity.Role;
import com.medislot.entity.Schedule;
import com.medislot.entity.User;
import com.medislot.repository.AppointmentRepository;
import com.medislot.repository.DepartmentRepository;
import com.medislot.repository.DoctorRepository;
import com.medislot.repository.PaymentRepository;
import com.medislot.repository.ScheduleRepository;
import com.medislot.repository.UserRepository;
import com.medislot.service.AppointmentService;
import com.medislot.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付流程测试：下单生成待支付订单、支付、超时作废释放号源、取消自动退款。
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentFlowTest {

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ScheduleRepository scheduleRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Doctor createDoctor(String suffix, String fee) {
        Department dept = departmentRepository.save(new Department("支付测试科" + suffix, 200));
        User user = userRepository.save(new User("136" + suffix + "000", passwordEncoder.encode("x"), "支付医生" + suffix, Role.DOCTOR));
        Doctor doctor = new Doctor(user, dept, "主治医师", "测试");
        doctor.setRegistrationFee(new BigDecimal(fee));
        return doctorRepository.save(doctor);
    }

    private User createPatient(String suffix) {
        return userRepository.save(new User("135" + suffix + "000", passwordEncoder.encode("x"), "支付患者" + suffix, Role.PATIENT));
    }

    private Schedule createSchedule(Doctor doctor) {
        return scheduleRepository.save(new Schedule(doctor, LocalDate.now().plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 3));
    }

    private AppointmentForm formFor(Schedule schedule) {
        AppointmentForm form = new AppointmentForm();
        form.setScheduleId(schedule.getId());
        form.setPatientName("测试患者");
        form.setPatientPhone("13500000000");
        return form;
    }

    @Test
    void bookingCreatesUnpaidPaymentAndPayMarksPaid() {
        Doctor doctor = createDoctor("10001", "30.00");
        User patient = createPatient("10001");
        Schedule schedule = createSchedule(doctor);

        AppointmentForm form = formFor(schedule);
        form.setPatientPhone(patient.getPhone());
        Appointment appointment = appointmentService.create(form, patient.getPhone());

        Payment payment = paymentRepository.findByAppointmentId(appointment.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(payment.getAmount()).isEqualByComparingTo("30.00");
        assertThat(appointment.getRegistrationFee()).isEqualByComparingTo("30.00");

        paymentService.pay(payment.getPaymentNo(), patient.getPhone());
        assertThat(paymentRepository.findByAppointmentId(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void unpaidOverdueExpiresAndReleasesSlot() {
        Doctor doctor = createDoctor("10002", "20.00");
        User patient = createPatient("10002");
        Schedule schedule = createSchedule(doctor);
        int bookedBefore = scheduleRepository.findById(schedule.getId()).orElseThrow().getBookedCount();

        AppointmentForm form = formFor(schedule);
        form.setPatientPhone(patient.getPhone());
        Appointment appointment = appointmentService.create(form, patient.getPhone());

        Payment payment = paymentRepository.findByAppointmentId(appointment.getId()).orElseThrow();
        payment.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        paymentRepository.save(payment);

        int expired = paymentService.expireOverdue();
        assertThat(expired).isGreaterThanOrEqualTo(1);

        assertThat(paymentRepository.findByAppointmentId(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(scheduleRepository.findById(schedule.getId()).orElseThrow().getBookedCount())
                .isEqualTo(bookedBefore);
    }

    @Test
    void cancelPaidAppointmentRefundsAndReleasesSlot() {
        Doctor doctor = createDoctor("10003", "50.00");
        User patient = createPatient("10003");
        Schedule schedule = createSchedule(doctor);
        int bookedBefore = scheduleRepository.findById(schedule.getId()).orElseThrow().getBookedCount();

        AppointmentForm form = formFor(schedule);
        form.setPatientPhone(patient.getPhone());
        Appointment appointment = appointmentService.create(form, patient.getPhone());
        Payment payment = paymentRepository.findByAppointmentId(appointment.getId()).orElseThrow();
        paymentService.pay(payment.getPaymentNo(), patient.getPhone());

        appointmentService.cancel(appointment.getId(), patient.getPhone());

        assertThat(paymentRepository.findByAppointmentId(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(scheduleRepository.findById(schedule.getId()).orElseThrow().getBookedCount())
                .isEqualTo(bookedBefore);
    }
}
