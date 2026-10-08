package com.medislot.dto;

import com.medislot.entity.Schedule;

import java.time.LocalDate;
import java.util.List;

/**
 * 按天分组的排班视图（上午 / 下午），供医生详情页渲染。
 */
public record ScheduleDayView(LocalDate date, List<Schedule> morning, List<Schedule> afternoon) {

    public int totalRemaining() {
        return morning.stream().mapToInt(Schedule::getRemaining).sum()
                + afternoon.stream().mapToInt(Schedule::getRemaining).sum();
    }
}
