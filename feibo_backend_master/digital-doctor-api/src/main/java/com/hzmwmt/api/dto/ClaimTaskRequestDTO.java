package com.hzmwmt.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ClaimTaskRequestDTO
 * @Date: 2025/9/10 14:08
 */
@Data
public class ClaimTaskRequestDTO implements Serializable {

    @NotNull(message = "审批流程ID不能为空")
    private Long copywritingApprovalFlowId;
}
