package com.hzmwmt.domain.vo;

import com.hzmwmt.domain.entity.function.TCopywriting;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 管理员文案VO类，继承自文案实体，添加用户姓名和分类名称
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "AdminCopywritingVO", description = "管理员文案VO")
public class AdminCopywritingVO extends TCopywriting implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户姓名/昵称")
    private String userName;

    @Schema(description = "文案分类名称")
    private String categoryName;

    @Schema(description = "审批流程ID")
    private Long flowId;

    @Schema(description = "审批历史评论列表")
    private List<AdminCopywritingApprovalHistoryVO> approvalHistories;

    @Schema(description = "当前操作人姓名")
    private String currentProcessorName;
}