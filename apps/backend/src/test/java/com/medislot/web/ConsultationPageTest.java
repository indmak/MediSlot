package com.medislot.web;

import com.medislot.dto.AppointmentForm;
import com.medislot.entity.Appointment;
import com.medislot.entity.Conversation;
import com.medislot.entity.Department;
import com.medislot.entity.Doctor;
import com.medislot.entity.Payment;
import com.medislot.entity.Role;
import com.medislot.entity.Schedule;
import com.medislot.entity.User;
import com.medislot.repository.DepartmentRepository;
import com.medislot.repository.DoctorRepository;
import com.medislot.repository.PaymentRepository;
import com.medislot.repository.ScheduleRepository;
import com.medislot.repository.UserRepository;
import com.medislot.service.AppointmentService;
import com.medislot.service.ConsultationService;
import com.medislot.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 诊前咨询相关页面渲染测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class ConsultationPageTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private ConsultationService consultationService;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ScheduleRepository scheduleRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String patientPhone;
    private String doctorPhone;
    private Conversation group;
    private Conversation privateCase;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        String suffix = String.valueOf(System.nanoTime() % 100000);
        Department dept = departmentRepository.save(new Department("咨询页面科" + suffix, 400));
        User doctorUser = userRepository.save(new User("136" + suffix + "00", passwordEncoder.encode("x"), "页面医生", Role.DOCTOR));
        Doctor doctor = new Doctor(doctorUser, dept, "主治医师", "测试");
        doctor.setRegistrationFee(new BigDecimal("10.00"));
        doctorRepository.save(doctor);
        User patient = userRepository.save(new User("135" + suffix + "00", passwordEncoder.encode("x"), "页面患者", Role.PATIENT));
        Schedule schedule = scheduleRepository.save(new Schedule(doctor, LocalDate.now().plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 3));

        AppointmentForm form = new AppointmentForm();
        form.setScheduleId(schedule.getId());
        form.setPatientName(patient.getName());
        form.setPatientPhone(patient.getPhone());
        Appointment appointment = appointmentService.create(form, patient.getPhone());
        Payment payment = paymentRepository.findByAppointmentId(appointment.getId()).orElseThrow();
        paymentService.pay(payment.getPaymentNo(), patient.getPhone());

        patientPhone = patient.getPhone();
        doctorPhone = doctorUser.getPhone();
        group = consultationService.getOrCreateGroup(appointment.getId());
        privateCase = consultationService.getOrCreateDoctorPrivate(appointment.getId());
        consultationService.postPatientMessage(group.getId(), patient, "头痛两天了");
    }

    @Test
    void patientGroupChatRenders() throws Exception {
        mockMvc.perform(get("/consultations/" + group.getId()).with(user(patientPhone).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("诊前咨询")));
    }

    @Test
    void doctorGroupChatRenders() throws Exception {
        mockMvc.perform(get("/consultations/" + group.getId()).with(user(doctorPhone).roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("指挥 AI")));
    }

    @Test
    void doctorCaseWindowRenders() throws Exception {
        mockMvc.perform(get("/cases/" + privateCase.getId()).with(user(doctorPhone).roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("病例研究")));
    }

    @Test
    void adminSettingsRenders() throws Exception {
        mockMvc.perform(get("/admin/settings").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("设置中心")));
    }

    @Test
    void messagesJsonReturnsIncrement() throws Exception {
        mockMvc.perform(get("/consultations/" + group.getId() + "/messages.json?after=0")
                        .with(user(patientPhone).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].senderType").exists())
                .andExpect(jsonPath("$[0].time").exists());
    }

    @Test
    void postMessageJsonCreatesReply() throws Exception {
        mockMvc.perform(post("/consultations/" + group.getId() + "/messages.json")
                        .with(user(doctorPhone).roles("DOCTOR"))
                        .with(csrf())
                        .param("content", "给一个最近的饮食方案")
                        .param("action", "directive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
}
