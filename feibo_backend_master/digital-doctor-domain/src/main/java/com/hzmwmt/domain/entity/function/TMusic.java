package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 音乐实体类（对应数据库表：t_music）
 */
@Data
@TableName("t_music")
@Schema(name = "Music", description = "音乐信息实体")
public class TMusic {

    @Schema(description = "主键ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "音乐标题", example = "汽车声音")
    private String title;

    @Schema(description = "排序值", example = "0")
    private Integer sort;

    @Schema(description = "OSS音乐链接", example = "https://digital-do...")
    private String musicUrl;

    @Schema(description = "创建时间", example = "2025-10-16T15:30:25")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2025-10-16T15:30:30")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(description = "是否显示（1：是；0：否）", example = "1")
    private Integer isShow;

    @Schema(description = "逻辑删除（1：已删除；0：正常）", example = "0")
    @TableLogic
    private Integer isDeleted;
}