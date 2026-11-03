package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ApprovalRequestDTO
 * @Date: 2025/9/10 14:07
 */
@Data
public class ApprovalRequestDTO {
    @NotNull(message = "审批流程ID不能为空")
    private Long copywritingApprovalFlowId;

    @NotNull(message = "文案ID不能为空")
    private Long copywritingId;

    @NotBlank(message = "审批意见不能为空")
    private String comment;
}
