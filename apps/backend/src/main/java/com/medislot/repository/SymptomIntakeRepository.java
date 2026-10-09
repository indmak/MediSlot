package com.medislot.repository;

import com.medislot.entity.SymptomIntake;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SymptomIntakeRepository extends JpaRepository<SymptomIntake, Long> {

    Optional<SymptomIntake> findByAppointmentId(Long appointmentId);
}
