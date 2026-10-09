package com.medislot.entity;

/**
 * 会话类型：群聊（患者+医生+AI）/ 医生病例研究（医生+AI，私有）。
 */
public enum ConversationScope {
    GROUP,
    DOCTOR_PRIVATE
}
