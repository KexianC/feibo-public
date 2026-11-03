package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_copywriting_approval_flow")
@Schema(name = "TCopywritingApprovalFlow", description = "文案审批流程实体")
public class TCopywritingApprovalFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "文案审批流程ID")
    private Long copywritingApprovalFlowId;

    @Schema(description = "文案ID")
    @TableField("copywriting_id")
    private Long copywritingId;

    @Schema(description = "当前状态")
    @TableField("current_status")
    private Integer currentStatus;

    @Schema(description = "当前处理人ID")
    private Long currentProcessorId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}