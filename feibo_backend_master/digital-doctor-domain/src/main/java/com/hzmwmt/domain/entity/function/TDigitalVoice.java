package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数字语音实体类
 * 对应数据库表：t_digital_voice
 */
@Data
@TableName("t_digital_voice")
@Schema(
        description = "数字语音实体类，对应数据库表t_digital_voice，存储数字语音的基本信息（名称、风格、性别等属性）"
)
public class TDigitalVoice {

    /**
     * 主键 ID（自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(
            description = "主键ID（数据库自增，唯一标识一条数字语音记录）",
            example = "101"
    )
    private Integer id;

    /**
     * 数字语音业务 ID
     */
    @TableField("digital_voice_id")
    @Schema(
            description = "数字语音业务ID（关联数字语音资源的业务唯一标识）",
            example = "100001"
    )
    private Long digitalVoiceId;

    /**
     * 名称
     */
    @TableField("name")
    @Schema(
            description = "数字语音名称（如语音模型的展示名称）",
            example = "温情男声"
    )
    private String name;

    /**
     * 风格名称
     */
    @TableField("style_name")
    @Schema(
            description = "语音风格名称（描述语音的风格特征）",
            example = "温情、舒缓"
    )
    private String styleName;

    /**
     * 性别（业务自定义：如 0=未知、1=男、2=女 等）
     */
    @TableField("gender")
    @Schema(
            description = "语音性别（业务自定义：0=未知，1=男，2=女）",
            example = "1",
            allowableValues = {"0", "1", "2"}
    )
    private Integer gender;

    /**
     * 是否显示
     */
    @TableField("is_display")
    @Schema(
            description = "是否在前端显示（true=显示，false=隐藏）",
            example = "true",
            allowableValues = {"true", "false"}
    )
    private Boolean isDisplay;

    /**
     * 是否删除（逻辑删除标识）
     */
    @TableField("is_delete")
    @Schema(
            description = "逻辑删除标识（true=已删除，false=未删除）",
            example = "false",
            allowableValues = {"true", "false"}
    )
    private Boolean isDelete;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @Schema(
            description = "记录创建时间（数据库自动生成）",
            example = "2024-06-10T08:30:00"
    )
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    @Schema(
            description = "记录最后更新时间（数据变更时自动更新）",
            example = "2024-06-10T15:20:00"
    )
    private LocalDateTime updateTime;
}