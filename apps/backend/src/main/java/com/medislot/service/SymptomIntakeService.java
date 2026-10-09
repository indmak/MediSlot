package com.medislot.service;

import com.medislot.dto.SymptomIntakeForm;
import com.medislot.entity.Appointment;
import com.medislot.entity.Conversation;
import com.medislot.entity.ConversationScope;
import com.medislot.entity.SeverityLevel;
import com.medislot.entity.SymptomIntake;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.repository.ConversationRepository;
import com.medislot.repository.SymptomIntakeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 结构化症状采集业务。
 */
@Service
public class SymptomIntakeService {

    private final SymptomIntakeRepository repository;
    private final ConversationRepository conversationRepository;

    public SymptomIntakeService(SymptomIntakeRepository repository,
                                ConversationRepository conversationRepository) {
        this.repository = repository;
        this.conversationRepository = conversationRepository;
    }

    @Transactional(readOnly = true)
    public Optional<SymptomIntake> findByAppointmentId(Long appointmentId) {
        return repository.findByAppointmentId(appointmentId);
    }

    @Transactional(readOnly = true)
    public SymptomIntakeForm toForm(Long appointmentId) {
        SymptomIntake intake = repository.findByAppointmentId(appointmentId).orElse(null);
        SymptomIntakeForm form = new SymptomIntakeForm();
        if (intake == null) {
            return form;
        }
        form.setChiefComplaint(intake.getChiefComplaint());
        form.setSymptoms(split(intake.getSymptoms()));
        form.setDuration(intake.getDuration());
        form.setSeverity(intake.getSeverity());
        form.setAccompanying(intake.getAccompanying());
        form.setPastHistory(intake.getPastHistory());
        form.setMedications(intake.getMedications());
        form.setAllergies(intake.getAllergies());
        form.setTemperature(intake.getTemperature());
        form.setRedFlags(split(intake.getRedFlags()));
        return form;
    }

    @Transactional
    public void save(Long conversationId, User patient, SymptomIntakeForm form) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException("会话不存在"));
        if (conversation.getScope() != ConversationScope.GROUP) {
            throw new BusinessException("会话类型不匹配");
        }
        Appointment appointment = conversation.getAppointment();
        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw new BusinessException("无权操作该问诊信息");
        }

        SymptomIntake intake = repository.findByAppointmentId(appointment.getId())
                .orElseGet(() -> new SymptomIntake(appointment));
        intake.setChiefComplaint(trim(form.getChiefComplaint()));
        intake.setSymptoms(join(form.getSymptoms()));
        intake.setDuration(trim(form.getDuration()));
        intake.setSeverity(form.getSeverity());
        intake.setAccompanying(trim(form.getAccompanying()));
        intake.setPastHistory(trim(form.getPastHistory()));
        intake.setMedications(trim(form.getMedications()));
        intake.setAllergies(trim(form.getAllergies()));
        intake.setTemperature(form.getTemperature());
        intake.setRedFlags(join(form.getRedFlags()));
        repository.save(intake);
    }

    /** 构建注入 AI 系统提示词的结构化信息文本；无数据时返回空串。 */
    @Transactional(readOnly = true)
    public String buildContext(Long appointmentId) {
        SymptomIntake intake = repository.findByAppointmentId(appointmentId).orElse(null);
        if (intake == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【患者预填问诊信息】\n");
        appendIfPresent(sb, "主诉", intake.getChiefComplaint());
        appendIfPresent(sb, "症状", intake.getSymptoms());
        appendIfPresent(sb, "病程", intake.getDuration());
        if (intake.getSeverity() != null) {
            appendIfPresent(sb, "严重程度", intake.getSeverity().getLabel());
        }
        appendIfPresent(sb, "伴随症状", intake.getAccompanying());
        appendIfPresent(sb, "既往史", intake.getPastHistory());
        appendIfPresent(sb, "用药", intake.getMedications());
        appendIfPresent(sb, "过敏史", intake.getAllergies());
        if (intake.getTemperature() != null) {
            appendIfPresent(sb, "体温", intake.getTemperature().toPlainString() + "℃");
        }
        if (intake.getRedFlags() != null && !intake.getRedFlags().isBlank()) {
            appendIfPresent(sb, "⚠️ 危险信号", intake.getRedFlags());
            sb.append("（如存在上述危险信号，请优先提示患者尽快就医/急诊。）\n");
        }
        return sb.toString();
    }

    /** 是否有危险信号。 */
    public static boolean hasRedFlags(String redFlags) {
        return redFlags != null && !redFlags.isBlank();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append("：").append(value).append('\n');
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = String.join(",", values.stream().filter(s -> s != null && !s.isBlank()).toList());
        return joined.isBlank() ? null : joined;
    }
}
