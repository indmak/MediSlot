package com.medislot.service;

import com.medislot.entity.Appointment;
import com.medislot.entity.Conversation;
import com.medislot.entity.ConversationMessage;
import com.medislot.entity.ConversationScope;
import com.medislot.entity.Department;
import com.medislot.entity.Doctor;
import com.medislot.entity.MessageType;
import com.medislot.entity.ReviewStatus;
import com.medislot.entity.Role;
import com.medislot.entity.Schedule;
import com.medislot.entity.SenderType;
import com.medislot.entity.SymptomIntake;
import com.medislot.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 问诊记录 Markdown 组装：脱敏 + 只保留有效内容（排除指令与未通过草稿）。
 */
class ConsultationKbServiceTest {

    private final ConsultationKbService service =
            new ConsultationKbService(null, null, null, null, null, null, null);

    @Test
    void markdownIsAnonymizedAndFiltersNoise() {
        User doctorUser = new User("13800000001", "x", "张明华", Role.DOCTOR);
        Department department = new Department("内科", 1);
        Doctor doctor = new Doctor(doctorUser, department, "副主任医师", "简介");
        User patient = new User("13900000000", "x", "王小明", Role.PATIENT);
        Schedule schedule = new Schedule(doctor, LocalDate.of(2026, 1, 2),
                LocalTime.of(9, 0), LocalTime.of(9, 30), 3);
        Appointment appointment = new Appointment("A20260102-000001", schedule, patient,
                "咳嗽三天，我叫王小明，电话13900000000");
        appointment.setDiagnosisNote("上呼吸道感染");
        Conversation conversation = new Conversation(appointment, ConversationScope.GROUP, doctor);
        conversation.setSummary("主诉咳嗽，建议多休息。");

        SymptomIntake intake = new SymptomIntake(appointment);
        intake.setChiefComplaint("咳嗽");
        intake.setSymptoms("咳嗽,发热");
        intake.setTemperature(new BigDecimal("38.5"));
        intake.setRedFlags("呼吸困难");

        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(new ConversationMessage(conversation, SenderType.PATIENT, patient.getId(),
                MessageType.CHAT, "我叫王小明，手机13900000000，咳嗽"));
        messages.add(new ConversationMessage(conversation, SenderType.AI, null,
                MessageType.CHAT, "请描述病程"));
        messages.add(new ConversationMessage(conversation, SenderType.DOCTOR, doctorUser.getId(),
                MessageType.DIRECTIVE, "出个饮食方案"));
        messages.add(new ConversationMessage(conversation, SenderType.AI, null,
                MessageType.DRAFT, "未通过的草稿内容"));
        ConversationMessage approved = new ConversationMessage(conversation, SenderType.AI, null,
                MessageType.DRAFT, "建议清淡饮食");
        approved.setReviewStatus(ReviewStatus.APPROVED);
        messages.add(approved);

        String md = service.buildMarkdown(conversation, appointment, doctor, intake, messages,
                patient.getName(), patient.getPhone());

        // 结构
        assertTrue(md.contains("# 问诊记录 A20260102-000001"), "应含标题");
        assertTrue(md.contains("内科"), "应含科室");
        assertTrue(md.contains("张明华"), "应含医生姓名");
        assertTrue(md.contains("上呼吸道感染"), "应含诊断备注");
        // 脱敏
        assertFalse(md.contains("王小明"), "不应出现患者姓名");
        assertFalse(md.contains("13900000000"), "不应出现患者手机号");
        assertTrue(md.contains("患者"), "应保留“患者”称谓");
        // 内容取舍
        assertTrue(md.contains("**患者：**"), "应含患者发言");
        assertTrue(md.contains("建议清淡饮食"), "应含已通过的草稿");
        assertFalse(md.contains("未通过的草稿内容"), "不应含未通过的草稿");
        assertFalse(md.contains("出个饮食方案"), "不应含医生的指挥指令");
    }
}
