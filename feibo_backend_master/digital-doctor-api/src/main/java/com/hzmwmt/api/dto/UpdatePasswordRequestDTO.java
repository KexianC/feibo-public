package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UpdatePasswordRequestDTO
 * @Date: 2025/9/17 13:48
 */
@Builder
@Getter
public class UpdatePasswordRequestDTO {
    @NotBlank(message = "密码不能为空")
    private String password;
    @NotBlank(message = "验证码不能为空")
    private String code;
}
