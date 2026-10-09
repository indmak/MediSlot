package com.medislot.entity;

/**
 * 知识库文档处理状态。
 */
public enum KnowledgeStatus {
    PENDING("待处理"),
    PROCESSING("处理中"),
    READY("已就绪"),
    FAILED("失败");

    private final String label;

    KnowledgeStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
