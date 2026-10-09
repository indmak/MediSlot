package com.medislot.dto;

import java.math.BigDecimal;

/**
 * 医生卡片视图对象（供医生列表页渲染，避免把实体直接暴露给页面）。
 */
public record DoctorCard(
        Long id,
        String name,
        String title,
        String departmentName,
        BigDecimal rating,
        Integer appointmentCount,
        String avatarUrl,
        BigDecimal registrationFee,
        boolean hasAvailableSlot,
        int remainingToday
) {
}
