package com.medislot.dto;

/**
 * 管理后台首页统计。
 */
public record DashboardStats(
        long doctors,
        long patients,
        long departments,
        long todayAppointments,
        long pendingAppointments,
        long totalAppointments
) {
}
