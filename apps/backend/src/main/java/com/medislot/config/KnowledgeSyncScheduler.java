package com.medislot.config;

import com.medislot.service.ConsultationKbService;
import com.medislot.service.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时把已关闭的诊前咨询会话归档进知识库（可在设置中心用 {@code kb.consultation.auto-sync} 关闭）。
 */
@Component
public class KnowledgeSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSyncScheduler.class);

    private final ConsultationKbService consultationKbService;
    private final SettingService settingService;

    public KnowledgeSyncScheduler(ConsultationKbService consultationKbService, SettingService settingService) {
        this.consultationKbService = consultationKbService;
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
}
