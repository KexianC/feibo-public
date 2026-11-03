package com.hzmwmt.domain.user.service;

import com.hzmwmt.domain.user.model.aggregate.UserAIResourceAggregate;
import com.hzmwmt.domain.user.model.aggregate.UserAISubscriptionAggregate;
import com.hzmwmt.domain.user.model.entity.AIQuotaEntity;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IUserResourceService
 * @Date: 2025/9/3 14:37
 */
public interface IUserResourceService {
    List<UserAIResourceAggregate> queryUserAiResourceIndex(Long userId);

    List<UserAISubscriptionAggregate> queryAiMembershipPlanSubscriptionIndex(Long userId);

    void bindDefaultAiMembershipPlan(Long userId);

    void receiveAiQuota(Long userId);

    AIQuotaEntity queryAiQuota(Long userId);
}
