package com.medislot.repository;

import com.medislot.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(
            Long doctorId, LocalDate from, LocalDate to);

    /** 只取还有余号的排班。 */
    @Query("""
            select s from Schedule s
            where s.doctor.id = :doctorId
              and s.date between :from and :to
              and s.bookedCount < s.totalCount
            order by s.date asc, s.startTime asc
            """)
    List<Schedule> findAvailable(@Param("doctorId") Long doctorId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    boolean existsByDoctorIdAndDateAndStartTime(Long doctorId, LocalDate date, LocalTime startTime);

    List<Schedule> findByDoctorIdOrderByDateDescStartTimeAsc(Long doctorId);
}
