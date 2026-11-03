package com.hzmwmt.domain.copywriting.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/** Read model for a copywriting audit event. */
@Data
@Builder
public class CopywritingAuditRecordEntity {
    private Long copywritingApprovalHistoryId;
    private Long copywritingApprovalFlowId;
    private Long copywritingId;
    private String copywritingTitle;
    private Long operatorId;
    private String operatorName;
    private Boolean approved;
    private Integer fromStatus;
    private Integer toStatus;
    private String comment;
    private Date operationTime;
}
