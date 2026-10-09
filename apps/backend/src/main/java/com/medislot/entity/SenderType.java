package com.medislot.entity;

/**
 * 消息发送者类型。
 */
public enum SenderType {
    PATIENT("患者"),
    DOCTOR("医生"),
    AI("AI 助手"),
    SYSTEM("系统");

    private final String label;

    SenderType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
