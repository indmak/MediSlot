package com.medislot.web;

import com.medislot.dto.MessageView;
import com.medislot.entity.Conversation;
import com.medislot.entity.ConversationMessage;
import com.medislot.entity.Role;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.service.ConsultationService;
import com.medislot.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    public CaseStudyController(ConsultationService consultationService, UserService userService) {
        this.consultationService = consultationService;
        this.userService = userService;
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
                                               @RequestParam String content) {
        User user = userService.findByPhone(authentication.getName());
        try {
            consultationService.postCaseMessage(id, user, content);
            return Map.of("ok", true);
        } catch (BusinessException e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    @PostMapping("/{id}/messages")
    public String postMessage(@PathVariable Long id,
                              Authentication authentication,
                              @RequestParam String content,
                              RedirectAttributes ra) {
        User user = userService.findByPhone(authentication.getName());
        try {
            consultationService.postCaseMessage(id, user, content);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }
}
