package com.hzmwmt.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * A single, user-visible copywriting audit event.
 *
 * <p>The audit-prefixed fields are retained for the current client contract;
 * the operation-prefixed fields are the canonical names used by the detail
 * view. Keeping both makes the history endpoints backwards compatible.</p>
 */
@Data
@Builder
public class CopywritingAuditRecordResponseDTO implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long copywritingId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long copywritingApprovalFlowId;

    private String copywritingTitle;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;

    private Date operationTime;
    private String operatorName;
    private String operatorRole;
    private String operation;
    private String comment;
    private String fromStatus;
    private Integer fromStatusCode;
    private String toStatus;
    private Integer toStatusCode;

    // Compatibility aliases used by the existing 智核医审 frontend.
    private Date auditTime;
    private String auditorName;
    private String auditorRole;
    private String auditResult;
    private String auditComment;
    private String previousStatus;
    private String currentStatus;
}
