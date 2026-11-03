package com.hzmwmt.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员文案审批历史VO类
 *
 * @author system
 */
@Data
@Schema(name = "AdminCopywritingApprovalHistoryVO", description = "管理员文案审批历史VO")
public class AdminCopywritingApprovalHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "评论内容")
    private String comment;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作状态")
    private Integer operationStatus;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "来源状态")
    private String fromStatus;

    @Schema(description = "目标状态")
    private String toStatus;

    @Schema(description = "操作人姓名")
    private String operatorName;
}