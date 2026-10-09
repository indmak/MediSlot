package com.medislot.repository;

import com.medislot.entity.KnowledgeDocument;
import com.medislot.entity.KnowledgeSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();

    List<KnowledgeDocument> findBySourceIdOrderByCreatedAtDesc(Long sourceId);

    boolean existsByContentHash(String contentHash);

    long countBySourceId(Long sourceId);

    long countBySourceType(KnowledgeSourceType sourceType);
}
