package com.hzmwmt.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 用户创建请求DTO
 */
public class UserCreateRequest {
    @Schema(description = "用户昵称", example = "张三", required = true)
    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称长度不能超过20个字符")
    private String nickName;

    @Schema(description = "手机号", example = "13800138000", required = true)
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phoneNumber;

    @Schema(description = "性别：1-男，2-女", example = "1")
    @Min(value = 0, message = "性别取值必须为0、1或2")
    @Max(value = 2, message = "性别取值必须为0、1或2")
    private Integer gender;

    @Schema(description = "微信OpenID", example = "oXJ280xxxxxxxx")
    @Size(max = 128, message = "OpenID长度不能超过128个字符")
    private String openId;

    @Schema(description = "角色ID，可选，默认使用医生角色", example = "1")
    @Positive(message = "角色ID必须为正数")
    private Long roleId; // 可选，默认使用医生角色

    // getter and setter methods
    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public String getOpenId() {
        return openId;
    }

    public void setOpenId(String openId) {
        this.openId = openId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}
