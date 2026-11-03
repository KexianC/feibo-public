package com.hzmwmt.api.dto;

import com.hzmwmt.api.valid.Phone;
import com.hzmwmt.types.enums.LoginType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: 密码登录请求DTO
 * @ClassName AuthUserByPasswordRequestDTO
 * @Date: 2025/9/4 16:03
 */
@Getter
public class AuthUserByPasswordRequestDTO {
    @Schema(description = "手机号", example = "13800138000", required = true)
    @Phone(message = "手机号不能为空")
    String phoneNumber;

    @Schema(description = "密码", example = "123456", required = true)
    @NotNull(message = "密码不能为空")
    String password;

    @Schema(description = "客户端指纹", example = "device-123456", required = true)
    @NotBlank(message = "客户端指纹不能为空")
    String clientFingerprint;

    @Schema(description = "登录类型：1-前台登录，2-后台登录", example = "1", required = true)
    @NotNull(message = "登录类型不能为空")
    // 使用枚举的数字code，支持前台后台登录区分
    Integer loginType = LoginType.FRONTEND.getCode(); // 默认前台登录

    @Deprecated
    @Schema(description = "兼容旧版客户端的废弃字段，服务端忽略", deprecated = true)
    String verificationCode;
}
