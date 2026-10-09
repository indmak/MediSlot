package com.medislot.config;

import com.medislot.service.ai.AiChatClient;
import com.medislot.service.ai.DeepSeekChatClient;
import com.medislot.service.ai.MockAiChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 选择 AI 客户端：已配置 DeepSeek 密钥用真实实现，否则降级为 Mock。
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public AiChatClient aiChatClient(ExternalApiProperties properties, ObjectMapper objectMapper) {
        if (properties.isConfigured()) {
            log.info("[ai] DeepSeek 已配置，使用真实模型：{}", properties.getModel());
            return new DeepSeekChatClient(properties, objectMapper);
        }
        log.warn("[ai] 未配置 DeepSeek 密钥，降级为 Mock AI（仅演示）");
        return new MockAiChatClient();
    }
}
