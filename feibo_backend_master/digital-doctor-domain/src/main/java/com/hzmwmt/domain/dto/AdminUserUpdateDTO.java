package com.hzmwmt.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员修改用户信息的请求参数DTO
 */
@Data
@Schema(description = "管理员修改用户信息的请求参数，包含允许修改的用户字段（如昵称、头像、状态等）")
public class AdminUserUpdateDTO {

    @Schema(
            description = "用户昵称（长度限制：1-20个字符）",
            example = "张三_管理员",
            maxLength = 20,
            minLength = 1
    )
    @Size(min = 1, max = 20, message = "昵称长度必须为1到20个字符")
    private String nickName;       // 昵称

    @Schema(
            description = "用户头像地址（URL格式，如https://xxx.com/avatar.png）",
            example = "https://example.com/avatars/user123.png"
    )
    @Size(max = 2048, message = "头像地址长度不能超过2048个字符")
    private String avatar;         // 头像地址

    @Schema(
            description = "手机号码（11位数字，如13800138000）",
            example = "13800138000",
            pattern = "^1[3-9]\\d{9}$"  // 简单手机号正则校验
    )
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phoneNumber;    // 手机号码

    @Schema(
            description = "性别（0:未知, 1:男, 2:女）",
            example = "1",
            allowableValues = {"0", "1", "2"},  // 允许的取值
            defaultValue = "0"
    )
    @Min(value = 0, message = "性别取值必须为0、1或2")
    @Max(value = 2, message = "性别取值必须为0、1或2")
    private Integer gender;        // 性别（0:未知, 1:男, 2:女）

}
