package com.medislot.service;

import com.medislot.dto.DoctorAdminRow;
import com.medislot.dto.DoctorCard;
import com.medislot.dto.DoctorForm;
import com.medislot.entity.Department;
import com.medislot.entity.Doctor;
import com.medislot.entity.Role;
import com.medislot.entity.Schedule;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.repository.DepartmentRepository;
import com.medislot.repository.DoctorRepository;
import com.medislot.repository.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 医生 / 科室业务。
 */
@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final UserService userService;

    public DoctorService(DoctorRepository doctorRepository,
                         DepartmentRepository departmentRepository,
                         ScheduleRepository scheduleRepository,
                         UserService userService) {
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<Department> listDepartments() {
        return departmentRepository.findAllByOrderBySortOrderAsc();
    }

    /**
     * 医生列表（可按科室过滤），组装成卡片视图。
     */
    @Transactional(readOnly = true)
    public List<DoctorCard> listDoctors(Long departmentId) {
        List<Doctor> doctors = (departmentId == null)
                ? doctorRepository.findAll()
                : doctorRepository.findByDepartmentId(departmentId);
        return doctors.stream().map(this::toCard).toList();
    }

    /** 管理后台医生列表行。 */
    @Transactional(readOnly = true)
    public List<DoctorAdminRow> listAdminRows() {
        return doctorRepository.findAll().stream()
                .map(d -> new DoctorAdminRow(
                        d.getId(),
                        d.getName(),
                        d.getUser().getPhone(),
                        d.getTitle(),
                        d.getDepartment().getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Doctor getDoctor(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new BusinessException("医生不存在：" + id));
    }

    @Transactional(readOnly = true)
    public DoctorCard getDoctorCard(Long id) {
        return toCard(getDoctor(id));
    }

    @Transactional(readOnly = true)
    public long countDoctors(Long departmentId) {
        return (departmentId == null) ? doctorRepository.count() : doctorRepository.countByDepartmentId(departmentId);
    }

    /**
     * 管理员新增医生：同时创建登录账号（DOCTOR 角色）与医生资料。
     */
    @Transactional
    public Doctor createDoctor(DoctorForm form) {
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            throw new BusinessException("请设置初始密码");
        }
        User user = userService.createStaff(form.getPhone(), form.getPassword(), form.getName(), Role.DOCTOR);
        Department department = departmentRepository.findById(form.getDepartmentId())
                .orElseThrow(() -> new BusinessException("科室不存在"));
        Doctor doctor = new Doctor(user, department, form.getTitle(), form.getBio());
        doctor.setRating(new BigDecimal("5.0"));
        doctor.setAppointmentCount(0);
        return doctorRepository.save(doctor);
    }

    /**
     * 管理员编辑医生资料（不改手机号 / 密码）。
     */
    @Transactional
    public void updateDoctor(Long id, DoctorForm form) {
        Doctor doctor = getDoctor(id);
        Department department = departmentRepository.findById(form.getDepartmentId())
                .orElseThrow(() -> new BusinessException("科室不存在"));
        doctor.setDepartment(department);
        doctor.setTitle(form.getTitle());
        doctor.setBio(form.getBio());
        doctor.getUser().setName(form.getName());
        doctorRepository.save(doctor);
    }

    private DoctorCard toCard(Doctor doctor) {
        LocalDate today = LocalDate.now();
        List<Schedule> todaySchedules =
                scheduleRepository.findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(
                        doctor.getId(), today, today);
        int remainingToday = todaySchedules.stream().mapToInt(Schedule::getRemaining).sum();
        return new DoctorCard(
                doctor.getId(),
                doctor.getName(),
                doctor.getTitle(),
                doctor.getDepartment().getName(),
                doctor.getRating(),
                doctor.getAppointmentCount(),
                doctor.getAvatarUrl(),
                remainingToday > 0,
                remainingToday
        );
    }
}
