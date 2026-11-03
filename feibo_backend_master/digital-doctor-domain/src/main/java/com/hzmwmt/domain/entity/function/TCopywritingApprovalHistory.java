package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_copywriting_approval_history")
@Schema(name = "TCopywritingApprovalHistory", description = "文案审批历史实体")
public class TCopywritingApprovalHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "文案审批历史ID")
    private Long copywritingApprovalHistoryId;

    @Schema(description = "文案审批流程ID")
    private Long copywritingApprovalFlowId;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作状态")
    private Integer operationStatus;

    @Schema(description = "评论")
    private String comment;

    @Schema(description = "从状态")
    private String fromStatus;

    @Schema(description = "到状态")
    private String toStatus;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}