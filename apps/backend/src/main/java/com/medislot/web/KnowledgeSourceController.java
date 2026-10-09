package com.medislot.web;

import com.medislot.entity.KnowledgeSource;
import com.medislot.exception.BusinessException;
import com.medislot.service.KnowledgeSourceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识库来源管理（管理员 / 知识库维护员）。
 */
@Controller
@RequestMapping("/admin/knowledge/sources")
public class KnowledgeSourceController {

    private final KnowledgeSourceService sourceService;

    public KnowledgeSourceController(KnowledgeSourceService sourceService) {
        this.sourceService = sourceService;
    }

    @GetMapping
    public String list(Model model) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (KnowledgeSource source : sourceService.list()) {
            counts.put(source.getId(), sourceService.count(source.getId()));
        }
        model.addAttribute("sources", sourceService.list());
        model.addAttribute("counts", counts);
        return "admin/knowledge-sources";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        KnowledgeSource source = sourceService.getRequired(id);
        boolean nowEnabled = !source.isEnabled();
        sourceService.toggle(id, nowEnabled);
        ra.addFlashAttribute("message", "来源「" + source.getName() + "」已" + (nowEnabled ? "启用" : "停用"));
        return "redirect:/admin/knowledge/sources";
    }

    @PostMapping("/consultation/sync")
    public String syncConsultation(RedirectAttributes ra) {
        try {
            int count = sourceService.syncConsultation();
            ra.addFlashAttribute("message", "已扫描并归档 " + count + " 条问诊记录");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "同步失败：" + e.getMessage());
        }
        return "redirect:/admin/knowledge/sources";
    }
}
