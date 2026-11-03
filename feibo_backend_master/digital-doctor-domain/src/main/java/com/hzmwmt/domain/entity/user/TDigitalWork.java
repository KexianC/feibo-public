package com.hzmwmt.domain.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数字作品实体类
 * 对应数据库表：t_digital_work
 */
@Data
@TableName("t_digital_work")
@Schema(
        description = "数字作品实体类，对应数据库表t_digital_work，存储数字作品的基本信息（如标题、内容、关联用户及创建时间等）"
)
public class TDigitalWork {

    /**
     * 主键ID（自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(
            description = "主键ID（数据库自增，唯一标识一条数字作品记录）",
            example = "1001"
    )
    private Integer id;

    /**
     * 数字作品ID
     */
    @TableField("digital_work_id")
    @Schema(
            description = "数字作品业务ID（关联数字作品资源的唯一业务标识）",
            example = "200001"
    )
    private Long digitalWorkId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    @Schema(
            description = "用户ID（关联创建该数字作品的用户）",
            example = "10086"
    )
    private Long userId;

    /**
     * 标题
     */
    @TableField("title")
    @Schema(
            description = "数字作品的标题（展示用，如作品名称）",
            example = "AI生成的星空插画"
    )
    private String title;

    /**
     * 内容体
     */
    @TableField("body")
    @Schema(
            description = "数字作品的内容体（可能包含作品描述、详细参数或结构化数据等）",
            example = "这幅星空插画由AI根据'银河、星云、深蓝色调'关键词生成，分辨率为1920×1080..."
    )
    private String body;

    /**
     * 外部ID
     */
    @TableField("external_id")
    @Schema(
            description = "外部系统关联ID（如第三方平台的作品标识，非必填）",
            example = "ext_platform_202409_789",
            nullable = true
    )
    private String externalId;
    @TableField("quota_biz_key")
    private String quotaBizKey;
    @TableField("quota_hidden")
    private Boolean quotaHidden;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @Schema(
            description = "作品创建时间（数据库自动生成，标识作品创建时刻）",
            example = "2024-09-15T11:30:00"
    )
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    @Schema(
            description = "作品最后更新时间（数据变更时自动更新，标识作品最后修改时刻）",
            example = "2024-09-15T14:20:00"
    )
    private LocalDateTime updateTime;

    /**
     * 视频URL
     */
    @TableField("url")
    @Schema(
            description = "数字作品的视频URL地址",
            example = "https://feibo-oss.com/video/123456.mp4",
            nullable = true
    )
    private String url;

    /**
     * 视频时长（秒）
     */
    @TableField("duration")
    @Schema(
            description = "视频时长，单位为秒",
            example = "120",
            nullable = true
    )
    private Integer duration;

    /**
     * 花费金额
     */
    @TableField("bill")
    @Schema(
            description = "生成数字作品的花费金额",
            example = "5.99",
            nullable = true
    )
    private Double bill;

//    /**
//     * 视频名称
//     * 注释掉TableField注解，因为数据库中不存在此列
//     */
//    // @TableField("name")
//    @Schema(
//            description = "视频名称（数字作品相关视频的名称标识）",
//            example = "项目演示视频.mp4"
//    )
//    private String name;

    /**
     * 是否为预览视频
     */
    @TableField("is_preview")
    @Schema(
            description = "是否为预览视频（true=预览视频，false=普通视频）",
            example = "false",
            defaultValue = "false"
    )
    private Boolean isPreview;

    /**
     * 状态字段：1表示成功，0表示失败
     */
    @TableField("status")
    @Schema(
            description = "视频生成状态：1表示成功，0表示失败",
            example = "1",
            nullable = true
    )
    private Integer status;

    @TableField("failure_reason")
    @Schema(description = "视频合成失败原因", nullable = true)
    private String failureReason;
}
