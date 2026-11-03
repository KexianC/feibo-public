package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UpdateDigitalTemplateTitleRequestDTO
 * @Date: 2025/9/24 21:38
 */
@Data
public class UpdateDigitalTemplateTitleRequestDTO {
    @NotNull(message = "模板ID不能为空")
    private Long digitalTemplateId;
    @NotBlank(message = "模板标题不能为空")
    private String title;
}
