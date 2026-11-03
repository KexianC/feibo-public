package com.hzmwmt.domain.user.model.aggregate;

import com.hzmwmt.domain.user.model.entity.UserAISubscriptionEntity;
import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserAISubscriptionAggregate
 * @Date: 2025/9/3 15:09
 */
@Builder
@Data
public class UserAISubscriptionAggregate {
    private String name;
    private String planCode;
    private UserAISubscriptionEntity userAiSubscriptionEntity;
}
