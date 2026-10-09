package com.medislot.web;

import com.medislot.entity.Conversation;
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
        // 归属校验：仅该预约的医生
        consultationService.assertCanViewPrivate(conversation.getId(), user);
        return "redirect:/cases/" + conversation.getId();
    }

    @GetMapping("/{id}")
    public String chat(@PathVariable Long id, Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName());
        consultationService.assertCanViewPrivate(id, user);
        Conversation conversation = consultationService.getRequired(id);
        model.addAttribute("conversation", conversation);
        model.addAttribute("messages", consultationService.listMessages(id));
        return "consultation/case";
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
