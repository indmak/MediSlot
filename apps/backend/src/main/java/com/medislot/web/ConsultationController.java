package com.medislot.web;

import com.medislot.dto.MessageView;
import com.medislot.dto.SymptomIntakeForm;
import com.medislot.entity.Conversation;
import com.medislot.entity.ConversationMessage;
import com.medislot.entity.MessageType;
import com.medislot.entity.ReviewStatus;
import com.medislot.entity.Role;
import com.medislot.entity.SeverityLevel;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.service.ConsultationService;
import com.medislot.service.SymptomIntakeService;
import com.medislot.service.UserService;
import com.medislot.service.ai.AiChatClient;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 诊前咨询群聊（患者 + 医生 + AI）。
 */
@Controller
@RequestMapping("/consultations")
public class ConsultationController {

    private final ConsultationService consultationService;
    private final UserService userService;
    private final SymptomIntakeService symptomIntakeService;
    private final AiChatClient aiChatClient;

    public ConsultationController(ConsultationService consultationService,
                                  UserService userService,
                                  SymptomIntakeService symptomIntakeService,
                                  AiChatClient aiChatClient) {
        this.consultationService = consultationService;
        this.userService = userService;
        this.symptomIntakeService = symptomIntakeService;
        this.aiChatClient = aiChatClient;
    }

    /** 常见症状选项。 */
    private static final List<String> SYMPTOM_OPTIONS = List.of(
            "发热", "咳嗽", "咽痛", "头痛", "头晕", "腹痛", "腹泻", "恶心", "呕吐",
            "乏力", "皮疹", "胸闷", "心悸", "关节痛", "失眠");

    /** 危险信号选项。 */
    private static final List<String> RED_FLAG_OPTIONS = List.of(
            "胸痛", "呼吸困难", "意识障碍", "大出血", "剧烈头痛", "持续高热");

    @GetMapping("/by-appointment/{appointmentId}")
    public String byAppointment(@PathVariable Long appointmentId) {
        Conversation conversation = consultationService.getOrCreateGroup(appointmentId);
        return "redirect:/consultations/" + conversation.getId();
    }

