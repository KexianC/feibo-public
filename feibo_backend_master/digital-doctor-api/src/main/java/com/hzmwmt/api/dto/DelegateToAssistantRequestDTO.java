package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DelegateToAssistantRequestDTO
 * @Date: 2025/9/10 14:08
 */
@Data
public class DelegateToAssistantRequestDTO {

    @NotNull(message = "审批流程ID不能为空")
    private Long approvalFlowId;

    @NotNull(message = "助理ID不能为空")
    private Long assistantId;
}
