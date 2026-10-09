package com.medislot.service;

import com.medislot.entity.Appointment;
import com.medislot.entity.Conversation;
import com.medislot.entity.ConversationMessage;
import com.medislot.entity.ConversationScope;
import com.medislot.entity.ConversationStatus;
import com.medislot.entity.MessageType;
import com.medislot.entity.ReviewStatus;
import com.medislot.entity.Role;
import com.medislot.entity.SenderType;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.repository.AppointmentRepository;
import com.medislot.repository.ConversationMessageRepository;
import com.medislot.repository.ConversationRepository;
import com.medislot.service.ai.AiChatClient;
import com.medislot.service.ai.AiReply;
import com.medislot.service.ai.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 诊前咨询业务：
 * <ul>
 *   <li>群聊（GROUP）：患者+医生+AI。患者发言触发 AI 回复；医生可发普通消息或「指挥 AI」产出草稿，并对草稿核实。</li>
 *   <li>医生病例研究（DOCTOR_PRIVATE）：医生与 AI 私有对话，患者不可见。</li>
 * </ul>
 * 所有方法按 id 重新加载会话，避免游离实体的懒加载问题。
 */
@Service
public class ConsultationService {

    private static final Logger log = LoggerFactory.getLogger(ConsultationService.class);
    private static final int MAX_CONTENT_LENGTH = 4000;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final AppointmentRepository appointmentRepository;
    private final SettingService settingService;
    private final SymptomIntakeService symptomIntakeService;
    private final AiChatClient aiChatClient;