    @GetMapping("/{id}")
    public String chat(@PathVariable Long id, Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName());
        consultationService.assertCanViewGroup(id, user);
        Conversation conversation = consultationService.getRequired(id);
        List<ConversationMessage> messages = consultationService.listMessages(id);
        model.addAttribute("conversation", conversation);
        model.addAttribute("messages", messages);
        model.addAttribute("lastMessageId", messages.isEmpty() ? 0L : messages.get(messages.size() - 1).getId());
        model.addAttribute("isDoctor", user.getRole() == Role.DOCTOR);
        model.addAttribute("isPatient", user.getRole() == Role.PATIENT);
        model.addAttribute("intake",
                symptomIntakeService.findByAppointmentId(conversation.getAppointment().getId()).orElse(null));
        return "consultation/chat";
    }

    // ===== 结构化症状采集 =====

    @GetMapping("/{id}/intake")
    public String intakeForm(@PathVariable Long id, Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName());
        consultationService.assertCanViewGroup(id, user);
        if (user.getRole() != Role.PATIENT) {
            return "redirect:/consultations/" + id;
        }
        Conversation conversation = consultationService.getRequired(id);
        model.addAttribute("conversation", conversation);
        model.addAttribute("form", symptomIntakeService.toForm(conversation.getAppointment().getId()));
        model.addAttribute("symptomOptions", SYMPTOM_OPTIONS);
        model.addAttribute("redFlagOptions", RED_FLAG_OPTIONS);
        model.addAttribute("severities", SeverityLevel.values());
        return "consultation/intake";
    }

    @PostMapping("/{id}/intake")
    public String saveIntake(@PathVariable Long id,
                             Authentication authentication,
                             @Valid @ModelAttribute("form") SymptomIntakeForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes ra) {
        User user = userService.findByPhone(authentication.getName());
        if (user.getRole() != Role.PATIENT) {
            return "redirect:/consultations/" + id;
        }
        Conversation conversation = consultationService.getRequired(id);
        if (bindingResult.hasErrors()) {
            model.addAttribute("conversation", conversation);
            model.addAttribute("symptomOptions", SYMPTOM_OPTIONS);
            model.addAttribute("redFlagOptions", RED_FLAG_OPTIONS);
            model.addAttribute("severities", SeverityLevel.values());
            return "consultation/intake";
        }
        symptomIntakeService.save(id, user, form);
        ra.addFlashAttribute("message", "问诊信息已保存，AI 与医生已同步");
        return "redirect:/consultations/" + id;
    }

    // ===== 实时：流式发送（SSE） =====

    @PostMapping(value = "/{id}/messages/stream", produces = "text/event-stream;charset=UTF-8")
    public StreamingResponseBody streamMessage(@PathVariable Long id,
                                               Authentication authentication,
                                               @RequestParam String content,
                                               @RequestParam(defaultValue = "chat") String action,
                                               @RequestParam(required = false) Long attachmentId) {
        User user = userService.findByPhone(authentication.getName());
        ConsultationService.AiRequest request;
        Long humanMessageId = null;
        try {
            if (user.getRole() == Role.PATIENT) {
                request = consultationService.preparePatientMessage(id, user, content, attachmentId);
            } else if ("directive".equals(action)) {
                request = consultationService.prepareDoctorDirective(id, user, content, attachmentId);
            } else {
                request = null;
                humanMessageId = consultationService.postDoctorChat(id, user, content, attachmentId);
            }
        } catch (BusinessException e) {
            String message = e.getMessage();
            return out -> SseUtil.write(newWriter(out), "error", "{\"message\":" + SseUtil.jsonString(message) + "}");
        }

        ConsultationService.AiRequest aiRequest = request;
        Long humanId = humanMessageId;
        return out -> {
            PrintWriter writer = newWriter(out);
            try {
                long hid = aiRequest != null ? aiRequest.humanMessageId() : humanId;
                SseUtil.write(writer, "start", "{\"humanMessageId\":" + hid + "}");
                if (aiRequest != null) {
                    StringBuilder full = new StringBuilder();
                    aiChatClient.streamChat(aiRequest.prompt(), token -> {
                        full.append(token);
                        SseUtil.write(writer, "delta", "{\"text\":" + SseUtil.jsonString(token) + "}");
                    });
                    Long aiId = consultationService.finishAiReply(id, aiRequest, full.toString(), aiChatClient.model());
                    SseUtil.write(writer, "done", "{\"aiMessageId\":" + aiId + "}");
                } else {
                    SseUtil.write(writer, "done", "{\"aiMessageId\":null}");
                }
            } catch (Exception e) {
                SseUtil.write(writer, "error", "{\"message\":\"AI 暂时不可用，请稍后再试\"}");
            } finally {
                writer.flush();
            }
        };
    }

    private PrintWriter newWriter(java.io.OutputStream out) {
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
    }

    // ===== 实时：增量消息（JSON） =====

    @GetMapping("/{id}/messages.json")
    @ResponseBody
    public List<MessageView> messagesJson(@PathVariable Long id,
                                          @RequestParam(defaultValue = "0") Long after,
                                          Authentication authentication) {
        User user = userService.findByPhone(authentication.getName());
        consultationService.assertCanViewGroup(id, user);
        return consultationService.listMessages(id).stream()
                .filter(m -> m.getId() != null && m.getId() > after)
                .map(MessageView::of)
                .toList();
    }

    @PostMapping("/{id}/messages.json")
    @ResponseBody
    public Map<String, Object> postMessageJson(@PathVariable Long id,
                                               Authentication authentication,
                                               @RequestParam String content,
                                               @RequestParam(defaultValue = "chat") String action,
                                               @RequestParam(required = false) Long attachmentId) {
        User user = userService.findByPhone(authentication.getName());
        try {
            if (user.getRole() == Role.DOCTOR) {
                MessageType type = "directive".equals(action) ? MessageType.DIRECTIVE : MessageType.CHAT;
                consultationService.postDoctorGroupMessage(id, user, content, type, attachmentId);
            } else {
                consultationService.postPatientMessage(id, user, content, attachmentId);
            }
            return Map.of("ok", true);
        } catch (BusinessException e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ===== 表单动作（审核 / 摘要 / 关闭） =====

    @PostMapping("/{id}/messages")
    public String postMessage(@PathVariable Long id,
                              Authentication authentication,
                              @RequestParam String content,
                              @RequestParam(defaultValue = "chat") String action,
                              @RequestParam(required = false) Long attachmentId,
                              RedirectAttributes ra) {
        User user = userService.findByPhone(authentication.getName());
        try {
            if (user.getRole() == Role.DOCTOR) {
                MessageType type = "directive".equals(action) ? MessageType.DIRECTIVE : MessageType.CHAT;
                consultationService.postDoctorGroupMessage(id, user, content, type, attachmentId);
            } else {
                consultationService.postPatientMessage(id, user, content, attachmentId);
            }
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/consultations/" + id;
    }

    @PostMapping("/{id}/drafts/{messageId}/review")
    public String reviewDraft(@PathVariable Long id,
                              @PathVariable Long messageId,
                              Authentication authentication,
                              @RequestParam String status,
                              @RequestParam(required = false) String note,
                              RedirectAttributes ra) {
        User user = userService.findByPhone(authentication.getName());
        try {
            consultationService.reviewDraft(id, user, messageId, ReviewStatus.valueOf(status), note);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/consultations/" + id;
    }

    @PostMapping("/{id}/summary")
    public String summary(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User user = userService.findByPhone(authentication.getName());
        try {
            consultationService.generateSummary(id, user);
            ra.addFlashAttribute("message", "摘要已生成");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/consultations/" + id;
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        User user = userService.findByPhone(authentication.getName());
        try {
            consultationService.close(id, user);
            ra.addFlashAttribute("message", "会话已关闭");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/consultations/" + id;
    }
}
