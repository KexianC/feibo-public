package com.hzmwmt.domain.copywriting.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DelegateCommandEntity
 * @Date: 2025/9/8 12:28
 */
@Builder
@Data
public class DelegateCommandEntity {
    private Long expertId;
    private Long assistantId;
    private Long approvalFlowId;
}
