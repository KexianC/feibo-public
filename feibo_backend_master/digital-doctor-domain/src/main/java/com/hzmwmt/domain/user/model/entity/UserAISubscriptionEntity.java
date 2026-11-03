package com.hzmwmt.domain.user.model.entity;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserAISubscriptionEntity
 * @Date: 2025/9/3 15:07
 */
@Builder
@Data
public class UserAISubscriptionEntity {

    /**
     * 用户AI订阅id
     */
    private Long userAiSubscriptionId;

    /**
     * 订阅开始时间
     */
    private Date startTime;

    /**
     * 订阅结束时间
     */
    private Date endTime;

}
