package com.medislot.service.ai;

import java.util.List;

/**
 * AI 对话客户端抽象。真实实现为 DeepSeek；无密钥时降级为 Mock。
 */
public interface AiChatClient {

    /** 发送对话并返回回复。失败时抛出 RuntimeException。 */
    AiReply chat(List<ChatMessage> messages);

    /** 是否可用（已配置密钥）。 */
    boolean isAvailable();
}
