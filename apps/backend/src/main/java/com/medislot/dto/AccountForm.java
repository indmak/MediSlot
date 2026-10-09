package com.medislot.dto;

import com.medislot.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员 / 知识库维护员账号创建表单。
 */
public class AccountForm {

    @NotBlank(message = "请填写手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "请填写姓名")
    @Size(max = 50, message = "姓名过长")
    private String name;

    @NotBlank(message = "请设置密码")
    @Size(min = 8, max = 100, message = "密码至少 8 位")
    private String password;

    @NotNull(message = "请选择角色")
    private Role role;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
