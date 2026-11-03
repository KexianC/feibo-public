package com.hzmwmt.infrastructure.adapter.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hzmwmt.domain.user.adapter.repository.IUserRepository;
import com.hzmwmt.domain.user.model.aggregate.UserAIResourceAggregate;
import com.hzmwmt.domain.user.model.aggregate.UserAISubscriptionAggregate;
import com.hzmwmt.domain.user.model.entity.*;
import com.hzmwmt.domain.user.model.valobj.AIFeatureTemplateVO;
import com.hzmwmt.infrastructure.dao.*;
import com.hzmwmt.infrastructure.dao.po.*;
import com.hzmwmt.types.common.Constants;
import com.hzmwmt.types.common.OffsetPageResponseData;
import com.hzmwmt.types.enums.GenderEnumVO;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import com.hzmwmt.types.util.TimeAgoUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.hzmwmt.types.common.Constants.PERMANENT_END_TIME;

/**
 * @author huihui
 * @version v1.0
 * @Description: 用户仓储层实现
 * @Date: 2025/9/3 14:42
 */
@Slf4j
@Repository
public class UserRepository extends AbstractRepository implements IUserRepository {
    @Resource
    PasswordEncoder passwordEncoder;
    @Resource
    private UserAIResourceMapper userAiResourceMapper;
    @Resource
    private AIFeatureTemplateMapper aiFeatureTemplateMapper;
    @Resource
    private UserAISubscriptionMapper userAiSubscriptionMapper;
    @Resource
    private AIMembershipPlanMapper aiMembershipPlanMapper;
    @Resource
    private AIPlanFeatureMapMapper aiPlanFeatureMapMapper;
    @Resource
    private AIFeatureMapper aiFeatureMapper;
    @Resource
    private UserAIResourceDetailMapper userAiResourceDetailMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private ExpertAssistantMapMapper expertAssistantMapMapper;


