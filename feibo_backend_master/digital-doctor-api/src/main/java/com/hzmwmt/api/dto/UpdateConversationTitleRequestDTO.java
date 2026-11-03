package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UpdateConversationTitleRequestDTO
 * @Date: 2025/9/25 9:56
 */
@Data
public class UpdateConversationTitleRequestDTO {
    @NotNull(message = "对话ID不能为空")
    private Long aiConversationId;

    @NotBlank(message = "对话标题不能为空")
    private String title;
}
