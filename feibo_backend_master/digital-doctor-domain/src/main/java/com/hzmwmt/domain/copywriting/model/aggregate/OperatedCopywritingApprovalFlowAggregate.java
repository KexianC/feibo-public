package com.hzmwmt.domain.copywriting.model.aggregate;

import com.hzmwmt.domain.copywriting.model.entity.CopywritingApprovalFlowEntity;
import com.hzmwmt.domain.copywriting.model.valobj.CopywritingDataScope;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName OperatedCopywritingApprovalFlowAggregate
 * @Date: 2025/9/10 13:41
 */
@Builder
@Data
public class OperatedCopywritingApprovalFlowAggregate {
    private CopywritingApprovalFlowEntity approvalFlowEntity;
    private Long operatorUserId;
    private Long expectedProcessorId;
    private CopywritingDataScope dataScope;
    private Long copywritingId;
    private String comment;
}
