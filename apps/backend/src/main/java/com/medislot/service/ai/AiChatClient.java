package com.medislot.service.ai;

import java.util.List;
import java.util.function.Consumer;

/**
 * AI 对话客户端抽象。真实实现为 DeepSeek；无密钥时降级为 Mock。
 */
public interface AiChatClient {

    /** 发送对话并返回完整回复（非流式）。失败时抛出 RuntimeException。 */
    AiReply chat(List<ChatMessage> messages);

    /** 流式对话：每收到一段增量文本就回调 onToken。失败时抛出 RuntimeException。 */
    void streamChat(List<ChatMessage> messages, Consumer<String> onToken);

    /** 当前使用的模型名。 */
    String model();

    /** 是否可用（已配置密钥）。 */
    boolean isAvailable();
}
