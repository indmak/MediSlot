package com.medislot.entity;

/**
 * 知识库来源类型。
 */
public enum KnowledgeSourceType {
    /** 维护员手动上传。 */
    MANUAL("人工上传"),
    /** 由诊前咨询全流程数据自动生成。 */
    CONSULTATION("诊前咨询"),
    /** 第三方数据源 API 批量导入。 */
    EXTERNAL_API("外部接口");

    private final String label;

    KnowledgeSourceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
