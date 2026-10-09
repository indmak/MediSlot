package com.medislot.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 管理员新增 / 编辑医生表单。
 * 新增时 {@code password} 必填；编辑时可留空表示不修改。
 */
public class DoctorForm {

    @NotBlank(message = "请填写姓名")
    @Size(max = 50, message = "姓名过长")
    private String name;

    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 仅新增时使用。 */
    @Size(max = 100, message = "密码过长")
    private String password;

    @NotNull(message = "请选择科室")
    private Long departmentId;

    @Size(max = 50, message = "职称过长")
    private String title;

    @Size(max = 1000, message = "简介过长")
    private String bio;

    /** 挂号费（元）。 */
    @NotNull(message = "请填写挂号费")
    @DecimalMin(value = "0.00", message = "挂号费不能为负")
    private BigDecimal registrationFee = BigDecimal.ZERO;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public BigDecimal getRegistrationFee() {
        return registrationFee;
    }

    public void setRegistrationFee(BigDecimal registrationFee) {
        this.registrationFee = registrationFee;
    }
}
