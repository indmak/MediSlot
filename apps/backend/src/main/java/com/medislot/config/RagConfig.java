package com.medislot.config;

import com.medislot.service.ai.ZhipuEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RAG 装配：智谱嵌入 + PgVectorStore + 文本切分器。
 * 仅当 {@code medislot.rag.enabled=true} 时启用（测试环境关闭）。
 */
@Configuration
@ConditionalOnProperty(prefix = "medislot.rag", name = "enabled", havingValue = "true")
public class RagConfig {

    @Bean
    public EmbeddingModel embeddingModel(ZhipuProperties properties) {
        return new ZhipuEmbeddingModel(properties);
    }

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                   EmbeddingModel embeddingModel,
                                   @Value("${medislot.rag.dimensions:1024}") int dimensions) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
    }
}
