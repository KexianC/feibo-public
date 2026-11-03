package com.hzmwmt.domain.trade.model.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIMembershipPlanEntity
 * @Date: 2025/8/27 9:30
 */
@Builder
@Data
public class AIMembershipPlanEntity {
    /**
     * AI会员套餐id
     */
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
     * 价格 (元)
     */
    private BigDecimal price;

    /**
     * 订阅有效天数 (-1表示永久)
     */
    private Integer durationDays;


}
