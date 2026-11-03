package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_digital_template")
@Schema(name = "TDigitalTemplate", description = "数字模板实体类")
public class TDigitalTemplate {

    @Schema(description = "主键ID", example = "1")
    @TableId
    private Integer id;

    @Schema(description = "数字模板ID", example = "197087722977067009")
    @TableField("digital_template_id")
    private Long digitalTemplateId;

    @Schema(description = "用户ID", example = "19653047755")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "模板标题", example = "智慧医疗")
    @TableField("title")
    private String title;

    @Schema(description = "模板内容JSON", example = "{\"active\":0,...}")
    @TableField("body")
    private String body;

    @Schema(description = "创建时间", example = "2025-09-24T23:47:00")
    @TableField("create_time")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2025-09-24T23:47:00")
    @TableField("update_time")
    private LocalDateTime updateTime;
}