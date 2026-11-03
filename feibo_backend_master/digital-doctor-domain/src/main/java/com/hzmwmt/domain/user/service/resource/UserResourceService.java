package com.hzmwmt.domain.user.service.resource;

import com.hzmwmt.domain.user.adapter.port.IUserPort;
import com.hzmwmt.domain.user.adapter.repository.IUserRepository;
import com.hzmwmt.domain.user.model.aggregate.UserAIResourceAggregate;
import com.hzmwmt.domain.user.model.aggregate.UserAISubscriptionAggregate;
import com.hzmwmt.domain.user.model.entity.AIQuotaEntity;
import com.hzmwmt.domain.user.service.IUserResourceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserResourceService
 * @Date: 2025/9/3 14:39
 */
@Service
public class UserResourceService implements IUserResourceService {
    @Resource
    private IUserRepository userRepository;
    @Resource
    private IUserPort userPort;

    @Override
    public List<UserAIResourceAggregate> queryUserAiResourceIndex(Long userId) {
        return userRepository.queryUserAiResourceIndex(userId);
    }

    @Override
    public List<UserAISubscriptionAggregate> queryAiMembershipPlanSubscriptionIndex(Long userId) {
        return userRepository.queryAiMembershipPlanSubscriptionIndex(userId);
    }

    @Override
    public void bindDefaultAiMembershipPlan(Long userId) {
        userRepository.bindDefaultAiMembershipPlan(userId);
    }

    @Override
    public void receiveAiQuota(Long userId) {
        userPort.receiveAiQuota(userId.toString());
    }

    @Override
    public AIQuotaEntity queryAiQuota(Long userId) {
        return userPort.queryAiQuota(userId.toString());
    }

}
