package com.medislot.repository;

import com.medislot.entity.Appointment;
import com.medislot.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<Appointment> findByPatientIdAndStatusOrderByCreatedAtDesc(Long patientId, AppointmentStatus status);

    /** 同一患者对同一排班是否已有未取消的预约。 */
    boolean existsByPatientIdAndScheduleIdAndStatusNot(Long patientId, Long scheduleId, AppointmentStatus status);

    List<Appointment> findByScheduleDoctorIdAndScheduleDateOrderByScheduleStartTimeAsc(Long doctorId, LocalDate date);

    long countByScheduleDoctorIdAndScheduleDate(Long doctorId, LocalDate date);

    Optional<Appointment> findByAppointmentNo(String appointmentNo);
}
