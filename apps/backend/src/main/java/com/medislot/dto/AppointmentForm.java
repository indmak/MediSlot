package com.medislot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 预约提交表单（web 层 {@code @ModelAttribute} 绑定）。
 */
public class AppointmentForm {

    @NotNull(message = "请选择就诊时段")
    private Long scheduleId;

    @NotBlank(message = "请填写就诊人姓名")
    @Size(max = 50, message = "姓名过长")
    private String patientName;

    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String patientPhone;

    @Size(max = 500, message = "就诊原因过长")
    private String reason;

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getPatientPhone() {
        return patientPhone;
    }

    public void setPatientPhone(String patientPhone) {
        this.patientPhone = patientPhone;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
