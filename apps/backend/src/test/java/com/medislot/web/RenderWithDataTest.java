package com.medislot.web;

import com.medislot.entity.Department;
import com.medislot.entity.Doctor;
import com.medislot.entity.Role;
import com.medislot.entity.User;
import com.medislot.repository.DepartmentRepository;
import com.medislot.repository.DoctorRepository;
import com.medislot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 带数据的页面渲染测试：验证 Thymeleaf 读取 record 型 DTO 属性、管理后台渲染，
 * 以及 /admin 的访问控制。
 */
@SpringBootTest
@ActiveProfiles("test")
class RenderWithDataTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void homePageRendersDoctorCard() throws Exception {
        Department department = departmentRepository.save(new Department("渲染测试科", 99));
        User user = userRepository.save(new User("13700009999", passwordEncoder.encode("x"), "渲染测试医生", Role.DOCTOR));
        Doctor doctor = new Doctor(user, department, "主治医师", "测试简介");
        doctorRepository.save(doctor);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("渲染测试医生")))
                .andExpect(content().string(containsString("渲染测试科")));
    }

    @Test
    void adminRequiresLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDashboardRenders() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("管理后台")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDoctorsRenders() throws Exception {
        mockMvc.perform(get("/admin/doctors"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("医生管理")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminSchedulesRenders() throws Exception {
        mockMvc.perform(get("/admin/schedules"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("排班管理")));
    }
}
