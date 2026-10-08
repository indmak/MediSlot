package com.medislot.entity;

/**
 * 用户角色。存储时带 {@code ROLE_} 前缀供 Spring Security 使用。
 */
public enum Role {
    PATIENT,
    DOCTOR,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
