package com.medislot.service.ai;

/**
 * 一次 AI 回复及其用量。
 */
public record AiReply(String content, String model, int promptTokens, int completionTokens) {
}
