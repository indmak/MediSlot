package com.medislot.web;

import com.medislot.dto.AppointmentForm;
import com.medislot.entity.Appointment;
import com.medislot.entity.AppointmentStatus;
import com.medislot.entity.Schedule;
import com.medislot.entity.User;
import com.medislot.service.AppointmentService;
import com.medislot.service.ScheduleService;
import com.medislot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/**
 * 患者预约流程：确认页、提交、我的预约、取消。
 */
@Controller
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final ScheduleService scheduleService;
    private final UserService userService;

    public AppointmentController(AppointmentService appointmentService,
                                 ScheduleService scheduleService,
                                 UserService userService) {
        this.appointmentService = appointmentService;
        this.scheduleService = scheduleService;
        this.userService = userService;
    }

    @GetMapping("/appointments/new")
    public String newForm(@ModelAttribute("form") AppointmentForm form,
                          @ModelAttribute("scheduleId") Long scheduleId,
                          Authentication authentication, Model model) {
        Schedule schedule = scheduleService.getRequired(scheduleId);
        User patient = userService.findByPhone(authentication.getName());
        form.setScheduleId(scheduleId);
        form.setPatientName(patient.getName());
        form.setPatientPhone(patient.getPhone());
        model.addAttribute("schedule", schedule);
        return "appointment/form";
    }

    @PostMapping("/appointments")
    public String create(@Valid @ModelAttribute("form") AppointmentForm form,
                         BindingResult bindingResult,
                         Authentication authentication, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("schedule", scheduleService.getRequired(form.getScheduleId()));
            return "appointment/form";
        }
        Appointment appointment = appointmentService.create(form, authentication.getName());
        return "redirect:/appointments/" + appointment.getId() + "/success";
    }

    @GetMapping("/appointments/{id}/success")
    public String success(@PathVariable Long id, Authentication authentication, Model model) {
        Appointment appointment = appointmentService.get(id);
        if (!appointment.getPatient().getPhone().equals(authentication.getName())) {
            return "redirect:/appointments/my";
        }
        model.addAttribute("appointment", appointment);
        return "appointment/success";
    }

    @GetMapping("/appointments/my")
    public String my(Authentication authentication, Model model) {
        List<Appointment> all = appointmentService.listMine(authentication.getName());
        model.addAttribute("pending", all.stream().filter(a -> a.getStatus() == AppointmentStatus.PENDING).toList());
        model.addAttribute("completed", all.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).toList());
        model.addAttribute("cancelled", all.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).toList());
        return "appointment/my";
    }

    @PostMapping("/appointments/{id}/cancel")
    public String cancel(@PathVariable Long id, Authentication authentication) {
        appointmentService.cancel(id, authentication.getName());
        return "redirect:/appointments/my";
    }
}
