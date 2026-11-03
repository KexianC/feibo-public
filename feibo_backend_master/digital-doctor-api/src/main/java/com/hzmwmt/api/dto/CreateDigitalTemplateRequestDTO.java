package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName CreateDigitalTemplateRequestDTO
 * @Date: 2025/9/24 21:38
 */
@Data
public class CreateDigitalTemplateRequestDTO {
    @NotBlank(message = "模板标题不能为空")
    private String title;
    @NotBlank(message = "模板内容不能为空")
    private String body;
}