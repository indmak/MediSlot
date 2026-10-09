package com.medislot.service;

import com.medislot.entity.KnowledgeSource;
import com.medislot.entity.KnowledgeSourceType;
import com.medislot.exception.BusinessException;
import com.medislot.repository.KnowledgeDocumentRepository;
import com.medislot.repository.KnowledgeSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库来源管理：列表 / 启停 / 手动同步。
 */
@Service
public class KnowledgeSourceService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSourceService.class);

    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final ConsultationKbService consultationKbService;

    public KnowledgeSourceService(KnowledgeSourceRepository sourceRepository,
                                  KnowledgeDocumentRepository documentRepository,
                                  ConsultationKbService consultationKbService) {
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
        this.consultationKbService = consultationKbService;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSource> list() {
        return sourceRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public KnowledgeSource getRequired(Long id) {
        return sourceRepository.findById(id).orElseThrow(() -> new BusinessException("来源不存在"));
    }

    @Transactional(readOnly = true)
    public long count(Long sourceId) {
        return documentRepository.countBySourceId(sourceId);
    }

    @Transactional
    public void toggle(Long id, boolean enabled) {
        KnowledgeSource source = getRequired(id);
        source.setEnabled(enabled);
        sourceRepository.save(source);
    }

    /** 手动同步「诊前咨询」来源（不受自动同步开关限制）。返回本次归档条数。 */
    @Transactional
    public int syncConsultation() {
        KnowledgeSource source = sourceRepository.findByType(KnowledgeSourceType.CONSULTATION).orElse(null);
        try {
            int count = consultationKbService.syncClosedConversations();
            markSynced(source, "OK", "本次归档 " + count + " 条");
            return count;
        } catch (RuntimeException e) {
            markSynced(source, "FAILED", e.getMessage());
            throw e;
        }
    }

    private void markSynced(KnowledgeSource source, String status, String message) {
        if (source == null) {
            return;
        }
        source.setLastSyncAt(LocalDateTime.now());
        source.setLastSyncStatus(status);
        source.setLastSyncMessage(message == null ? null : (message.length() > 500 ? message.substring(0, 500) : message));
        sourceRepository.save(source);
        log.info("[kb] 来源「{}」同步：{} - {}", source.getName(), status, message);
    }
}
