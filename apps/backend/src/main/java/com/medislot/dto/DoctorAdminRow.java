package com.medislot.dto;

import java.math.BigDecimal;

/**
 * 管理后台医生列表行。
 */
public record DoctorAdminRow(
        Long id,
        String name,
        String phone,
        String title,
        String departmentName,
        BigDecimal registrationFee
) {
}
