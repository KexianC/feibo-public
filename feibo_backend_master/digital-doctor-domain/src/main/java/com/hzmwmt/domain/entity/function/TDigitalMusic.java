package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数字音乐实体类（对应数据库表：t_digital_music）
 */
@Data
@TableName("t_digital_music")
@Schema(name = "DigitalMusic", description = "数字音乐信息实体")
public class TDigitalMusic {

    @Schema(description = "主键ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "数字音频ID", example = "1890117089631129661")
    private Long digitalAudioId;

    @Schema(description = "用户ID", example = "1976200871068037122")
    private Long userId;

    @Schema(description = "音乐名称", example = "2025-10-20 11:41:17.wav")
    private String name;

    @Schema(description = "音乐URL", example = "https://digital-doctor-os...")
    private String url;

    @Schema(description = "音乐时长（秒）", example = "6")
    private Integer duration;

    @Schema(description = "音乐类型", example = "1")
    private Integer type;

    @Schema(description = "创建时间", example = "2025-10-20T11:41:17")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2025-10-20T11:41:17")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
