package com.medislot.service;

import com.medislot.dto.DoctorCard;
import com.medislot.entity.Department;
import com.medislot.entity.Doctor;
import com.medislot.entity.Schedule;
import com.medislot.exception.BusinessException;
import com.medislot.repository.DepartmentRepository;
import com.medislot.repository.DoctorRepository;
import com.medislot.repository.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public DoctorService(DoctorRepository doctorRepository,
                         DepartmentRepository departmentRepository,
                         ScheduleRepository scheduleRepository) {
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.scheduleRepository = scheduleRepository;
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
