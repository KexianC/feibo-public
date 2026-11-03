package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserAISubscriptionAggregate
 * @Date: 2025/8/26 14:13
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_user_ai_subscription")
public class UserAISubscription {

    /**
     * 主键ID, 自增
     */
    private Long id;

    /**
     * 用户AI订阅id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long userAiSubscriptionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 订阅的会员套餐主键ID
     */
    private Long aiMembershipPlanId;

    /**
     * 来源订单的业务ID
     */
    private Long orderId;

    /**
     * 订阅开始时间
     */
    private Date startTime;

    /**
     * 订阅结束时间
     */
    private Date endTime;


    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
