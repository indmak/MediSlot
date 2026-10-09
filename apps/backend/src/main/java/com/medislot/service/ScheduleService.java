package com.medislot.service;

import com.medislot.dto.ScheduleDayView;
import com.medislot.dto.ScheduleForm;
import com.medislot.entity.Doctor;
import com.medislot.entity.Schedule;
import com.medislot.exception.BusinessException;
import com.medislot.repository.DoctorRepository;
import com.medislot.repository.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 排班 / 号源查询业务。
 */
@Service
public class ScheduleService {

    /** 医生详情页默认展示未来 7 天。 */
    public static final int DEFAULT_DAYS = 7;

    private final ScheduleRepository scheduleRepository;
    private final DoctorRepository doctorRepository;

    public ScheduleService(ScheduleRepository scheduleRepository, DoctorRepository doctorRepository) {
        this.scheduleRepository = scheduleRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public List<Schedule> listByDoctor(Long doctorId, LocalDate from, int days) {
        LocalDate to = from.plusDays(days - 1L);
        return scheduleRepository.findByDoctorIdAndDateBetweenOrderByDateAscStartTimeAsc(doctorId, from, to);
    }

    @Transactional(readOnly = true)
    public List<Schedule> listAvailableByDoctor(Long doctorId, LocalDate from, int days) {
        LocalDate to = from.plusDays(days - 1L);
        return scheduleRepository.findAvailable(doctorId, from, to);
    }

    /**
     * 按天分组（上午 / 下午），供医生详情页渲染。
     */
    @Transactional(readOnly = true)
    public List<ScheduleDayView> listGroupedByDay(Long doctorId, LocalDate from, int days) {
        List<Schedule> all = listByDoctor(doctorId, from, days);
        Map<LocalDate, List<Schedule>> byDate = all.stream()
                .collect(Collectors.groupingBy(Schedule::getDate, TreeMap::new, Collectors.toList()));
        return byDate.entrySet().stream()
                .map(e -> new ScheduleDayView(
                        e.getKey(),
                        e.getValue().stream().filter(s -> s.getStartTime().isBefore(LocalTime.NOON)).toList(),
                        e.getValue().stream().filter(s -> !s.getStartTime().isBefore(LocalTime.NOON)).toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Schedule getRequired(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("排班不存在：" + id));
    }

    /** 医生今天是否还有余号。 */
    @Transactional(readOnly = true)
    public boolean hasAvailableToday(Long doctorId) {
        LocalDate today = LocalDate.now();
        return scheduleRepository.findAvailable(doctorId, today, today).stream().findAny().isPresent();
    }

    /** 管理员查看某医生的全部排班（按日期倒序）。 */
    @Transactional(readOnly = true)
    public List<Schedule> listByDoctorForAdmin(Long doctorId) {
        return scheduleRepository.findByDoctorIdOrderByDateDescStartTimeAsc(doctorId);
    }

    /**
     * 管理员新增排班（号源）。
     */
    @Transactional
    public Schedule create(ScheduleForm form) {
        if (!form.getStartTime().isBefore(form.getEndTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        if (scheduleRepository.existsByDoctorIdAndDateAndStartTime(
                form.getDoctorId(), form.getDate(), form.getStartTime())) {
            throw new BusinessException("该医生在该日期时段已有排班");
        }
        Doctor doctor = doctorRepository.findById(form.getDoctorId())
                .orElseThrow(() -> new BusinessException("医生不存在"));
        Schedule schedule = new Schedule(doctor, form.getDate(), form.getStartTime(),
                form.getEndTime(), form.getTotalCount());
        return scheduleRepository.save(schedule);
    }

    public static LocalDate todayPlus(int days) {
        return LocalDate.now().plus(days, ChronoUnit.DAYS);
    }
}
