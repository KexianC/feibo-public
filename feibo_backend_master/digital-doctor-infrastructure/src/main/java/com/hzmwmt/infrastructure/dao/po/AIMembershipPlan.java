package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIMembershipPlanEntity
 * @Date: 2025/8/26 14:09
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_ai_membership_plan")
public class AIMembershipPlan {

    /**
     * 主键ID, 自增
     */
    private Long id;

    /**
     * AI会员套餐id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long aiMembershipPlanId;

    /**
     * 套餐唯一编码
     */
    private String planCode;

    /**
     * 套餐名称
     */
    private String name;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 订阅有效天数, -1表示永久
     */
    private Integer durationDays;

    /**
     * 是否上架: 0=上架, 1=不上架, 默认0
     */
    private Boolean isActive;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}