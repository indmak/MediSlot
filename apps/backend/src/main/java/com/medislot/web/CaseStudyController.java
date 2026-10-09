package com.medislot.web;

import com.medislot.dto.MessageView;
import com.medislot.entity.Conversation;
import com.medislot.entity.ConversationMessage;
import com.medislot.entity.Role;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.service.ConsultationService;
import com.medislot.service.UserService;
import com.medislot.service.ai.AiChatClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 医生病例研究窗口（医生 + AI，私有，患者不可见）。
 */
@Controller
@RequestMapping("/cases")
public class CaseStudyController {

    private final ConsultationService consultationService;
    private final UserService userService;
    private final AiChatClient aiChatClient;

    public CaseStudyController(ConsultationService consultationService,
                               UserService userService,
                               AiChatClient aiChatClient) {
        this.consultationService = consultationService;
        this.userService = userService;
        this.aiChatClient = aiChatClient;
    }

    @GetMapping("/by-appointment/{appointmentId}")
    public String byAppointment(@PathVariable Long appointmentId, Authentication authentication) {
        User user = userService.findByPhone(authentication.getName());
        if (user.getRole() != Role.DOCTOR) {
            throw new BusinessException("仅医生可访问病例研究窗口");
        }
        Conversation conversation = consultationService.getOrCreateDoctorPrivate(appointmentId);
        consultationService.assertCanViewPrivate(conversation.getId(), user);
        return "redirect:/cases/" + conversation.getId();
    }

    @GetMapping("/{id}")
    public String chat(@PathVariable Long id, Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName());
        consultationService.assertCanViewPrivate(id, user);
        Conversation conversation = consultationService.getRequired(id);
        List<ConversationMessage> messages = consultationService.listMessages(id);
        model.addAttribute("conversation", conversation);
        model.addAttribute("messages", messages);
        model.addAttribute("lastMessageId", messages.isEmpty() ? 0L : messages.get(messages.size() - 1).getId());
        return "consultation/case";
    }

    @PostMapping(value = "/{id}/messages/stream", produces = "text/event-stream;charset=UTF-8")
    public StreamingResponseBody streamMessage(@PathVariable Long id,
                                               Authentication authentication,
                                               @RequestParam String content,
                                               @RequestParam(required = false) Long attachmentId) {
        User user = userService.findByPhone(authentication.getName());
        ConsultationService.AiRequest request;
        try {
            request = consultationService.prepareCaseMessage(id, user, content, attachmentId);
        } catch (BusinessException e) {
            String message = e.getMessage();
            return out -> SseUtil.write(newWriter(out), "error", "{\"message\":" + SseUtil.jsonString(message) + "}");
        }
        return out -> {
            PrintWriter writer = newWriter(out);
            try {
                SseUtil.write(writer, "start", "{\"humanMessageId\":" + request.humanMessageId() + "}");
                StringBuilder full = new StringBuilder();
                aiChatClient.streamChat(request.prompt(), token -> {
                    full.append(token);
                    SseUtil.write(writer, "delta", "{\"text\":" + SseUtil.jsonString(token) + "}");
                });
                Long aiId = consultationService.finishAiReply(id, request, full.toString(), aiChatClient.model());
                SseUtil.write(writer, "done", "{\"aiMessageId\":" + aiId + "}");
            } catch (Exception e) {
                SseUtil.write(writer, "error", "{\"message\":\"AI 暂时不可用，请稍后再试\"}");
            } finally {
                writer.flush();
            }
        };
    }

    private PrintWriter newWriter(OutputStream out) {
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
    }

    @GetMapping("/{id}/messages.json")
    @ResponseBody
    public List<MessageView> messagesJson(@PathVariable Long id,
                                          @RequestParam(defaultValue = "0") Long after,
                                          Authentication authentication) {
        User user = userService.findByPhone(authentication.getName());
        consultationService.assertCanViewPrivate(id, user);
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
                                               @RequestParam(required = false) Long attachmentId) {
        User user = userService.findByPhone(authentication.getName());
        try {
            consultationService.postCaseMessage(id, user, content, attachmentId);
            return Map.of("ok", true);
        } catch (BusinessException e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    @PostMapping("/{id}/messages")
    public String postMessage(@PathVariable Long id,
                              Authentication authentication,
                              @RequestParam String content,
                              @RequestParam(required = false) Long attachmentId,
                              RedirectAttributes ra) {
        User user = userService.findByPhone(authentication.getName());
        try {
            consultationService.postCaseMessage(id, user, content, attachmentId);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }
}
