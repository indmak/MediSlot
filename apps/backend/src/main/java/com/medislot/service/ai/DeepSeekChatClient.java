package com.medislot.service.ai;

import com.medislot.config.ExternalApiProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek Chat Completions 客户端（OpenAI 兼容）。
 * 文档：https://api-docs.deepseek.com/
 *
 * <p>不直接依赖 Jackson（Spring Boot 4 使用 Jackson 3），改用 RestClient 的消息转换器把响应反序列化为 Map。
 */
public class DeepSeekChatClient implements AiChatClient {

    private final ExternalApiProperties properties;
    private final RestClient restClient;

    public DeepSeekChatClient(ExternalApiProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean isAvailable() {
        return properties.isConfigured();
    }

    @Override
    @SuppressWarnings("unchecked")
    public AiReply chat(List<ChatMessage> messages) {
        List<Map<String, String>> payloadMessages = messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", payloadMessages);
        body.put("stream", false);
        body.put("temperature", 0.7);

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApikey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response == null) {
                throw new AiException("AI 返回为空", null);
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AiException("AI 返回无 choices", null);
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = message == null ? "" : String.valueOf(message.getOrDefault("content", ""));

            int promptTokens = 0;
            int completionTokens = 0;
            Object usage = response.get("usage");
            if (usage instanceof Map<?, ?> usageMap) {
                promptTokens = toInt(usageMap.get("prompt_tokens"));
                completionTokens = toInt(usageMap.get("completion_tokens"));
            }
            return new AiReply(content, properties.getModel(), promptTokens, completionTokens);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("AI 服务调用失败：" + e.getMessage(), e);
        }
    }

    private int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
