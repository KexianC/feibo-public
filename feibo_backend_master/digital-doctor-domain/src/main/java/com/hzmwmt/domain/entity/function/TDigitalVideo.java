package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数字视频关联实体类
 * 对应数据库表：需替换为实际表名（如 t_digital_video_relation）
 */
@Data
@TableName("t_digital_video_relation") // 替换为实际数据库表名
@Schema(
        description = "数字视频关联实体类，对应数据库表t_digital_video_relation，" +
                "用于存储数字视频的关联信息及基本属性（如名称、时长、访问地址等）"
)
public class TDigitalVideo {

    /**
     * 主键 ID（自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(
            description = "主键ID（数据库自增，唯一标识一条视频关联记录）",
            example = "10001"
    )
    private Long id;

    /**
     * 数字视频关联 ID
     */
    @TableField("digital_video_id")
    @Schema(
            description = "数字视频关联ID（关联到具体数字视频资源的唯一标识）",
            example = "50001"
    )
    private Long digitalVideoId;

    /**
     * 用户 ID（允许为 NULL）
     */
    @TableField("user_id")
    @Schema(
            description = "用户ID（关联上传或拥有该视频的用户，允许为NULL，NULL时表示系统默认视频）",
            example = "100086",
            nullable = true
    )
    private Long userId;

    /**
     * 名称
     */
    @TableField("name")
    @Schema(
            description = "视频名称（视频文件的展示名称，如自定义名称或原文件名）",
            example = "城市夜景延时摄影.mp4"
    )
    private String name;

    /**
     * 是否为预览视频
     */
    @TableField("is_preview")
    @Schema(
            description = "是否为预览视频（true=预览视频，false=正式视频）",
            example = "false",
            allowableValues = {"true", "false"}
    )
    private Boolean isPreview = false;

    /**
     * 视频时长（单位：秒）
     */
    @TableField("duration")
    @Schema(
            description = "视频时长（单位：秒，记录视频的总播放时长）",
            example = "240"
    )
    private Integer duration;

    /**
     * 视频访问 URL
     */
    @TableField("url")
    @Schema(
            description = "视频访问URL（可直接通过该链接访问或播放视频资源）",
            example = "https://video.example.com/city_night.mp4"
    )
    private String url;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @Schema(
            description = "记录创建时间（数据库自动生成，标识视频关联记录的创建时刻）",
            example = "2024-05-20T15:30:00"
    )
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    @Schema(
            description = "记录更新时间（数据变更时自动更新，标识视频关联记录的最后修改时刻）",
            example = "2024-05-20T16:45:00"
    )
    private LocalDateTime updateTime;
}
