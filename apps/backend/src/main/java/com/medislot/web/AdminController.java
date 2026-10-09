package com.medislot.web;

import com.medislot.dto.DepartmentForm;
import com.medislot.dto.DoctorForm;
import com.medislot.dto.ScheduleForm;
import com.medislot.entity.Doctor;
import com.medislot.service.AdminService;
import com.medislot.service.DepartmentService;
import com.medislot.service.DoctorService;
import com.medislot.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 管理后台：仪表盘、科室、医生、排班。访问需 ADMIN 角色（见 SecurityConfig）。
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final DepartmentService departmentService;
    private final DoctorService doctorService;
    private final ScheduleService scheduleService;

    public AdminController(AdminService adminService,
                           DepartmentService departmentService,
                           DoctorService doctorService,
                           ScheduleService scheduleService) {
        this.adminService = adminService;
        this.departmentService = departmentService;
        this.doctorService = doctorService;
        this.scheduleService = scheduleService;
    }

    // ===== 仪表盘 =====

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", adminService.stats());
        return "admin/dashboard";
    }

    // ===== 科室 =====

    @GetMapping("/departments")
    public String departments(Model model) {
        model.addAttribute("departments", departmentService.list());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new DepartmentForm());
        }
        return "admin/departments";
    }

    @PostMapping("/departments")
    public String createDepartment(@Valid @ModelAttribute("form") DepartmentForm form,
                                   BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            flashForm(ra, form, bindingResult);
            return "redirect:/admin/departments";
        }
        departmentService.create(form);
        ra.addFlashAttribute("message", "科室已添加");
        return "redirect:/admin/departments";
    }

    // ===== 医生 =====

    @GetMapping("/doctors")
    public String doctors(Model model) {
        model.addAttribute("doctors", doctorService.listAdminRows());
        model.addAttribute("departments", doctorService.listDepartments());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new DoctorForm());
        }
        return "admin/doctors";
    }

    @PostMapping("/doctors")
    public String createDoctor(@Valid @ModelAttribute("form") DoctorForm form,
                               BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            flashForm(ra, form, bindingResult);
            return "redirect:/admin/doctors";
        }
        doctorService.createDoctor(form);
        ra.addFlashAttribute("message", "医生已添加");
        return "redirect:/admin/doctors";
    }

    @GetMapping("/doctors/{id}/edit")
    public String editDoctor(@PathVariable Long id, Model model) {
        Doctor doctor = doctorService.getDoctor(id);
        DoctorForm form = new DoctorForm();
        form.setName(doctor.getName());
        form.setPhone(doctor.getUser().getPhone());
        form.setDepartmentId(doctor.getDepartment().getId());
        form.setTitle(doctor.getTitle());
        form.setBio(doctor.getBio());
        model.addAttribute("form", form);
        model.addAttribute("doctorId", id);
        model.addAttribute("departments", doctorService.listDepartments());
        return "admin/doctor-form";
    }

    @PostMapping("/doctors/{id}")
    public String updateDoctor(@PathVariable Long id,
                               @Valid @ModelAttribute("form") DoctorForm form,
                               BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("doctorId", id);
            model.addAttribute("departments", doctorService.listDepartments());
            return "admin/doctor-form";
        }
        doctorService.updateDoctor(id, form);
        return "redirect:/admin/doctors";
    }

    // ===== 排班 =====

    @GetMapping("/schedules")
    public String schedules(@RequestParam(required = false) Long doctorId, Model model) {
        model.addAttribute("doctors", doctorService.listAdminRows());
        model.addAttribute("selectedDoctorId", doctorId);
        if (doctorId != null) {
            model.addAttribute("schedules", scheduleService.listByDoctorForAdmin(doctorId));
        }
        if (!model.containsAttribute("form")) {
            ScheduleForm form = new ScheduleForm();
            form.setDoctorId(doctorId);
            model.addAttribute("form", form);
        }
        return "admin/schedules";
    }

    @PostMapping("/schedules")
    public String createSchedule(@Valid @ModelAttribute("form") ScheduleForm form,
                                 BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            flashForm(ra, form, bindingResult);
            return "redirect:/admin/schedules?doctorId=" + form.getDoctorId();
        }
        scheduleService.create(form);
        ra.addFlashAttribute("message", "排班已添加");
        return "redirect:/admin/schedules?doctorId=" + form.getDoctorId();
    }

    private void flashForm(RedirectAttributes ra, Object form, BindingResult bindingResult) {
        ra.addFlashAttribute("form", form);
        ra.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
    }
}
