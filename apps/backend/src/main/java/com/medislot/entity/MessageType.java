package com.medislot.entity;

/**
 * 消息类型：
 * CHAT 普通对话；DIRECTIVE 医生指挥 AI；DRAFT AI 产出草稿（待医生核实）。
 */
public enum MessageType {
    CHAT,
    DIRECTIVE,
    DRAFT
}
