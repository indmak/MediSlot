package com.medislot.web;

import com.medislot.dto.DoctorCard;
import com.medislot.entity.Department;
import com.medislot.service.DoctorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 首页：科室筛选 + 医生列表。
 */
@Controller
public class HomeController {

    private final DoctorService doctorService;

    public HomeController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) Long dept, Model model) {
        List<DoctorCard> doctors = doctorService.listDoctors(dept);
        List<Department> departments = doctorService.listDepartments();
        model.addAttribute("doctors", doctors);
        model.addAttribute("departments", departments);
        model.addAttribute("currentDeptId", dept);
        model.addAttribute("doctorCount", doctors.size());
        return "index";
    }
}
