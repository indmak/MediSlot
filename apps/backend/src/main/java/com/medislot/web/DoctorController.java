package com.medislot.web;

import com.medislot.dto.ScheduleDayView;
import com.medislot.entity.Department;
import com.medislot.service.AppointmentService;
import com.medislot.service.DoctorService;
import com.medislot.service.ScheduleService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * 医生相关页面：公开的医生列表 / 详情，以及医生工作台。
 */
@Controller
public class DoctorController {

    private final DoctorService doctorService;
    private final ScheduleService scheduleService;
    private final AppointmentService appointmentService;

    public DoctorController(DoctorService doctorService,
                            ScheduleService scheduleService,
                            AppointmentService appointmentService) {
        this.doctorService = doctorService;
        this.scheduleService = scheduleService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/doctors")
    public String list(@RequestParam(required = false) Long dept, Model model) {
        model.addAttribute("doctors", doctorService.listDoctors(dept));
        model.addAttribute("departments", doctorService.listDepartments());
        model.addAttribute("currentDeptId", dept);
        model.addAttribute("doctorCount", doctorService.countDoctors(dept));
        return "doctor/list";
    }

    @GetMapping("/doctors/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("doctor", doctorService.getDoctorCard(id));
        List<ScheduleDayView> days = scheduleService.listGroupedByDay(id, LocalDate.now(), ScheduleService.DEFAULT_DAYS);
        model.addAttribute("days", days);
        return "doctor/detail";
    }

    // ===== 医生工作台（/doctor/**，需 DOCTOR 角色） =====

    @GetMapping("/doctor/today")
    public String today(Authentication authentication, Model model) {
        model.addAttribute("appointments", appointmentService.listDoctorToday(authentication.getName()));
        model.addAttribute("today", LocalDate.now());
        return "doctor/today";
    }

    @PostMapping("/doctor/appointments/{id}/checkin")
    public String checkIn(@PathVariable Long id, Authentication authentication) {
        appointmentService.checkIn(id, authentication.getName());
        return "redirect:/doctor/today";
    }

    @PostMapping("/doctor/appointments/{id}/complete")
    public String complete(@PathVariable Long id,
                           @RequestParam(required = false) String note,
                           Authentication authentication) {
        appointmentService.complete(id, authentication.getName(), note);
        return "redirect:/doctor/today";
    }
}
