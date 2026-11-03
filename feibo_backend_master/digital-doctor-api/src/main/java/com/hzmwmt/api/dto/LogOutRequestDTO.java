package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName LogOutRequestDTO
 * @Date: 2025/9/30 15:35
 */
@Data
public class LogOutRequestDTO {
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
