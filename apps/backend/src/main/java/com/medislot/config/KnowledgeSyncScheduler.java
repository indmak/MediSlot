package com.medislot.config;

import com.medislot.service.ConsultationKbService;
import com.medislot.service.ExternalKnowledgeService;
import com.medislot.service.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 知识库定时同步：
 * <ul>
 *   <li>每 5 分钟把已关闭的诊前咨询会话归档（可用 {@code kb.consultation.auto-sync} 关闭）；</li>
 *   <li>每 10 分钟检查各外部来源的 {@code schedule_cron}，到期的执行抓取。</li>
 * </ul>
 */
@Component
public class KnowledgeSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSyncScheduler.class);

    private final ConsultationKbService consultationKbService;
    private final ExternalKnowledgeService externalKnowledgeService;
    private final SettingService settingService;

    public KnowledgeSyncScheduler(ConsultationKbService consultationKbService,
                                  ExternalKnowledgeService externalKnowledgeService,
                                  SettingService settingService) {
        this.consultationKbService = consultationKbService;
        this.externalKnowledgeService = externalKnowledgeService;
        this.settingService = settingService;
    }

    /** 每 5 分钟扫描一次，启动 1 分钟后首次执行。 */
    @Scheduled(fixedDelayString = "300000", initialDelayString = "60000")
    public void syncConsultations() {
        if (!settingService.getBoolean("kb.consultation.auto-sync")) {
            return;
        }
        int count = consultationKbService.syncClosedConversations();
        if (count > 0) {
            log.info("[kb] 自动归档 {} 条问诊记录到知识库", count);
        }
    }

    /** 每 10 分钟检查外部来源的定时表达式，启动 2 分钟后首次执行。 */
    @Scheduled(fixedDelayString = "600000", initialDelayString = "120000")
    public void syncExternal() {
        int count = externalKnowledgeService.syncDueExternal();
        if (count > 0) {
            log.info("[kb] 外部来源定时同步新增 {} 条文档", count);
        }
    }
}
