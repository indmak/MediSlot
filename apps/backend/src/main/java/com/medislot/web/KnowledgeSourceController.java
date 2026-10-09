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
import org.springframework.web.bind.annotation.RequestParam;
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

    // ===== 外部来源 =====

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) String preset, Model model) {
        String config = "pubmed".equalsIgnoreCase(preset) ? sourceService.pubmedPresetJson() : "";
        model.addAttribute("formName", "pubmed".equalsIgnoreCase(preset) ? "PubMed 文献" : "");
        model.addAttribute("formDescription", "pubmed".equalsIgnoreCase(preset) ? "抓取 PubMed 近一年文献" : "");
        model.addAttribute("formConfig", config);
        model.addAttribute("formCron", "");
        model.addAttribute("formEnabled", true);
        model.addAttribute("mode", "create");
        model.addAttribute("pubmedPreset", true);
        return "admin/knowledge-source-form";
    }

    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String configJson,
                         @RequestParam(required = false) String scheduleCron,
                         RedirectAttributes ra) {
        try {
            sourceService.createExternal(name, description, configJson, scheduleCron);
            ra.addFlashAttribute("message", "外部来源已创建");
            return "redirect:/admin/knowledge/sources";
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/knowledge/sources/new";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        KnowledgeSource source = sourceService.getRequired(id);
        model.addAttribute("sourceId", id);
        model.addAttribute("formName", source.getName());
        model.addAttribute("formDescription", source.getDescription());
        model.addAttribute("formConfig", source.getConfigJson());
        model.addAttribute("formCron", source.getScheduleCron());
        model.addAttribute("formEnabled", source.isEnabled());
        model.addAttribute("mode", "edit");
        model.addAttribute("pubmedPreset", false);
        return "admin/knowledge-source-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String configJson,
                         @RequestParam(required = false) String scheduleCron,
                         @RequestParam(defaultValue = "false") boolean enabled,
                         RedirectAttributes ra) {
        try {
            sourceService.update(id, name, description, configJson, scheduleCron, enabled);
            ra.addFlashAttribute("message", "来源已保存");
            return "redirect:/admin/knowledge/sources";
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/knowledge/sources/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/sync")
    public String sync(@PathVariable Long id, RedirectAttributes ra) {
        try {
            int count = sourceService.syncExternal(id);
            ra.addFlashAttribute("message", "同步完成，新增 " + count + " 条文档");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "同步失败：" + e.getMessage());
        }
        return "redirect:/admin/knowledge/sources";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            sourceService.delete(id);
            ra.addFlashAttribute("message", "来源及其文档已删除");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/knowledge/sources";
    }
}
