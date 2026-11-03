package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName RegisterUserRequestDTO
 * @Date: 2025/9/4 15:18
 */
@Getter
public class RegisterUserRequestDTO {
    @NotBlank(message = "客户端指纹不能为空")
    String clientFingerprint;
    /**
     * 昵称
     */
    @NotNull(message = "昵称不能为空")
    private String nickName;
    /**
     * 密码
     */
    @NotNull(message = "密码不能为空")
    private String password;
    /**
     * 手机号码
     */
    @NotNull(message = "手机号码不能为空")
    private String phoneNumber;
    /**
     * 性别 ()
     */
    @NotBlank(message = "性别不能为空")
    private String gender;
}
