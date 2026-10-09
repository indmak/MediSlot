package com.medislot.service;

import com.medislot.entity.KnowledgeDocument;
import com.medislot.entity.KnowledgeStatus;
import com.medislot.exception.BusinessException;
import com.medislot.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 知识库文档业务：上传 → 解析(Tika) → 切分 → 向量化(PgVector) → 入库；重建、删除。
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private final KnowledgeDocumentRepository repository;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectProvider<TokenTextSplitter> splitterProvider;
    private final Path kbDir;

    public KnowledgeService(KnowledgeDocumentRepository repository,
                            ObjectProvider<VectorStore> vectorStoreProvider,
                            ObjectProvider<TokenTextSplitter> splitterProvider,
                            @Value("${medislot.kb.dir:uploads/kb}") String kbDir) {
        this.repository = repository;
        this.vectorStoreProvider = vectorStoreProvider;
        this.splitterProvider = splitterProvider;
        this.kbDir = Paths.get(kbDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.kbDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public KnowledgeDocument getRequired(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("文档不存在"));
    }

    @Transactional
    public KnowledgeDocument upload(MultipartFile file, String title, String category, Long userId) {
        requireVectorStore();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("文件过大（上限 20MB）");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            Files.copy(file.getInputStream(), kbDir.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败");
        }
        String docTitle = (title == null || title.isBlank())
                ? (original == null ? "未命名文档" : original)
                : title.trim();
        KnowledgeDocument document = new KnowledgeDocument(
                docTitle, original, stored, file.getContentType(), file.getSize(), category, userId);
        repository.save(document);
        ingest(document);
        return document;
    }

    @Transactional
    public void reindex(Long id) {
        ingest(getRequired(id));
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeDocument document = getRequired(id);
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore != null) {
            vectorStore.delete(new FilterExpressionTextParser().parse("documentId == '" + id + "'"));
        }
        try {
            Files.deleteIfExists(kbDir.resolve(document.getStoredName()));
        } catch (IOException e) {
            log.warn("[kb] 删除文件失败：{}", e.getMessage());
        }
        repository.delete(document);
    }

    private void ingest(KnowledgeDocument document) {
        VectorStore vectorStore = requireVectorStore();
        TokenTextSplitter splitter = splitterProvider.getIfAvailable();
        if (splitter == null) {
            throw new BusinessException("RAG 未启用");
        }
        try {
            document.setStatus(KnowledgeStatus.PROCESSING);
            document.setErrorMessage(null);
            repository.save(document);

            // 清理旧向量
            vectorStore.delete(new FilterExpressionTextParser().parse("documentId == '" + document.getId() + "'"));

            Resource resource = new FileSystemResource(kbDir.resolve(document.getStoredName()));
            List<Document> raw = new TikaDocumentReader(resource).get();
            List<Document> chunks = splitter.apply(raw);

            List<Document> withMeta = new ArrayList<>();
            int index = 0;
            for (Document chunk : chunks) {
                if (chunk.getText() == null || chunk.getText().isBlank()) {
                    continue;
                }
                withMeta.add(Document.builder()
                        .text(chunk.getText())
                        .metadata("documentId", String.valueOf(document.getId()))
                        .metadata("title", document.getTitle())
                        .metadata("chunkIndex", index++)
                        .build());
            }
            if (!withMeta.isEmpty()) {
                vectorStore.add(withMeta);
            }
            document.setChunkCount(withMeta.size());
            document.setStatus(KnowledgeStatus.READY);
            repository.save(document);
            log.info("[kb] 文档 #{} 索引完成，共 {} 片段", document.getId(), withMeta.size());
        } catch (Exception e) {
            log.warn("[kb] 文档 #{} 索引失败：{}", document.getId(), e.getMessage());
            document.setStatus(KnowledgeStatus.FAILED);
            document.setErrorMessage(truncate(e.getMessage()));
            repository.save(document);
            throw new BusinessException("文档解析/向量化失败：" + e.getMessage());
        }
    }

    private VectorStore requireVectorStore() {
        return vectorStoreProvider.getIfAvailable(() -> {
            throw new BusinessException("RAG 未启用（medislot.rag.enabled=false）");
        });
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
