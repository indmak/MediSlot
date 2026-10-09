package com.medislot.entity;

/**
 * 用户角色。存储时带 {@code ROLE_} 前缀供 Spring Security 使用。
 */
public enum Role {
    PATIENT,
    DOCTOR,
    ADMIN,
    /** 知识库维护员：维护 RAG 知识库（上传/更新/删除）。 */
    KB_MAINTAINER;

    public String authority() {
        return "ROLE_" + name();
    }
}
