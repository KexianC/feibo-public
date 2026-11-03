package com.hzmwmt.domain.copywriting.model.valobj;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(name = "CopywritingDetailVO", description = "文案详情VO")
public class CopywritingDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "文案ID")
    private Long copywritingId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名/昵称")
    private String userName;

    @Schema(description = "文案分类ID")
    private Long copywritingCategoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "文案来源ID")
    private Long copywritingSourceId;

    @Schema(description = "来源名称")
    private String sourceName;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "状态（1-启用，0-禁用）")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}