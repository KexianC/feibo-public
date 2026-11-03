package com.hzmwmt.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** Full copywriting content, current flow and all preceding audit events. */
@Data
@Builder
public class CopywritingAuditDetailResponseDTO implements Serializable {
    private ApprovalCopywritingResponseDTO.CopywritingDTO copywriting;
    private String categoryName;
    private String sourceName;
    private ApprovalCopywritingResponseDTO.CopywritingApprovalFlowDTO approvalFlow;
    private ApprovalCopywritingResponseDTO.ApprovalUserDTO approvalUser;
    private ApprovalCopywritingResponseDTO.ApprovalUserDTO submitterUser;
    private Boolean expertReviewer;
    private Boolean organizationReviewer;
    private List<CopywritingAuditRecordResponseDTO> auditHistory;
}
