package com.medislot.service;

import com.medislot.entity.Appointment;
import com.medislot.entity.Conversation;
import com.medislot.entity.ConversationMessage;
import com.medislot.entity.ConversationScope;
import com.medislot.entity.ConversationStatus;
import com.medislot.entity.Doctor;
import com.medislot.entity.KnowledgeSourceType;
import com.medislot.entity.KnowledgeVisibility;
import com.medislot.entity.MessageType;
import com.medislot.entity.ReviewStatus;
import com.medislot.entity.SymptomIntake;
import com.medislot.repository.ConversationMessageRepository;
import com.medislot.repository.ConversationRepository;
import com.medislot.repository.SymptomIntakeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 把已关闭的诊前咨询会话归档为 Markdown 并写入知识库。
 *
 * <p>隐私：患者姓名/手机号会被剔除（内容中出现的也做替换），生成的文档标记为
 * {@link KnowledgeVisibility#PRIVATE}，仅医生/管理员/维护员在知识库问答中可见，
 * 患者侧诊前咨询检索增强不会命中。
 */
@Service
public class ConsultationKbService {

    private static final Logger log = LoggerFactory.getLogger(ConsultationKbService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final SymptomIntakeRepository intakeRepository;
    private final KnowledgeService knowledgeService;
    private final RagService ragService;
    private final SettingService settingService;
    private final ObjectProvider<ConsultationKbService> selfProvider;

    public ConsultationKbService(ConversationRepository conversationRepository,
                                 ConversationMessageRepository messageRepository,
                                 SymptomIntakeRepository intakeRepository,
                                 KnowledgeService knowledgeService,
                                 RagService ragService,
                                 SettingService settingService,
                                 ObjectProvider<ConsultationKbService> selfProvider) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.intakeRepository = intakeRepository;
        this.knowledgeService = knowledgeService;
        this.ragService = ragService;
        this.settingService = settingService;
        this.selfProvider = selfProvider;
    }

    /** 扫描所有「已关闭且尚未入库」的群聊会话，逐条归档。返回成功条数。 */
    public int syncClosedConversations() {
        if (!ragService.isAvailable()) {
            return 0;
        }
        List<Conversation> pending = conversationRepository
                .findByScopeAndStatusAndKbDocumentIdIsNull(ConversationScope.GROUP, ConversationStatus.CLOSED);
        int count = 0;
        for (Conversation conversation : pending) {
            try {
                if (selfProvider.getObject().generate(conversation.getId())) {
                    count++;
                }
            } catch (Exception e) {
                log.warn("[kb] 会话 #{} 归档失败：{}", conversation.getId(), e.getMessage());
            }
        }
        return count;
    }

    /**
     * 归档单个会话。返回是否真正生成了文档（内容不足或重复时为 false）。
     *
     * <p>使用 {@code REQUIRES_NEW} 让每条会话独立事务，单条失败不影响整批扫描。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean generate(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null || conversation.getKbDocumentId() != null) {
            return false;
        }
        Appointment appointment = conversation.getAppointment();
        Doctor doctor = conversation.getDoctor() != null
                ? conversation.getDoctor() : appointment.getSchedule().getDoctor();
        List<ConversationMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        SymptomIntake intake = intakeRepository.findByAppointmentId(appointment.getId()).orElse(null);
        boolean hasApprovedDraft = messages.stream()
                .anyMatch(m -> m.getMessageType() == MessageType.DRAFT && m.getReviewStatus() == ReviewStatus.APPROVED);
        int minMessages = Math.max(1, settingService.getInt("kb.consultation.min-messages"));
        boolean enoughContent = messages.size() >= minMessages
                || (conversation.getSummary() != null && !conversation.getSummary().isBlank())
                || (appointment.getDiagnosisNote() != null && !appointment.getDiagnosisNote().isBlank())
                || hasApprovedDraft;
        if (!enoughContent) {
            // 内容太少，暂不归档；标记为已处理以免反复扫描
            conversation.setKbDocumentId(-1L);
            conversationRepository.save(conversation);
            return false;
        }

        String patientName = appointment.getPatient().getName();
        String patientPhone = appointment.getPatient().getPhone();
        String markdown = buildMarkdown(conversation, appointment, doctor, intake, messages, patientName, patientPhone);

        String title = "问诊记录 " + appointment.getAppointmentNo() + "（"
                + appointment.getSchedule().getDate() + "）";
        String category = doctor.getDepartment() != null ? doctor.getDepartment().getName() : "问诊记录";
        var document = knowledgeService.ingestMarkdown(title, markdown, category,
                knowledgeService.sourceIdForType(KnowledgeSourceType.CONSULTATION),
                KnowledgeSourceType.CONSULTATION, KnowledgeVisibility.PRIVATE, null, null);
        if (document == null) {
            conversation.setKbDocumentId(-1L);
        } else {
            conversation.setKbDocumentId(document.getId());
        }
        conversationRepository.save(conversation);
        return document != null;
    }

    // ==================== Markdown 组装 ====================

    String buildMarkdown(Conversation conversation, Appointment appointment, Doctor doctor,
                         SymptomIntake intake, List<ConversationMessage> messages,
                         String patientName, String patientPhone) {
        StringBuilder md = new StringBuilder(2048);
        md.append("# 问诊记录 ").append(appointment.getAppointmentNo()).append("\n\n");
        md.append("> 来源：诊前咨询自动归档（已脱敏）· 生成时间：")
                .append(LocalDateTime.now().format(DATE)).append("\n\n");

        // 一、就诊信息
        md.append("## 一、就诊信息\n\n");
        md.append("- 科室：").append(text(doctor.getDepartment() == null ? "-" : doctor.getDepartment().getName(), patientName, patientPhone)).append('\n');
        md.append("- 医生：").append(doctor.getUser().getName());
        if (doctor.getTitle() != null && !doctor.getTitle().isBlank()) {
            md.append("（").append(doctor.getTitle()).append("）");
        }
        md.append('\n');
        md.append("- 就诊时间：").append(appointment.getSchedule().getDate())
                .append(' ').append(appointment.getSchedule().getStartTime()).append('\n');
        if (appointment.getReason() != null && !appointment.getReason().isBlank()) {
            md.append("- 就诊原因：").append(text(appointment.getReason(), patientName, patientPhone)).append('\n');
        }
        md.append('\n');

        // 二、结构化症状采集
        if (intake != null) {
            md.append("## 二、结构化症状采集\n\n");
            appendField(md, "主诉", intake.getChiefComplaint(), patientName, patientPhone);
            appendField(md, "症状", intake.getSymptoms(), patientName, patientPhone);
            appendField(md, "病程", intake.getDuration(), patientName, patientPhone);
            appendField(md, "严重程度", intake.getSeverity() == null ? null : intake.getSeverity().name(), patientName, patientPhone);
            appendField(md, "伴随症状", intake.getAccompanying(), patientName, patientPhone);
            appendField(md, "体温", intake.getTemperature() == null ? null : intake.getTemperature().toPlainString() + " ℃", patientName, patientPhone);
            appendField(md, "既往史", intake.getPastHistory(), patientName, patientPhone);
            appendField(md, "用药", intake.getMedications(), patientName, patientPhone);
            appendField(md, "过敏史", intake.getAllergies(), patientName, patientPhone);
            appendField(md, "危险信号", intake.getRedFlags(), patientName, patientPhone);
            md.append('\n');
        }

        // 三、问诊对话（患者/AI/医生普通消息）
        md.append("## 三、问诊对话\n\n");
        boolean any = false;
        for (ConversationMessage m : messages) {
            if (m.getMessageType() == MessageType.DIRECTIVE) {
                continue; // 指挥指令，价值低
            }
            String content = m.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (m.getMessageType() == MessageType.DRAFT) {
                continue; // 草稿单独在第四节展示（仅已通过）
            }
            String who = m.getSenderType().getLabel();
            md.append("**").append(who).append("：** ").append(text(content, patientName, patientPhone)).append("\n\n");
            any = true;
        }
        if (!any) {
            md.append("（无对话内容）\n\n");
        }

        // 四、医生审核通过的 AI 建议
        boolean draftHeader = false;
        for (ConversationMessage m : messages) {
            if (m.getMessageType() == MessageType.DRAFT && m.getReviewStatus() == ReviewStatus.APPROVED) {
                if (!draftHeader) {
                    md.append("## 四、医生审核通过的 AI 建议\n\n");
                    draftHeader = true;
                }
                md.append("- ").append(text(m.getContent(), patientName, patientPhone)).append('\n');
            }
        }
        if (draftHeader) {
            md.append('\n');
        }

        // 五、医生诊断备注
        if (appointment.getDiagnosisNote() != null && !appointment.getDiagnosisNote().isBlank()) {
            md.append("## 五、医生诊断备注\n\n")
                    .append(text(appointment.getDiagnosisNote(), patientName, patientPhone)).append("\n\n");
        }

        // 六、问诊摘要
        if (conversation.getSummary() != null && !conversation.getSummary().isBlank()) {
            md.append("## 六、问诊摘要\n\n")
                    .append(text(conversation.getSummary(), patientName, patientPhone)).append("\n");
        }
        return md.toString();
    }

    private void appendField(StringBuilder md, String label, String value, String name, String phone) {
        if (value != null && !value.isBlank()) {
            md.append("- ").append(label).append("：").append(text(value, name, phone)).append('\n');
        }
    }

    /** 脱敏：剔除患者姓名与手机号（正文中出现的也替换）。 */
    private String text(String raw, String name, String phone) {
        if (raw == null) {
            return "";
        }
        String s = raw;
        if (name != null && !name.isBlank()) {
            s = s.replace(name, "患者");
        }
        if (phone != null && !phone.isBlank()) {
            s = s.replace(phone, "[已脱敏]");
        }
        return s;
    }
}
