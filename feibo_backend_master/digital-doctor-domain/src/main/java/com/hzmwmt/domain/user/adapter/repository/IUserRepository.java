package com.hzmwmt.domain.user.adapter.repository;

import com.hzmwmt.domain.user.model.aggregate.UserAIResourceAggregate;
import com.hzmwmt.domain.user.model.aggregate.UserAISubscriptionAggregate;
import com.hzmwmt.domain.user.model.entity.*;
import com.hzmwmt.types.common.OffsetPageResponseData;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IUserRepository
 * @Date: 2025/9/3 14:40
 */
public interface IUserRepository {
    List<UserAIResourceAggregate> queryUserAiResourceIndex(Long userId);

    List<UserAISubscriptionAggregate> queryAiMembershipPlanSubscriptionIndex(Long userId);

    void registerAssistant(RegisterAssistantCommandEntity registerAssistantCommandEntity);

    void bindDefaultAiMembershipPlan(Long userId);

    OffsetPageResponseData<UserEntity> queryExpertConfig(Long userId, Integer page, Integer size);

    OffsetPageResponseData<UserEntity> queryAssistantConfig(Long userId, Integer page, Integer size);

    void updatePassword(UpdatePasswordCommandEntity commandEntity);

    void updateNickName(UpdateNickNameCommandEntity commandEntity);

    void updateAvatar(UpdateAvatarCommandEntity updateAvatarCommandEntity);
}