    @Override
    public List<UserAIResourceAggregate> queryUserAiResourceIndex(Long userId) {
        // 1. 查询用户资源列表
        List<UserAIResource> userAiResourceList = userAiResourceMapper.selectUserAiResourceListByUserId(userId);
        if (userAiResourceList == null || userAiResourceList.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 批量查询模版（避免N+1）
        List<Long> templateIds = userAiResourceList.stream()
                .map(UserAIResource::getAiFeatureTemplateId)
                .distinct()
                .toList();
        List<AIFeatureTemplate> aiFeatureTemplates = aiFeatureTemplateMapper.selectAIFeatureTemplateListByAIFeatureTemplateIds(templateIds);
        Map<Long, AIFeatureTemplate> templateMap = aiFeatureTemplates.stream()
                .collect(Collectors.toMap(AIFeatureTemplate::getAiFeatureTemplateId, t -> t));

        // 3. 批量查询资源明细并分组计算（内存聚合）
        List<Long> resourceIds = userAiResourceList.stream()
                .map(UserAIResource::getUserAiResourceId)
                .toList();

        LambdaQueryWrapper<UserAIResourceDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.in(UserAIResourceDetail::getUserAiResourceId, resourceIds);
        List<UserAIResourceDetail> allDetails = userAiResourceDetailMapper.selectList(detailWrapper);

        // 使用 Integer 作为 Value 类型，因为聚合结果要求是 Integer
        Map<Long, Integer> purchasedMap = new HashMap<>();
        Map<Long, Integer> subscriptionMap = new HashMap<>();

        if (allDetails != null) {
            for (UserAIResourceDetail detail : allDetails) {
                Long rId = detail.getUserAiResourceId();
                // 数据库取出的 BigDecimal 转为 int (null则视为0)
                int amount = detail.getBalance() == null ? 0 : detail.getBalance().intValue();

                if (Boolean.TRUE.equals(detail.getType())) {
                    purchasedMap.merge(rId, amount, Integer::sum);
                } else {
                    subscriptionMap.merge(rId, amount, Integer::sum);
                }
            }
        }

        // 4. 组装返回结果
        return userAiResourceList.stream().map(userAiResource -> {
            AIFeatureTemplate aiFeatureTemplate = templateMap.get(userAiResource.getAiFeatureTemplateId());
            // 如果模版数据缺失，跳过该项，防止报错
            if (aiFeatureTemplate == null) {
                return null;
            }

            return UserAIResourceAggregate.builder()
                    .userAiResourceEntity(
                            UserAIResourceEntity.builder()
                                    .userAiResourceId(userAiResource.getUserAiResourceId())
                                    .aiFeatureTemplateVO(
                                            AIFeatureTemplateVO.builder()
                                                    .templateCode(aiFeatureTemplate.getTemplateCode())
                                                    .name(aiFeatureTemplate.getName())
                                                    .description(aiFeatureTemplate.getDescription())
                                                    .build()
                                    )
                                    .build()
                    )
                    // 使用 Integer 类型，默认值为 0
                    .purchasedBalance(purchasedMap.getOrDefault(userAiResource.getUserAiResourceId(), 0))
                    .subscriptionBalance(subscriptionMap.getOrDefault(userAiResource.getUserAiResourceId(), 0))
                    .build();
        }).filter(Objects::nonNull).toList();
    }

    @Override
    public List<UserAISubscriptionAggregate> queryAiMembershipPlanSubscriptionIndex(Long userId) {
        List<UserAISubscription> userAiSubscriptionList = userAiSubscriptionMapper.selectUserAiSubscriptionListByUserId(userId);
        if (userAiSubscriptionList == null || userAiSubscriptionList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> aiMembershipPlanIds = userAiSubscriptionList.stream()
                .map(UserAISubscription::getAiMembershipPlanId)
                .distinct()
                .toList();

        List<AIMembershipPlan> aiMembershipPlanList = aiMembershipPlanMapper.selectValidMembershipPlanByAiMembershipPlanIds(aiMembershipPlanIds);

        // 容错处理：如果不为空则转Map，为空则空Map
        Map<Long, AIMembershipPlan> planMap;
        if (aiMembershipPlanList == null || aiMembershipPlanList.isEmpty()) {
            planMap = new HashMap<>();
        } else {
            planMap = aiMembershipPlanList.stream()
                    .collect(Collectors.toMap(AIMembershipPlan::getAiMembershipPlanId, p -> p));
        }

        return userAiSubscriptionList.stream().map(userAiSubscription -> {
            AIMembershipPlan aiMembershipPlan = planMap.get(userAiSubscription.getAiMembershipPlanId());
            // 如果套餐已下架查不到，过滤掉，不阻断列表展示
            if (aiMembershipPlan == null) {
                return null;
            }
            return UserAISubscriptionAggregate.builder()
                    .userAiSubscriptionEntity(
                            UserAISubscriptionEntity.builder()
                                    .userAiSubscriptionId(userAiSubscription.getUserAiSubscriptionId())
                                    .endTime(userAiSubscription.getEndTime())
                                    .startTime(userAiSubscription.getStartTime())
                                    .build()
                    )
                    .planCode(aiMembershipPlan.getPlanCode())
                    .name(aiMembershipPlan.getName())
                    .build();
        }).filter(Objects::nonNull).toList();
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerAssistant(RegisterAssistantCommandEntity registerAssistantCommandEntity) {
        // 注册子用户
        User user = User.builder()
                .avatar(Constants.DEFAULT_AVATAR)
                .gender(registerAssistantCommandEntity.getGenderEnumVO().getCode())
                .nickName(registerAssistantCommandEntity.getNickName())
                .phoneNumber(registerAssistantCommandEntity.getPhoneNumber())
                .password(passwordEncoder.encode(registerAssistantCommandEntity.getPassword()))
                .build();
        userMapper.insert(user);

        // 绑定关系
        expertAssistantMapMapper.insert(ExpertAssistantMap.builder()
                .expertUserId(registerAssistantCommandEntity.getExpertId())
                .assistantUserId(user.getUserId())
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindDefaultAiMembershipPlan(Long userId) {
        AIMembershipPlan defaultAiMembershipPlan = aiMembershipPlanMapper.selectDefaultAiMembershipPlan();
        if (defaultAiMembershipPlan == null) {
            throw new AppException(ResponseCode.MANAGE_ERROR.getCode(), "未找到默认会员权益");
        }

        Date now = new Date();
        // 先计算时间，确保后续插入 UserAIResourceDetail 时时间已确定
        Date endTime = TimeAgoUtil.calculateEndTime(now, defaultAiMembershipPlan.getDurationDays());

        // 订阅永久不能是null
        if (endTime == null) {
            endTime = Date.from(PERMANENT_END_TIME.atZone(ZoneId.systemDefault()).toInstant());
        }

        List<Long> aiFeatureIds = aiPlanFeatureMapMapper.selectAiFeatureIdsByMembershipPlanId(defaultAiMembershipPlan.getAiMembershipPlanId());

        if (aiFeatureIds != null && !aiFeatureIds.isEmpty()) {
            List<AIFeature> aiFeatureList = aiFeatureMapper.selectAiFeatureListByAiFeatureIds(aiFeatureIds);

            for (AIFeature feature : aiFeatureList) {
                UserAIResource newResource = UserAIResource.builder()
                        .userId(userId)
                        .aiFeatureTemplateId(feature.getAiFeatureTemplateId())
                        .build();
                userAiResourceMapper.insert(newResource);

                // 插入资源详情
                userAiResourceDetailMapper.insert(
                        UserAIResourceDetail.builder()
                                .userAiResourceId(newResource.getUserAiResourceId())
                                // 将 feature.getValue() 转成 BigDecimal
                                .balance(feature.getValue())
                                .type(false)
                                .expiryTime(endTime)
                                .build()
                );
            }
            log.info("用户 {} 绑定默认会员权益成功：已为用户 {} 批量更新/插入 {} 项会员权益。", userId, userId, aiFeatureList.size());
        }

        // 插入用户订阅记录
        userAiSubscriptionMapper.insert(
                UserAISubscription.builder()
                        .userId(userId)
                        .aiMembershipPlanId(defaultAiMembershipPlan.getAiMembershipPlanId())
                        .startTime(now)
                        .endTime(endTime)
                        .orderId(null)
                        .build()
        );
    }

    @Override
    public OffsetPageResponseData<UserEntity> queryExpertConfig(Long userId, Integer page, Integer size) {
        return getFromDbOffsetPage(
                (offset, s) -> expertAssistantMapMapper.selectExpertIdsPageByAssistantId(userId, offset, s),
                () -> {
                    LambdaQueryWrapper<ExpertAssistantMap> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ExpertAssistantMap::getAssistantUserId, userId);
                    return expertAssistantMapMapper.selectCount(wrapper);
                },
                ids -> userMapper.selectUserListByUserIds(ids),
                User::getUserId,
                user -> UserEntity.builder()
                        .userId(user.getUserId())
                        .nickName(user.getNickName())
                        .avatar(user.getAvatar())
                        .phoneNumber(user.getPhoneNumber())
                        .genderEnumVO(GenderEnumVO.fromCode(user.getGender()))
                        .build(),
                page,
                size
        );
    }

    @Override
    public OffsetPageResponseData<UserEntity> queryAssistantConfig(Long userId, Integer page, Integer size) {
        return getFromDbOffsetPage(
                (offset, s) -> expertAssistantMapMapper.selectAssistantIdsPageByExpertId(userId, offset, s),
                () -> {
                    LambdaQueryWrapper<ExpertAssistantMap> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ExpertAssistantMap::getExpertUserId, userId);
                    return expertAssistantMapMapper.selectCount(wrapper);
                },
                ids -> userMapper.selectUserListByUserIds(ids),
                User::getUserId,
                user -> UserEntity.builder()
                        .userId(user.getUserId())
                        .nickName(user.getNickName())
                        .avatar(user.getAvatar())
                        .phoneNumber(user.getPhoneNumber())
                        .genderEnumVO(GenderEnumVO.fromCode(user.getGender()))
                        .build(),
                page,
                size
        );
    }

    @Override
    public void updatePassword(UpdatePasswordCommandEntity commandEntity) {
        String encodePassword = passwordEncoder.encode(commandEntity.getPassword());

        LambdaUpdateWrapper<User> userLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        userLambdaUpdateWrapper
                .eq(User::getPhoneNumber, commandEntity.getPhoneNumber())
                .set(User::getPassword, encodePassword);
        userMapper.update(userLambdaUpdateWrapper);
    }

    @Override
    public void updateNickName(UpdateNickNameCommandEntity commandEntity) {
        LambdaUpdateWrapper<User> userLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        userLambdaUpdateWrapper
                .eq(User::getUserId, commandEntity.getUserId())
                .set(User::getNickName, commandEntity.getNewNickName());
        userMapper.update(userLambdaUpdateWrapper);
    }

    @Override
    public void updateAvatar(UpdateAvatarCommandEntity updateAvatarCommandEntity) {
        LambdaUpdateWrapper<User> userUpdateWrapper = new LambdaUpdateWrapper<>();
        userUpdateWrapper
                .eq(User::getUserId, updateAvatarCommandEntity.getUserId())
                .set(User::getAvatar, updateAvatarCommandEntity.getUrl());
        userMapper.update(userUpdateWrapper);
    }

}