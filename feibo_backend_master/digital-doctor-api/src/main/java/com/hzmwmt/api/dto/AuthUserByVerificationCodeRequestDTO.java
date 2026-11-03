package com.hzmwmt.api.dto;

import com.hzmwmt.api.valid.Phone;
import com.hzmwmt.types.enums.LoginType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AuthUserByVerificationCodeRequestDTO
 * @Date: 2025/9/16 16:47
 */
@Getter
public class AuthUserByVerificationCodeRequestDTO {
    @Phone(message = "手机号格式不对")
    String phoneNumber;
    @NotNull(message = "验证码不能为空")
    String verificationCode;
    @NotBlank(message = "客户端指纹不能为空")
    String clientFingerprint;
    @NotNull(message = "登录类型不能为空")
    // 使用枚举的数字code
    Integer loginType = LoginType.FRONTEND.getCode(); // 默认前台登录
}
