package com.medislot.web;

import com.medislot.dto.KnowledgeAnswer;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.service.KnowledgeService;
import com.medislot.service.RagService;
import com.medislot.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 知识库维护后台（管理员 / 知识库维护员）。
 */
@Controller
@RequestMapping("/admin/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final RagService ragService;
    private final UserService userService;

    public KnowledgeController(KnowledgeService knowledgeService,
                               RagService ragService,
                               UserService userService) {
        this.knowledgeService = knowledgeService;
        this.ragService = ragService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("documents", knowledgeService.list());
        model.addAttribute("ragAvailable", ragService.isAvailable());
        return "admin/knowledge";
    }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(required = false) String title,
                         @RequestParam(required = false) String category,
                         Authentication authentication,
                         RedirectAttributes ra) {
        User user = userService.findByPhone(authentication.getName());
        try {
            knowledgeService.upload(file, title, category, user.getId());
            ra.addFlashAttribute("message", "文档已上传并完成索引");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/knowledge";
    }

    @PostMapping("/{id}/reindex")
    public String reindex(@PathVariable Long id, RedirectAttributes ra) {
        try {
            knowledgeService.reindex(id);
            ra.addFlashAttribute("message", "已重建索引");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/knowledge";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            knowledgeService.delete(id);
            ra.addFlashAttribute("message", "已删除");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/knowledge";
    }

    @GetMapping("/ask")
    public String askPage() {
        return "admin/knowledge-ask";
    }

    @PostMapping("/ask")
    public String ask(@RequestParam String question,
                      @RequestParam(defaultValue = "4") int topK,
                      Model model) {
        model.addAttribute("question", question);
        model.addAttribute("topK", topK);
        try {
            KnowledgeAnswer answer = ragService.answer(question, topK);
            model.addAttribute("answer", answer.answer());
            model.addAttribute("citations", answer.citations());
        } catch (Exception e) {
            model.addAttribute("error", "问答失败：" + e.getMessage());
        }
        return "admin/knowledge-ask";
    }
}
