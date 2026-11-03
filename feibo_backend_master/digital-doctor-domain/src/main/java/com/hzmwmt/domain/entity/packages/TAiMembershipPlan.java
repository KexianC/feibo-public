package com.hzmwmt.domain.entity.packages;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Date;

/**
 * AI会员套餐表
 */
@Data
@TableName("t_ai_membership_plan")
@Schema(
        description = "AI会员套餐表实体类，对应数据库表t_ai_membership_plan，存储AI会员套餐的基本信息"
)
public class TAiMembershipPlan {

    @TableId(type = IdType.AUTO)
    @Schema(
            description = "主键ID（数据库自增，唯一标识一条会员套餐记录）",
            example = "101"
    )
    private Integer id;

    @TableField("ai_membership_plan_id")
    @Schema(
            description = "会员套餐业务ID",
            example = "1960611827637805057"
    )
    private Long aiMembershipPlanId;

    @TableField("plan_code")
    @Schema(
            description = "套餐编码",
            example = "PLAN_FREE"
    )
    private String planCode;

    @Schema(
            description = "套餐名称（会员套餐的展示名称）",
            example = "年度AI会员套餐"
    )
    @Size(min = 1, max = 64, message = "套餐名称长度必须为1到64个字符")
    @NotBlank(message = "套餐名称不能为空")
    private String name;

    @Schema(
            description = "套餐价格（单位：元，会员套餐的购买价格）",
            example = "399.00"
    )
    @DecimalMin(value = "0.00", message = "套餐价格不能为负数")
    @NotNull(message = "套餐价格不能为空")
    private BigDecimal price;

    @TableField("duration_days")
    @Schema(
            description = "有效期天数（-1表示永久）",
            example = "-1"
    )
    @Min(value = -1, message = "套餐有效期不能小于-1")
    @NotNull(message = "套餐有效期不能为空")
    private Integer durationDays;

    @TableField("is_active")
    @Schema(
            description = "是否启用（true=启用，false=禁用）",
            example = "true"
    )
    @NotNull(message = "套餐状态不能为空")
    private Boolean isActive;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(
            description = "创建时间",
            example = "2025-08-27 15:54:20"
    )
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(
            description = "更新时间",
            example = "2025-08-27 16:07:14"
    )
    private Date updateTime;
}
