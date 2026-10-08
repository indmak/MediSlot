package com.medislot.entity;

/**
 * 预约状态。流转规则见 docs/db-schema.md：
 * PENDING → CHECKED_IN → COMPLETED，PENDING → CANCELLED。
 */
public enum AppointmentStatus {
    PENDING("待就诊"),
    CHECKED_IN("就诊中"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
