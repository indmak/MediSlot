package com.medislot.entity;

/**
 * 症状严重程度。
 */
public enum SeverityLevel {
    MILD("轻"),
    MODERATE("中"),
    SEVERE("重");

    private final String label;

    SeverityLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
