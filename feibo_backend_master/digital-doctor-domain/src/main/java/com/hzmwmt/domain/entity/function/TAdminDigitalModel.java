package com.hzmwmt.domain.entity.function;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_admin_digital_model") // 表名更新
@Schema(name = "TAdminDigitalModel", description = "数字人模型实体")
public class TAdminDigitalModel {

    @Schema(description = "唯一标识", example = "1889917869750554624")
    @TableId("id")
    private String id;

    @Schema(description = "数字人模型名称", example = "都健")
    @TableField("model_name") // 原speaker_name → model_name
    private String modelName;

    @Schema(description = "数字人模型唯一ID", example = "Dujian_2437_no_alpha_512")
    @TableField("model_id") // 原speaker_id → model_id
    private String modelId;

    @Schema(description = "模型类型", example = "two")
    @TableField("model_type") // 原speaker_type → model_type
    private String modelType;

    @Schema(description = "模型宽度", example = "1080")
    @TableField("model_width") // 原speaker_width → model_width
    private Integer modelWidth;

    @Schema(description = "模型高度", example = "1920")
    @TableField("model_height") // 原speaker_height → model_height
    private Integer modelHeight;

    @Schema(description = "模型图片OSS链接")
    @TableField("model_url") // 原speaker_url → model_url
    private String modelUrl;

    @Schema(description = "模型版本类型", example = "avatar_v3")
    @TableField("model_model_type") // 原speaker_model_type → model_model_type
    private String modelModelType;

    @Schema(description = "性别", example = "female")
    @TableField("gender")
    private String gender;

    @Schema(description = "音频ID", example = "dujian_meet_24k")
    @TableField("magic_audio_id")
    private String magicAudioId;

    @Schema(description = "头部x坐标", example = "370")
    @TableField("headx")
    private Integer headx;

    @Schema(description = "头部y坐标", example = "556")
    @TableField("heady")
    private Integer heady;

    @Schema(description = "头部旋转参数", example = "244")
    @TableField("headr")
    private Integer headr;

    @Schema(description = "头部图片OSS链接")
    @TableField("head_url")
    private String headUrl;

    @Schema(description = "头部宽度", example = "732")
    @TableField("head_width")
    private Integer headWidth;

    @Schema(description = "头部高度", example = "732")
    @TableField("head_height")
    private Integer headHeight;

    @Schema(description = "自定义类型", example = "self")
    @TableField("custom_type")
    private String customType;

    @Schema(description = "创建时间", example = "2025-02-13T14:01:47")
    @TableField("created")
    private LocalDateTime created;

    @Schema(description = "状态", example = "published")
    @TableField("status")
    private String status;

    @Schema(description = "最后更新时间", example = "2025-02-13T14:04:08")
    @TableField("last_update_time")
    private LocalDateTime lastUpdateTime;

    @Schema(description = "形象图片链接")
    @TableField("figure_url")
    private String figureUrl;

    @Schema(description = "是否推荐（1：是；0：否）")
    @TableField("recommend")
    private Integer recommend;

    @Schema(description = "企业微信ID")
    @TableField("wwid")
    private String wwid;

    @Schema(description = "排序号")
    @TableField("sort_num")
    private Integer sortNum;

    @Schema(description = "联系电话")
    @TableField("phone")
    private String phone;

    @Schema(description = "VIP类型")
    @TableField("vip_type")
    private String vipType;

    @Schema(description = "发布时间")
    @TableField("published")
    private LocalDateTime published;

    @Schema(description = "关联产品")
    @TableField("product")
    private String product;

    @Schema(description = "GIF动图链接")
    @TableField("gif_url")
    private String gifUrl;

    @Schema(description = "Alpha源来源")
    @TableField("alpha_source_origin")
    private String alphaSourceOrigin;

    @Schema(description = "模型演示链接")
    @TableField("model_demo_url") // 原speaker_demo_url → model_demo_url
    private String modelDemoUrl;

    @Schema(description = "医院")
    @TableField("hospital")
    private String hospital;

    // 新增逻辑删除字段
    @Schema(description = "逻辑删除标识（0：未删除；1：已删除）", example = "0")
    @TableLogic // 标记为逻辑删除字段
    @TableField(value = "is_deleted", fill = FieldFill.INSERT) // 插入时自动填充默认值
    private Integer isDeleted;
}