    public ConsultationService(ConversationRepository conversationRepository,
                               ConversationMessageRepository messageRepository,
                               AppointmentRepository appointmentRepository,
                               SettingService settingService,
                               SymptomIntakeService symptomIntakeService,
                               AiChatClient aiChatClient) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.appointmentRepository = appointmentRepository;
        this.settingService = settingService;
        this.symptomIntakeService = symptomIntakeService;
        this.aiChatClient = aiChatClient;
    }

    /** 一次 AI 回复请求：先落库人类消息并构建 prompt，再流式生成，最后落库 AI 消息。 */
    public record AiRequest(Long humanMessageId, List<ChatMessage> prompt,
                            MessageType responseType, Long parentMessageId) {
    }

    // ==================== 会话获取 ====================

    @Transactional
    public Conversation getOrCreateGroup(Long appointmentId) {
        return conversationRepository
                .findByAppointmentIdAndScope(appointmentId, ConversationScope.GROUP)
                .orElseGet(() -> {
                    Appointment appointment = loadAppointment(appointmentId);
                    return conversationRepository.save(
                            new Conversation(appointment, ConversationScope.GROUP, appointment.getSchedule().getDoctor()));
                });
    }

    @Transactional
    public Conversation getOrCreateDoctorPrivate(Long appointmentId) {
        return conversationRepository
                .findByAppointmentIdAndScope(appointmentId, ConversationScope.DOCTOR_PRIVATE)
                .orElseGet(() -> {
                    Appointment appointment = loadAppointment(appointmentId);
                    return conversationRepository.save(
                            new Conversation(appointment, ConversationScope.DOCTOR_PRIVATE, appointment.getSchedule().getDoctor()));
                });
    }

    @Transactional(readOnly = true)
    public Conversation getRequired(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("会话不存在"));
    }

    @Transactional(readOnly = true)
    public List<ConversationMessage> listMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional(readOnly = true)
    public void assertCanViewGroup(Long conversationId, User user) {
        Conversation conversation = getRequired(conversationId);
        if (user.getRole() == Role.DOCTOR) {
            assertGroupDoctor(conversation, user);
        } else {
            assertGroupPatient(conversation, user);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanViewPrivate(Long conversationId, User user) {
        assertPrivateDoctor(getRequired(conversationId), user);
    }

    // ==================== 群聊 ====================

    @Transactional
    public void postPatientMessage(Long conversationId, User patient, String content) {
        Conversation conversation = getRequired(conversationId);
        assertGroupPatient(conversation, patient);
        ensureActive(conversation);
        ensureEnabled();
        enforceRateLimit(conversation);
        enforceMaxMessages(conversation);
        saveMessage(conversation, SenderType.PATIENT, patient.getId(), MessageType.CHAT, content);
        aiReplyToPatient(conversation);
    }

    @Transactional
    public void postDoctorGroupMessage(Long conversationId, User doctor, String content, MessageType type) {
        Conversation conversation = getRequired(conversationId);
        assertGroupDoctor(conversation, doctor);
        ensureActive(conversation);
        ConversationMessage saved = saveMessage(conversation, SenderType.DOCTOR, doctor.getId(), type, content);
        if (type == MessageType.DIRECTIVE) {
            aiProduceDraft(conversation, saved);
        }
    }

    @Transactional
    public void reviewDraft(Long conversationId, User doctor, Long messageId, ReviewStatus status, String note) {
        Conversation conversation = getRequired(conversationId);
        assertGroupDoctor(conversation, doctor);
        ConversationMessage draft = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException("草稿不存在"));
        if (draft.getMessageType() != MessageType.DRAFT) {
            throw new BusinessException("该消息不是草稿");
        }
        draft.setReviewStatus(status);
        draft.setReviewedBy(doctor.getId());
        draft.setReviewNote(note);
        messageRepository.save(draft);
        if (status == ReviewStatus.ADJUSTED) {
            aiRegenerateDraft(conversation, draft, note);
        }
    }

    @Transactional
    public void generateSummary(Long conversationId, User doctor) {
        Conversation conversation = getRequired(conversationId);
        assertGroupDoctor(conversation, doctor);
        List<ConversationMessage> messages = listMessages(conversationId);
        if (messages.isEmpty()) {
            throw new BusinessException("暂无可摘要的内容");
        }
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(
                "你是医疗问诊助手。请根据以下诊前咨询对话，输出简洁的《问诊摘要》，包含：主诉、病程、"
                        + "伴随症状、既往史/用药（如有）、风险提示。不要下诊断结论，不要给出处方。"));
        prompt.add(ChatMessage.user(renderTranscript(messages)));
        AiReply reply = safeChat(prompt);
        if (reply == null) {
            throw new BusinessException("AI 暂时不可用，摘要生成失败");
        }
        conversation.setSummary(reply.content());
        conversationRepository.save(conversation);
    }

    @Transactional
    public void close(Long conversationId, User doctor) {
        Conversation conversation = getRequired(conversationId);
        assertGroupDoctor(conversation, doctor);
        conversation.setStatus(ConversationStatus.CLOSED);
        conversation.setClosedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    // ==================== 医生病例研究（私有） ====================

    @Transactional
    public void postCaseMessage(Long conversationId, User doctor, String content) {
        Conversation conversation = getRequired(conversationId);
        assertPrivateDoctor(conversation, doctor);
        ensureActive(conversation);
        ensureEnabled();
        saveMessage(conversation, SenderType.DOCTOR, doctor.getId(), MessageType.CHAT, content);
        aiReplyPrivate(conversation);
    }

    // ==================== 流式：准备 / 收尾 ====================

    /** 患者发言：落库并构建 AI 回复 prompt（不在此调用 AI）。 */
    @Transactional
    public AiRequest preparePatientMessage(Long conversationId, User patient, String content) {
        Conversation conversation = getRequired(conversationId);
        assertGroupPatient(conversation, patient);
        ensureActive(conversation);
        ensureEnabled();
        enforceRateLimit(conversation);
        enforceMaxMessages(conversation);
        ConversationMessage message = saveMessage(conversation, SenderType.PATIENT, patient.getId(), MessageType.CHAT, content);
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(groupSystemPrompt(conversation)));
        prompt.addAll(history(conversation, false));
        return new AiRequest(message.getId(), prompt, MessageType.CHAT, null);
    }

    /** 医生指挥 AI：落库指令并构建草稿 prompt。 */
    @Transactional
    public AiRequest prepareDoctorDirective(Long conversationId, User doctor, String content) {
        Conversation conversation = getRequired(conversationId);
        assertGroupDoctor(conversation, doctor);
        ensureActive(conversation);
        ConversationMessage message = saveMessage(conversation, SenderType.DOCTOR, doctor.getId(), MessageType.DIRECTIVE, content);
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(groupSystemPrompt(conversation)
                + "\n\n【当前任务】医生要求你产出一份给患者看的方案草稿，请直接给出内容，不要寒暄。"));
        prompt.addAll(history(conversation, true));
        prompt.add(ChatMessage.user("【医生指令】" + content + "\n请据此产出一份给患者看的方案草稿。"));
        return new AiRequest(message.getId(), prompt, MessageType.DRAFT, message.getId());
    }

    /** 医生病例研究发言：落库并构建 prompt。 */
    @Transactional
    public AiRequest prepareCaseMessage(Long conversationId, User doctor, String content) {
        Conversation conversation = getRequired(conversationId);
        assertPrivateDoctor(conversation, doctor);
        ensureActive(conversation);
        ensureEnabled();
        ConversationMessage message = saveMessage(conversation, SenderType.DOCTOR, doctor.getId(), MessageType.CHAT, content);
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(privateSystemPrompt()));
        prompt.addAll(history(conversation, true));
        return new AiRequest(message.getId(), prompt, MessageType.CHAT, null);
    }

    /** 医生在群聊「回复患者」：只落库，不触发 AI。 */
    @Transactional
    public Long postDoctorChat(Long conversationId, User doctor, String content) {
        Conversation conversation = getRequired(conversationId);
        assertGroupDoctor(conversation, doctor);
        ensureActive(conversation);
        return saveMessage(conversation, SenderType.DOCTOR, doctor.getId(), MessageType.CHAT, content).getId();
    }

    /** 流式结束后落库 AI 消息，返回其 id。 */
    @Transactional
    public Long finishAiReply(Long conversationId, AiRequest request, String content, String model) {
        Conversation conversation = getRequired(conversationId);
        ConversationMessage message = new ConversationMessage(
                conversation, SenderType.AI, null, request.responseType(), content);
        message.setParentMessageId(request.parentMessageId());
        message.setReviewStatus(request.responseType() == MessageType.DRAFT ? ReviewStatus.PENDING : null);
        message.setModel(model);
        messageRepository.save(message);
        if (conversation.getAiModel() == null) {
            conversation.setAiModel(model);
            conversationRepository.save(conversation);
        }
        return message.getId();
    }

    // ==================== AI 交互 ====================

    private void aiReplyToPatient(Conversation conversation) {
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(groupSystemPrompt(conversation)));
        prompt.addAll(history(conversation, false));
        AiReply reply = safeChat(prompt);
        if (reply == null) {
            saveMessage(conversation, SenderType.SYSTEM, null, MessageType.CHAT,
                    "AI 助手暂时不可用，请稍后再试，或等待医生回复。");
            return;
        }
        saveAiMessage(conversation, MessageType.CHAT, reply, null, null);
    }

    private void aiProduceDraft(Conversation conversation, ConversationMessage directive) {
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(groupSystemPrompt(conversation)
                + "\n\n【当前任务】医生要求你产出一份给患者看的方案草稿，请直接给出内容，不要寒暄。"));
        prompt.addAll(history(conversation, true));
        prompt.add(ChatMessage.user("【医生指令】" + directive.getContent() + "\n请据此产出一份给患者看的方案草稿。"));
        AiReply reply = safeChat(prompt);
        if (reply == null) {
            saveMessage(conversation, SenderType.SYSTEM, null, MessageType.CHAT,
                    "AI 暂时无法生成草稿，请稍后再试。");
            return;
        }
        saveAiMessage(conversation, MessageType.DRAFT, reply, directive.getId(), ReviewStatus.PENDING);
    }

    private void aiRegenerateDraft(Conversation conversation, ConversationMessage draft, String note) {
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(groupSystemPrompt(conversation)
                + "\n\n【当前任务】医生要求你根据其调整意见，重新生成一版方案草稿，请直接给出内容。"));
        prompt.addAll(history(conversation, true));
        prompt.add(ChatMessage.user("【医生调整意见】" + (note == null ? "" : note)
                + "\n请基于医生指令重新生成一版草稿。"));
        AiReply reply = safeChat(prompt);
        if (reply == null) {
            saveMessage(conversation, SenderType.SYSTEM, null, MessageType.CHAT,
                    "AI 暂时无法重新生成，请稍后再试。");
            return;
        }
        saveAiMessage(conversation, MessageType.DRAFT, reply, draft.getParentMessageId(), ReviewStatus.PENDING);
    }

    private void aiReplyPrivate(Conversation conversation) {
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system(privateSystemPrompt()));
        prompt.addAll(history(conversation, true));
        AiReply reply = safeChat(prompt);
        if (reply == null) {
            saveMessage(conversation, SenderType.SYSTEM, null, MessageType.CHAT, "AI 暂时不可用。");
            return;
        }
        saveAiMessage(conversation, MessageType.CHAT, reply, null, null);
    }

    private AiReply safeChat(List<ChatMessage> prompt) {
        try {
            return aiChatClient.chat(prompt);
        } catch (Exception e) {
            log.warn("[consultation] AI 调用失败：{}", e.getMessage());
            return null;
        }
    }

    // ==================== 内部工具 ====================

    private Appointment loadAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BusinessException("预约不存在"));
    }

    private ConversationMessage saveMessage(Conversation conversation, SenderType senderType, Long senderId,
                                            MessageType messageType, String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException("消息不能为空");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            text = text.substring(0, MAX_CONTENT_LENGTH);
        }
        ConversationMessage message = new ConversationMessage(conversation, senderType, senderId, messageType, text);
        return messageRepository.save(message);
    }

    private void saveAiMessage(Conversation conversation, MessageType type, AiReply reply,
                               Long parentId, ReviewStatus reviewStatus) {
        ConversationMessage message = new ConversationMessage(
                conversation, SenderType.AI, null, type, reply.content());
        message.setParentMessageId(parentId);
        message.setReviewStatus(reviewStatus);
        message.setModel(reply.model());
        message.setPromptTokens(reply.promptTokens());
        message.setCompletionTokens(reply.completionTokens());
        messageRepository.save(message);

        if (conversation.getAiModel() == null) {
            conversation.setAiModel(reply.model());
            conversationRepository.save(conversation);
        }
    }

    private List<ChatMessage> history(Conversation conversation, boolean includeDoctor) {
        List<ConversationMessage> all = listMessages(conversation.getId());
        int max = settingService.getInt("consultation.max-history");
        if (max <= 0) {
            max = 20;
        }
        List<ConversationMessage> recent = all.size() > max ? all.subList(all.size() - max, all.size()) : all;
        List<ChatMessage> result = new ArrayList<>();
        for (ConversationMessage message : recent) {
            switch (message.getSenderType()) {
                case PATIENT -> result.add(ChatMessage.user(message.getContent()));
                case AI -> result.add(ChatMessage.assistant(message.getContent()));
                case DOCTOR -> {
                    if (includeDoctor) {
                        result.add(ChatMessage.user("【医生】" + message.getContent()));
                    }
                }
                case SYSTEM -> {
                    // 系统提示不进上下文
                }
            }
        }
        return result;
    }

    private String renderTranscript(List<ConversationMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ConversationMessage message : messages) {
            if (message.getSenderType() == SenderType.SYSTEM) {
                continue;
            }
            sb.append(message.getSenderType().getLabel()).append("：")
                    .append(message.getContent()).append('\n');
        }
        return sb.toString();
    }

    private String groupSystemPrompt(Conversation conversation) {
        Appointment appointment = conversation.getAppointment();
        String doctorName = appointment.getSchedule().getDoctor().getName();
        String department = appointment.getSchedule().getDoctor().getDepartment().getName();
        String reason = appointment.getReason() == null ? "未填写" : appointment.getReason();
        return "你是「" + doctorName + "（" + department + "）」的诊前咨询助手，正在一个包含患者和医生的群聊中。"
                + "就诊原因：" + reason + "。\n"
                + "你的职责：主动、友好地追问患者，收集主诉、病程、伴随症状、既往史与用药；"
                + "给出一般性的健康建议。\n"
                + "严格遵守：不给出确诊结论，不开具处方；如出现胸痛、呼吸困难、大出血、意识障碍等危险信号，"
                + "立即提示尽快就医/急诊。回复简洁、分点、口语化。"
                + symptomIntakeService.buildContext(appointment.getId());
    }

    private String privateSystemPrompt() {
        return "你是医生的病例研究助手，仅供医生私下研究病例使用，患者不可见。请基于病例信息专业作答，"
                + "可给出鉴别诊断思路、检查建议、用药参考，但需注明「仅供参考，最终以医生判断为准」。";
    }

    // ==================== 权限与限制 ====================

    private void assertGroupPatient(Conversation conversation, User user) {
        if (conversation.getScope() != ConversationScope.GROUP) {
            throw new BusinessException("会话类型不匹配");
        }
        if (!conversation.getAppointment().getPatient().getId().equals(user.getId())) {
            throw new BusinessException("无权访问该会话");
        }
    }

    private void assertGroupDoctor(Conversation conversation, User user) {
        if (conversation.getScope() != ConversationScope.GROUP) {
            throw new BusinessException("会话类型不匹配");
        }
        Long doctorUserId = conversation.getAppointment().getSchedule().getDoctor().getUser().getId();
        if (!doctorUserId.equals(user.getId())) {
            throw new BusinessException("无权访问该会话");
        }
    }

    private void assertPrivateDoctor(Conversation conversation, User user) {
        if (conversation.getScope() != ConversationScope.DOCTOR_PRIVATE) {
            throw new BusinessException("会话类型不匹配");
        }
        if (conversation.getDoctor() == null || !conversation.getDoctor().getUser().getId().equals(user.getId())) {
            throw new BusinessException("无权访问该会话");
        }
    }

    private void ensureActive(Conversation conversation) {
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessException("会话已关闭");
        }
    }

    private void ensureEnabled() {
        if (!settingService.getBoolean("consultation.enabled")) {
            throw new BusinessException("诊前咨询功能已关闭");
        }
    }

    private void enforceRateLimit(Conversation conversation) {
        int seconds = settingService.getInt("consultation.rate-limit-seconds");
        if (seconds <= 0) {
            return;
        }
        messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversation.getId()).ifPresent(last -> {
            if (last.getCreatedAt() != null
                    && Duration.between(last.getCreatedAt(), LocalDateTime.now()).getSeconds() < seconds) {
                throw new BusinessException("发送过于频繁，请稍候再试");
            }
        });
    }

    private void enforceMaxMessages(Conversation conversation) {
        int max = settingService.getInt("consultation.max-messages");
        if (max > 0 && messageRepository.countByConversationId(conversation.getId()) >= max) {
            throw new BusinessException("本次咨询消息已达上限");
        }
    }
}
