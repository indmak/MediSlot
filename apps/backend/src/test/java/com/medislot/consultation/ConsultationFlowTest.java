package com.medislot.consultation;

import com.medislot.dto.AppointmentForm;
import com.medislot.entity.Appointment;
import com.medislot.entity.Conversation;
import com.medislot.entity.ConversationMessage;
import com.medislot.entity.ConversationScope;
import com.medislot.entity.Department;
import com.medislot.entity.Doctor;
import com.medislot.entity.MessageType;
import com.medislot.entity.Payment;
import com.medislot.entity.ReviewStatus;
import com.medislot.entity.Role;
import com.medislot.entity.Schedule;
import com.medislot.entity.SenderType;
import com.medislot.entity.User;
import com.medislot.repository.DepartmentRepository;
import com.medislot.repository.DoctorRepository;
import com.medislot.repository.PaymentRepository;
import com.medislot.repository.ScheduleRepository;
import com.medislot.repository.UserRepository;
import com.medislot.service.AppointmentService;
import com.medislot.service.ConsultationService;
import com.medislot.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 诊前咨询流程测试（无密钥 → 使用 Mock AI）。
 */
@SpringBootTest
@ActiveProfiles("test")
class ConsultationFlowTest {

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

    private record Setup(Appointment appointment, User patient, User doctor) {
    }

    private Setup createPaidAppointment(String suffix) {
        Department dept = departmentRepository.save(new Department("咨询测试科" + suffix, 300));
        User doctorUser = userRepository.save(new User("136" + suffix + "000", passwordEncoder.encode("x"), "咨询医生" + suffix, Role.DOCTOR));
        Doctor doctor = new Doctor(doctorUser, dept, "主治医师", "测试");
        doctor.setRegistrationFee(new BigDecimal("10.00"));
        doctorRepository.save(doctor);
        User patient = userRepository.save(new User("135" + suffix + "000", passwordEncoder.encode("x"), "咨询患者" + suffix, Role.PATIENT));
        Schedule schedule = scheduleRepository.save(new Schedule(doctor, LocalDate.now().plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 3));

        AppointmentForm form = new AppointmentForm();
        form.setScheduleId(schedule.getId());
        form.setPatientName(patient.getName());
        form.setPatientPhone(patient.getPhone());
        Appointment appointment = appointmentService.create(form, patient.getPhone());

        Payment payment = paymentRepository.findByAppointmentId(appointment.getId()).orElseThrow();
        paymentService.pay(payment.getPaymentNo(), patient.getPhone());
        return new Setup(appointment, patient, doctorUser);
    }

    private List<ConversationMessage> messages(Conversation conversation) {
        return consultationService.listMessages(conversation.getId());
    }

    @Test
    void paymentCreatesGroupConversationAndAiRepliesToPatient() {
        Setup setup = createPaidAppointment("20001");
        Conversation group = consultationService.getOrCreateGroup(setup.appointment().getId());
        assertThat(group.getScope()).isEqualTo(ConversationScope.GROUP);

        consultationService.postPatientMessage(group.getId(), setup.patient(), "我这两天发烧、咳嗽");
        List<ConversationMessage> messages = messages(group);
        assertThat(messages).anyMatch(m -> m.getSenderType() == SenderType.PATIENT);
        assertThat(messages).anyMatch(m -> m.getSenderType() == SenderType.AI);
    }

    @Test
    void doctorDirectiveProducesDraftAndAdjustRegenerates() {
        Setup setup = createPaidAppointment("20002");
        Conversation group = consultationService.getOrCreateGroup(setup.appointment().getId());

        consultationService.postDoctorGroupMessage(group.getId(), setup.doctor(), "给一个最近的饮食方案", MessageType.DIRECTIVE);
        ConversationMessage draft = messages(group).stream()
                .filter(m -> m.getMessageType() == MessageType.DRAFT)
                .findFirst().orElseThrow();
        assertThat(draft.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);

        consultationService.reviewDraft(group.getId(), setup.doctor(), draft.getId(), ReviewStatus.ADJUSTED, "少油少盐");
        long draftCount = messages(group).stream().filter(m -> m.getMessageType() == MessageType.DRAFT).count();
        assertThat(draftCount).isEqualTo(2);
        assertThat(messages(group).stream().filter(m -> m.getId().equals(draft.getId())).findFirst().orElseThrow()
                .getReviewStatus()).isEqualTo(ReviewStatus.ADJUSTED);
    }

    @Test
    void privateCaseWindowAndSummary() {
        Setup setup = createPaidAppointment("20003");
        Conversation group = consultationService.getOrCreateGroup(setup.appointment().getId());
        consultationService.postPatientMessage(group.getId(), setup.patient(), "反复胃痛一周");

        Conversation privateCase = consultationService.getOrCreateDoctorPrivate(setup.appointment().getId());
        assertThat(privateCase.getScope()).isEqualTo(ConversationScope.DOCTOR_PRIVATE);

        consultationService.postCaseMessage(privateCase.getId(), setup.doctor(), "需要补充哪些检查？");
        assertThat(messages(privateCase)).anyMatch(m -> m.getSenderType() == SenderType.AI);

        consultationService.generateSummary(group.getId(), setup.doctor());
        assertThat(consultationService.getRequired(group.getId()).getSummary()).isNotBlank();
    }
}
