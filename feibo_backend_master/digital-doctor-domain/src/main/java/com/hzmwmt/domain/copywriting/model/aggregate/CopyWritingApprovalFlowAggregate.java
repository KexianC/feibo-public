package com.hzmwmt.domain.copywriting.model.aggregate;

import com.hzmwmt.domain.copywriting.model.entity.CopywritingApprovalFlowEntity;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 当前工作流聚合对象
 * @ClassName CopyWritingApprovalFlowAggregate
 * @Date: 2025/9/10 13:18
 */
@Data
@Builder
public class CopyWritingApprovalFlowAggregate {
    private Long operatorUserId;
    private CopywritingApprovalFlowEntity approvalFlowEntity;
}
