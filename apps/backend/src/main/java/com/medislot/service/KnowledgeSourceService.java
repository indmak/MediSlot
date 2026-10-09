package com.medislot.service;

import com.medislot.entity.KnowledgeDocument;
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
 * 知识库来源管理：列表 / 增删改 / 启停 / 手动同步（诊前咨询、外部接口）。
 */
@Service
public class KnowledgeSourceService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSourceService.class);

    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeService knowledgeService;
    private final ConsultationKbService consultationKbService;
    private final ExternalKnowledgeService externalKnowledgeService;

    public KnowledgeSourceService(KnowledgeSourceRepository sourceRepository,
                                  KnowledgeDocumentRepository documentRepository,
                                  KnowledgeService knowledgeService,
                                  ConsultationKbService consultationKbService,
                                  ExternalKnowledgeService externalKnowledgeService) {
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
        this.knowledgeService = knowledgeService;
        this.consultationKbService = consultationKbService;
        this.externalKnowledgeService = externalKnowledgeService;
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

    // ==================== 外部来源维护 ====================

    @Transactional
    public KnowledgeSource createExternal(String name, String description, String configJson, String scheduleCron) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("请填写来源名称");
        }
        if (sourceRepository.existsByName(name.trim())) {
            throw new BusinessException("来源名称已存在");
        }
        KnowledgeSource source = new KnowledgeSource(name.trim(),
                KnowledgeSourceType.EXTERNAL_API,
                description == null || description.isBlank() ? "第三方 REST 接口批量导入" : description.trim(),
                true);
        source.setConfigJson(configJson);
        source.setScheduleCron(blankToNull(scheduleCron));
        return sourceRepository.save(source);
    }

    @Transactional
    public void update(Long id, String name, String description, String configJson, String scheduleCron, boolean enabled) {
        KnowledgeSource source = getRequired(id);
        if (name != null && !name.isBlank() && !name.trim().equals(source.getName())) {
            if (sourceRepository.existsByName(name.trim())) {
                throw new BusinessException("来源名称已存在");
            }
            source.setName(name.trim());
        }
        if (description != null) {
            source.setDescription(description.trim());
        }
        source.setConfigJson(configJson);
        source.setScheduleCron(blankToNull(scheduleCron));
        source.setEnabled(enabled);
        sourceRepository.save(source);
    }

    /** 删除外部来源，同时清理其文档与向量（内置来源不可删除）。 */
    @Transactional
    public void delete(Long id) {
        KnowledgeSource source = getRequired(id);
        if (source.getType() != KnowledgeSourceType.EXTERNAL_API) {
            throw new BusinessException("内置来源不可删除");
        }
        for (KnowledgeDocument doc : documentRepository.findBySourceIdOrderByCreatedAtDesc(id)) {
            try {
                knowledgeService.delete(doc.getId());
            } catch (RuntimeException e) {
                log.warn("[kb] 删除来源 #{} 的文档 #{} 失败：{}", id, doc.getId(), e.getMessage());
            }
        }
        sourceRepository.delete(source);
    }

    /** 手动同步外部来源（不受定时开关限制）。 */
    public int syncExternal(Long id) {
        KnowledgeSource source = getRequired(id);
        if (source.getType() != KnowledgeSourceType.EXTERNAL_API) {
            throw new BusinessException("该来源不是外部接口类型");
        }
        return externalKnowledgeService.sync(id);
    }

    // ==================== 诊前咨询来源 ====================

    /** 手动同步「诊前咨询」来源（不受自动同步开关限制）。返回本次归档条数。 */
    @Transactional
    public int syncConsultation() {
        KnowledgeSource source = sourceRepository.findFirstByTypeOrderByIdAsc(KnowledgeSourceType.CONSULTATION).orElse(null);
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

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** PubMed 预设配置（供新建外部来源表单预填）。 */
    public String pubmedPresetJson() {
        return externalKnowledgeService.pubmedPresetJson();
    }
}
