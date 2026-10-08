package com.medislot.repository;

import com.medislot.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    List<Doctor> findByDepartmentId(Long departmentId);

    Optional<Doctor> findByUserId(Long userId);

    long countByDepartmentId(Long departmentId);
}
