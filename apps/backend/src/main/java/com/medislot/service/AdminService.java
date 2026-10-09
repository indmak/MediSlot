package com.medislot.service;

import com.medislot.dto.DashboardStats;
import com.medislot.entity.AppointmentStatus;
import com.medislot.entity.Role;
import com.medislot.repository.AppointmentRepository;
import com.medislot.repository.DepartmentRepository;
import com.medislot.repository.DoctorRepository;
import com.medislot.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 管理后台业务（统计等）。
 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final AppointmentRepository appointmentRepository;

    public AdminService(UserRepository userRepository,
                        DoctorRepository doctorRepository,
                        DepartmentRepository departmentRepository,
                        AppointmentRepository appointmentRepository) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.departmentRepository = departmentRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStats stats() {
        LocalDate today = LocalDate.now();
        return new DashboardStats(
                doctorRepository.count(),
                userRepository.countByRole(Role.PATIENT),
                departmentRepository.count(),
                appointmentRepository.countByScheduleDate(today),
                appointmentRepository.countByStatus(AppointmentStatus.PENDING),
                appointmentRepository.count()
        );
    }
}
