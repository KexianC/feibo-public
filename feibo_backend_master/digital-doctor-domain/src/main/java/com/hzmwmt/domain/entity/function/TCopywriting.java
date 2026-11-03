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
@TableName("t_copywriting")
@Schema(name = "TCopywriting", description = "文案实体")
public class TCopywriting implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    @TableId(type = IdType.INPUT)
    private Long id;

    @Schema(description = "文案ID")
    @TableField("copywriting_id")
    private Long copywritingId;

    @Schema(description = "用户ID")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "文案分类ID")
    @TableField("copywriting_category_id")
    private Long copywritingCategoryId;

    @Schema(description = "文案来源ID")
    @TableField("copywriting_source_id")
    private Long copywritingSourceId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "状态（tinyint类型）")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}