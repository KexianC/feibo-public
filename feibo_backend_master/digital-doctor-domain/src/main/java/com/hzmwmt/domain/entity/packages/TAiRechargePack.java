package com.hzmwmt.domain.entity.packages;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Date;

/**
 * AI充值包表
 */
@Data
@TableName("t_ai_recharge_pack")
@Schema(
        description = "AI充值包表实体类，对应数据库表t_ai_recharge_pack，存储AI充值包的基本信息"
)
public class TAiRechargePack {

    @TableId(type = IdType.AUTO)
    @Schema(
            description = "主键ID（数据库自增，唯一标识一条充值包记录）",
            example = "1"
    )
    private Integer id;

    @TableField(value = "ai_recharge_pack_id", fill = FieldFill.INSERT)
    @Schema(
            description = "充值包业务ID",
            example = "1960611829835620354"
    )
    private Long aiRechargePackId;

    @TableField(value = "pack_code", fill = FieldFill.INSERT)
    @Schema(
            description = "充值包编码",
            example = "PACK_DUR"
    )
    private String packCode;

    @Schema(
            description = "充值包名称",
            example = "合成时长包(100分钟)"
    )
    @Size(min = 1, max = 64, message = "充值包名称长度必须为1到64个字符")
    @NotBlank(message = "充值包名称不能为空")
    private String name;

    @Schema(
            description = "价格（单位：元）",
            example = "1299.00"
    )
    @DecimalMin(value = "0.00", message = "充值包价格不能为负数")
    @NotNull(message = "充值包价格不能为空")
    private BigDecimal price;

    @TableField("ai_feature_template_id")
    @Schema(
            description = "AI功能模板ID",
            example = "1960611825770013186"
    )
    private Long aiFeatureTemplateId;

    @Schema(
            description = "算力值",
            example = "6000.00"
    )
    @DecimalMin(value = "0.00", message = "算力值不能为负数")
    @NotNull(message = "算力值不能为空")
    private BigDecimal value;

    @Schema(
            description = "描述信息",
            example = "合成时长包(100分钟)"
    )
    @Size(max = 500, message = "描述长度不能超过500个字符")
    private String description;

    @TableField("is_active")
    @Schema(
            description = "是否启用（0=启用，1=禁用）",
            example = "0"
    )
    @Min(value = 0, message = "启用状态只能为0或1")
    @Max(value = 1, message = "启用状态只能为0或1")
    @NotNull(message = "启用状态不能为空")
    private Byte isActive;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(
            description = "创建时间",
            example = "2025-08-27 15:54:21"
    )
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(
            description = "更新时间",
            example = "2025-09-18 15:52:23"
    )
    private Date updateTime;
}
