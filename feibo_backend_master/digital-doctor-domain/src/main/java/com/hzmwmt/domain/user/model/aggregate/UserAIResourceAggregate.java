package com.hzmwmt.domain.user.model.aggregate;

import com.hzmwmt.domain.user.model.entity.UserAIResourceEntity;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserAIResourceAggregate
 * @Date: 2025/9/23 23:38
 */
@Data
@Builder
public class UserAIResourceAggregate {
    /**
     * 用户AI资源
     */
    private UserAIResourceEntity userAiResourceEntity;
    /**
     * 订阅权益总余额
     */
    private Integer subscriptionBalance;

    /**
     * 购买资源包总余额
     */
    private Integer purchasedBalance;
}
