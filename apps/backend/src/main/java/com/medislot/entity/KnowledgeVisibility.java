package com.medislot.entity;

/**
 * 知识库文档的可见范围。
 *
 * <p>{@code PUBLIC} 可被任何人（含患者侧诊前咨询检索增强）检索；
 * {@code PRIVATE}（如由真实问诊记录生成的文档）仅医生 / 管理员 / 维护员在知识库问答中可见。
 */
public enum KnowledgeVisibility {
    PUBLIC("公开"),
    PRIVATE("私有");

    private final String label;

    KnowledgeVisibility(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
