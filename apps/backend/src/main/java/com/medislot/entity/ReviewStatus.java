package com.medislot.entity;

/**
 * 草稿核实状态（医生对 AI 产出的核实）。
 */
public enum ReviewStatus {
    PENDING("待核实"),
    APPROVED("已同意"),
    REJECTED("已反对"),
    ADJUSTED("已调整");

    private final String label;

    ReviewStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
