package com.medislot.web;

import com.medislot.entity.Conversation;
import com.medislot.entity.MessageType;
import com.medislot.entity.ReviewStatus;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 诊前咨询群聊（患者 + 医生 + AI）。
 */
@Controller
@RequestMapping("/consultations")
public class ConsultationController {

    private final ConsultationService consultationService;
    private final UserService userService;

    public ConsultationController(ConsultationService consultationService, UserService userService) {
        this.consultationService = consultationService;
        this.userService = userService;
    }

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
        model.addAttribute("conversation", conversation);
        model.addAttribute("messages", consultationService.listMessages(id));
        model.addAttribute("isDoctor", user.getRole() == Role.DOCTOR);
        model.addAttribute("isPatient", user.getRole() == Role.PATIENT);
        return "consultation/chat";
    }

    @PostMapping("/{id}/messages")
    public String postMessage(@PathVariable Long id,
                              Authentication authentication,
                              @RequestParam String content,
                              @RequestParam(defaultValue = "chat") String action,
                              RedirectAttributes ra) {
        User user = userService.findByPhone(authentication.getName());
        try {
            if (user.getRole() == Role.DOCTOR) {
                MessageType type = "directive".equals(action) ? MessageType.DIRECTIVE : MessageType.CHAT;
                consultationService.postDoctorGroupMessage(id, user, content, type);
            } else {
                consultationService.postPatientMessage(id, user, content);
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
