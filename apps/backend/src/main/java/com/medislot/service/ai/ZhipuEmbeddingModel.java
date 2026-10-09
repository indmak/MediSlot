package com.medislot.service.ai;

import com.medislot.config.ZhipuProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智谱 embedding-3 嵌入模型（OpenAI 兼容），实现 Spring AI 的 {@link EmbeddingModel}，
 * 供 {@code PgVectorStore} 使用。
 */
public class ZhipuEmbeddingModel implements EmbeddingModel {

    private final ZhipuProperties properties;
    private final RestClient restClient;
    private final int dimensions;

    public ZhipuEmbeddingModel(ZhipuProperties properties) {
        this.properties = properties;
        this.dimensions = properties.getDimensions();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("input", texts);
        body.put("dimensions", dimensions);

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + properties.getApikey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response == null) {
                throw new AiException("智谱嵌入返回为空", null);
            }
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) {
                throw new AiException("智谱嵌入返回无 data", null);
            }
            List<Embedding> embeddings = new ArrayList<>(data.size());
            for (Map<String, Object> item : data) {
                List<Number> vector = (List<Number>) item.get("embedding");
                float[] arr = new float[vector.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = vector.get(i).floatValue();
                }
                int index = item.get("index") instanceof Number n ? n.intValue() : 0;
                embeddings.add(new Embedding(arr, index));
            }
            return new EmbeddingResponse(embeddings);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("智谱嵌入调用失败：" + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(Document document) {
        return embed(getEmbeddingContent(document));
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
