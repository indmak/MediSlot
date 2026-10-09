package com.medislot.entity;

/**
 * 会话状态。
 */
public enum ConversationStatus {
    ACTIVE("进行中"),
    CLOSED("已关闭");

    private final String label;

    ConversationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
