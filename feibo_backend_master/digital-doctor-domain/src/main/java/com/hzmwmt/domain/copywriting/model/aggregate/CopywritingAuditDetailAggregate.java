package com.hzmwmt.domain.copywriting.model.aggregate;

import com.hzmwmt.domain.copywriting.model.entity.CopywritingAuditRecordEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Full audit detail read model. */
@Data
@Builder
public class CopywritingAuditDetailAggregate {
    private ApprovalCopywritingAggregate copywriting;
    private List<CopywritingAuditRecordEntity> auditHistory;
}
