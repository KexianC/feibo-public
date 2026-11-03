package com.hzmwmt.domain.service.function.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(name = "AdminCopywritingApprovalResponse", description = "文案审批响应DTO")
public class AdminCopywritingApprovalResponse {

    @Schema(description = "记录ID")
    private Long id;

    @Schema(description = "提交人身份", example = "医生")
    private String submitterIdentity;

    @Schema(description = "提交时间", example = "2024-03-20 09:13:29")
    private LocalDateTime time;

    @Schema(description = "提交人姓名", example = "测试账户")
    private String submitterName;

    @Schema(description = "文案标题", example = "体检时哪些检查需要空腹")
    private String title;

    @Schema(description = "当前状态", example = "提交文案")
    private String status;

    @Schema(description = "评定信息", example = "null, 所属医院/机构:P0")
    private String evaluation;

    @Schema(description = "文案ID", example = "1968215488064901122")
    private Long copywritingId;

    @Schema(description = "审核流程ID")
    private Long flowId;
}
