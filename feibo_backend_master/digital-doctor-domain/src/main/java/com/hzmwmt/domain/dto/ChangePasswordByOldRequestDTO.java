package com.hzmwmt.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 旧密码修改新密码请求DTO
 */
@Data
public class ChangePasswordByOldRequestDTO {

    /**
     * 当前密码
     */
    @NotBlank(message = "当前密码不能为空")
    private String currentPassword;

    /**
     * 新密码
     */
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
