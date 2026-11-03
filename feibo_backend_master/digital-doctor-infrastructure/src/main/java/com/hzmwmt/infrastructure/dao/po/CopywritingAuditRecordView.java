package com.hzmwmt.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/** Joined read-only projection used by the client audit-history endpoints. */
@Data
public class CopywritingAuditRecordView {
    private Long historyDatabaseId;
    private Long copywritingApprovalHistoryId;
    private Long copywritingApprovalFlowId;
    private Long copywritingId;
    private String copywritingTitle;
    private Long operatorId;
    private String operatorName;
    private Boolean operationStatus;
    private String comment;
    private Integer fromStatus;
    private Integer toStatus;
    private Date createTime;
}
