package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName SymptomAnalysisRequestDTO
 * @Date: 2025/9/11 9:56
 */
@Data
public class SymptomAnalysisRequestDTO {
    private Long conversationId;
    @NotBlank(message = "内容不能为空")
    private String content;
    @NotNull(message = "模型配置不能为空")
    private Long aiModelConfigId;
    @NotNull(message = "是否携带所有消息为空")
    private Boolean isAllHistory;
}
