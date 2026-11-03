package com.hzmwmt.infrastructure.adapter.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hzmwmt.domain.ai.adapter.repository.IAIRepository;
import com.hzmwmt.domain.ai.model.aggregate.AIModelConfigAggregate;
import com.hzmwmt.domain.ai.model.aggregate.CreateChatHistoryAggregate;
import com.hzmwmt.domain.ai.model.entity.AIConversationEntity;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.entity.AIModelConfigEntity;
import com.hzmwmt.domain.ai.model.valobj.AIChatRoleEnumVO;
import com.hzmwmt.infrastructure.dao.*;
import com.hzmwmt.infrastructure.dao.po.AIConversation;
import com.hzmwmt.infrastructure.dao.po.AIMessage;
import com.hzmwmt.infrastructure.dao.po.AIModelConfig;
import com.hzmwmt.types.common.CursorPageResponseData;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import groovy.util.logging.Slf4j;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.hzmwmt.types.common.Constants.AIChatConstants.TITLE_MAX_LENGTH;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIRepository
 * @Date: 2025/8/26 1:20
 */
@Slf4j
@Repository
public class AIRepository extends AbstractRepository implements IAIRepository {
    private static final Comparator<AIModelConfig> MODEL_NODE_ORDER = Comparator
            .comparing(AIModelConfig::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(
                    AIModelConfig::getAiModelConfigId,
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AIModelConfig::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    @Resource
    private AIConversationMapper aiConversationMapper;
    @Resource
    private AIMessageMapper aiMessageMapper;
    @Resource
    private UserAIResourceMapper userAiResourceMapper;
    @Resource
    private ExpertAssistantMapMapper expertAssistantMapMapper;
    @Resource
    private UserAIResourceDetailMapper userAiResourceDetailMapper;
    @Resource
    private AIModelConfigMapper aiModelConfigMapper;

    @Override
    public CursorPageResponseData<AIConversationEntity> queryAiConversationIndex(Long userId, Long lastCursorId, Integer size) {
        return getFromDbCursorPage(() ->
                        aiConversationMapper.selectAiConversationListCursorPageByUserIdAndModel(userId, lastCursorId, size + 1),
                AIConversation::getAiConversationId,
                aiConversation -> AIConversationEntity.builder()
                        .aiConversationId(aiConversation.getAiConversationId())
                        .title(aiConversation.getTitle())
                        .createTime(aiConversation.getCreateTime())
                        .build(),
                size);
    }

    @Override
    public CursorPageResponseData<AIMessageEntity> queryAiMessageIndex(Long userId, Long conversationId, Long lastCursorId, Integer size) {
        requireOwnedConversation(userId, conversationId);
        return getFromDbCursorPage(() ->
                        aiMessageMapper.selectAiMessageListCursorPageByConversationIdAndUserId(
                                userId, conversationId, lastCursorId, size + 1
                        ),
                AIMessage::getAiMessageId,
                aiMessage -> AIMessageEntity.builder()
                        .aiMessageId(aiMessage.getAiMessageId())
                        .aiChatRoleEnumVO(AIChatRoleEnumVO.getByCode(aiMessage.getRole()))
                        .content(aiMessage.getContent())
                        .createTime(aiMessage.getCreateTime())
                        .build(),
                size);
    }

    @Override
    public List<AIMessageEntity> queryAiMessageHistory(Long userId, Long conversationId, Long modelConfigId, Boolean isAllHistory) {
        requireOwnedConversation(userId, conversationId);
        List<AIMessage> aiMessageList = aiMessageMapper
                .selectAiMessageHistoryByConversationIdAndUserIdAndAiModelConfigId(
                        userId, conversationId, modelConfigId, Boolean.TRUE.equals(isAllHistory)
                );
        if (aiMessageList != null && !aiMessageList.isEmpty()) {
            return aiMessageList.stream().map(aiMessage -> AIMessageEntity.builder()
                    .aiMessageId(aiMessage.getAiMessageId())
                    .aiChatRoleEnumVO(AIChatRoleEnumVO.getByCode(aiMessage.getRole()))
                    .content(aiMessage.getContent())
                    .createTime(aiMessage.getCreateTime())
                    .build()).collect(Collectors.toList());
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
    }

    @Override
    @Transactional(timeout = 500)
    public Long saveChatHistory(CreateChatHistoryAggregate createChatHistoryAggregate) {
        AIMessageEntity aiUserMessageEntity = createChatHistoryAggregate.getAiUserMessageEntity();
        Long aiConversationId = aiUserMessageEntity.getAiConversationId();
        Long aiModelConfigId = aiUserMessageEntity.getAiModelConfigId();
        Long userId = createChatHistoryAggregate.getUserId();

        if (!Boolean.TRUE.equals(createChatHistoryAggregate.getIsNewConversation())) {
            requireOwnedConversation(userId, aiConversationId);
        }

        // Serialize history persistence with administrative model mutations.
        // This intentionally accepts a hidden/deleted tombstone: runtime entry
        // already validated visibility before inference, and retaining the row
        // lets an in-flight paid request finish persisting after an admin removes
        // the model from all new-request surfaces.
        AIModelConfig historyModelConfig = aiModelConfigMapper
                .selectAiModelHistoryConfigByAiModelConfigIdForUpdate(aiModelConfigId);
        if (historyModelConfig == null) {
            throw modelConfigurationUnavailable();
        }

        // 用户的第一条消息
        String content = aiUserMessageEntity.getContent();
        content = content.substring(0, Math.min(content.length(), TITLE_MAX_LENGTH));
        long userToken = aiUserMessageEntity.getToken();
        long aiToken = aiUserMessageEntity.getAiToken();
        long allToken = userToken + aiToken;
        Long oldToken = Objects.requireNonNullElse(aiConversationMapper
                        .selectConsumptionByAiConversationIdAndUserId(userId, aiConversationId)
                , 0L);
        // The current persistence mapper has never projected a model price, so
        // billing has historically fallen back to zero here. Do not re-query the
        // public visible-only leaf after acquiring the tombstone-capable lock.
        BigDecimal price = BigDecimal.ZERO;
        BigDecimal oldPrice = Objects.requireNonNullElse(aiConversationMapper
                .selectBillingByAiConversationIdAndUserId(userId, aiConversationId), BigDecimal.ZERO);
        AIConversation build = AIConversation.builder()
                .aiConversationId(aiConversationId)
                .userId(userId)
                .title(content)
                .consumption(allToken + oldToken) // 先获得之前的token + 新的token
                .billing(price.add(oldPrice))
                .build();

        if (Boolean.TRUE.equals(createChatHistoryAggregate.getIsNewConversation())) {
            aiConversationMapper.insert(build);
            aiConversationId = build.getAiConversationId();
        } else {
            int updated = aiConversationMapper.updateConversationByAiConversationIdAndUserId(build);
            if (updated != 1) {
                throw conversationNotFound();
            }
        }

        // 将用户消息转换为持久化对象 AIMessage
        AIMessage userMessageToSave = AIMessage.builder()
                .aiConversationId(aiConversationId)
                .role(AIChatRoleEnumVO.USER.getCode())
                .content(aiUserMessageEntity.getContent())
                .aiModelConfigId(aiModelConfigId)
                .token(userToken)
                .build();

        // 将AI助手消息转换为持久化对象 AIMessage
        AIMessage assistantMessageToSave = AIMessage.builder()
                .aiConversationId(aiConversationId)
                .role(AIChatRoleEnumVO.ASSISTANT.getCode())
                .content(createChatHistoryAggregate.getResult())
                .aiModelConfigId(aiModelConfigId)
                  .token(aiToken)
                  .usageJson(aiUserMessageEntity.getUsageRecord() == null ? null
                          : com.alibaba.fastjson.JSON.toJSONString(aiUserMessageEntity.getUsageRecord()))
                .build();
        List<AIMessage> messagesToInsert = List.of(userMessageToSave, assistantMessageToSave);
        aiMessageMapper.insert(messagesToInsert);
        return aiConversationId;
    }


    @Override
    public Boolean validateAiConversationId(Long userId, Long aiConversationId) {
        return aiConversationMapper.validateAiConversationIdAndUserId(userId, aiConversationId);
    }


    @Override

    public Long deductDoctorAiChatAccountQuota(Long userId) {
        Long resourceIdByDoctorId = userAiResourceMapper.selectAdvancedAiModelResourceIdByUserId(userId);
        if (resourceIdByDoctorId == null) {
            //没有购买过
            return null;
        }
        //先扣除订阅资源
        Long doctorSubscriptionDetailId = userAiResourceDetailMapper.selectAndLockSubscriptionDetailIdByUserAiResourceIdAndValue(resourceIdByDoctorId, 1);
        if (doctorSubscriptionDetailId != null) {
            int doctorSubscriptionDeductCount = userAiResourceDetailMapper.deductBalanceByUserAiResourceDetailId(doctorSubscriptionDetailId, 1);
            if (doctorSubscriptionDeductCount == 1) {
                return doctorSubscriptionDetailId;
            }
        }
        //扣除直购资源
        Long doctorPurchasedDetailId = userAiResourceDetailMapper.selectAndLockPurchasedDetailIdByUserAiResourceIdAndValue(doctorSubscriptionDetailId, 1);
        if (doctorPurchasedDetailId != null) {
            int doctorPurchasedDeductCount = userAiResourceDetailMapper.deductBalanceByUserAiResourceDetailId(doctorPurchasedDetailId, 1);
            if (doctorPurchasedDeductCount == 1) {
                return doctorPurchasedDetailId;
            }
        }
        return null;
    }

    @Override
    public Long deductDoctorAssistantAiChatAccountQuota(Long userId) {
        //助理先扣医生 共享医生资源
        List<Long> expertIds = expertAssistantMapMapper.selectAssistantIdsByExpertId(userId);
        for (Long expertId : expertIds) {
            Long resourceIdByExpertId = userAiResourceMapper.selectDigitalResourceIdByUserId(expertId);
            if (resourceIdByExpertId == null) {
                //当前医生没有购买过
                continue;
            }
            //先扣除订阅资源
            Long doctorSubscriptionDetailId = userAiResourceDetailMapper.selectAndLockSubscriptionDetailIdByUserAiResourceIdAndValue(resourceIdByExpertId, 1);
            if (doctorSubscriptionDetailId != null) {
                int doctorSubscriptionDeductCount = userAiResourceDetailMapper.deductBalanceByUserAiResourceDetailId(doctorSubscriptionDetailId, 1);
                if (doctorSubscriptionDeductCount == 1) {
                    return doctorSubscriptionDetailId;
                }
            }
            //扣除直购资源
            Long doctorPurchasedDetailId = userAiResourceDetailMapper.selectAndLockPurchasedDetailIdByUserAiResourceIdAndValue(resourceIdByExpertId, 1);
            if (doctorPurchasedDetailId != null) {
                int doctorPurchasedDeductCount = userAiResourceDetailMapper.deductBalanceByUserAiResourceDetailId(doctorPurchasedDetailId, 1);
                if (doctorPurchasedDeductCount == 1) {
                    return doctorPurchasedDetailId;
                }
            }
        }
        //再扣自己的
        return deductDoctorAiChatAccountQuota(userId);

    }

    @Override
    public AIModelConfigEntity queryAiModelLeafConfig(Long modelId) {
        AIModelConfig aiModelConfig = aiModelConfigMapper.selectAiModelLeafConfigByAiModelConfigId(modelId);
        if (aiModelConfig == null) {
            return null;
        }
        return AIModelConfigEntity.builder()
                .aiModelConfigId(aiModelConfig.getAiModelConfigId())
                .modelCode(aiModelConfig.getModelCode())
                .name(aiModelConfig.getName())
                .type(aiModelConfig.getType())
                .build();
    }

    @Override
    public List<AIModelConfigAggregate> queryAiModelConfigIndex() {
        List<AIModelConfig> aiModelConfigs = aiModelConfigMapper.selectAllAiModelConfig();

        if (aiModelConfigs == null || aiModelConfigs.isEmpty()) {
            return null;
        }

        // 构建映射表
        Map<Long, List<AIModelConfig>> childrenGroupedByParentId = aiModelConfigs.stream()
                .filter(node -> node.getParentId() != null && node.getParentId() != 0)
                .collect(Collectors.groupingBy(AIModelConfig::getParentId));
        childrenGroupedByParentId.values()
                .forEach(children -> children.sort(MODEL_NODE_ORDER));

        // 找出所有根节点
        List<AIModelConfig> rootNodes = aiModelConfigs.stream()
                .filter(node -> node.getParentId() != null && node.getParentId() == 0)
                .sorted(MODEL_NODE_ORDER)
                .toList();

        // 递归构建每个根节点的完整树形结构
        List<AIModelConfigAggregate> resultAggregates = new ArrayList<>();
        for (AIModelConfig rootNode : rootNodes) {
            AIModelConfigAggregate rootAggregate = buildTree(rootNode, childrenGroupedByParentId);
            if (rootAggregate != null) {
                resultAggregates.add(rootAggregate);
            }
        }

        return resultAggregates;
    }

    @Override
    public int updateAiConversationTitle(AIConversationEntity aiConversationEntity) {
        return aiConversationMapper.update(
                new LambdaUpdateWrapper<AIConversation>()
                        .eq(AIConversation::getAiConversationId, aiConversationEntity.getAiConversationId())
                        .eq(AIConversation::getUserId, aiConversationEntity.getUserId())
                        .set(AIConversation::getTitle, aiConversationEntity.getTitle())
        );
    }

    @Override
    public int deleteAiConversation(AIConversationEntity aiConversationEntity) {
        return aiConversationMapper.delete(
                new LambdaUpdateWrapper<AIConversation>()
                        .eq(AIConversation::getAiConversationId, aiConversationEntity.getAiConversationId())
                        .eq(AIConversation::getUserId, aiConversationEntity.getUserId())
        );
    }

    private void requireOwnedConversation(Long userId, Long aiConversationId) {
        if (!Boolean.TRUE.equals(validateAiConversationId(userId, aiConversationId))) {
            throw conversationNotFound();
        }
    }

    private AppException conversationNotFound() {
        return new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "会话不存在");
    }

    private AppException modelConfigurationUnavailable() {
        return new AppException(
                ResponseCode.MANAGE_ERROR.getCode(),
                "AI 模型运行配置不可用");
    }


    private AIModelConfigAggregate buildTree(
            AIModelConfig currentNode,
            Map<Long, List<AIModelConfig>> childrenGroupedByParentId) {

        // 构建当前节点的实体
        AIModelConfigEntity currentEntity = AIModelConfigEntity.builder()
                .aiModelConfigId(currentNode.getAiModelConfigId())
                .name(currentNode.getDisplayName() == null || currentNode.getDisplayName().isBlank()
                        ? currentNode.getName()
                        : currentNode.getDisplayName())
                .type(currentNode.getType())
                .description(currentNode.getDisplayDescription() == null
                        ? currentNode.getDescription()
                        : currentNode.getDisplayDescription())
                .image(currentNode.getImage())
                .build();

        List<AIModelConfigAggregate> childrenAggregateList = new ArrayList<>();

        // 只有非叶节点才查找子节点
        if (currentNode.getIsLeaf() == null || !currentNode.getIsLeaf()) {
            // 获取当前节点的直接子节点
            List<AIModelConfig> directChildren = childrenGroupedByParentId.get(currentNode.getId());

            if (directChildren != null && !directChildren.isEmpty()) {
                for (AIModelConfig child : directChildren.stream()
                        .sorted(MODEL_NODE_ORDER)
                        .toList()) {
                    AIModelConfigAggregate childAggregate = buildTree(child, childrenGroupedByParentId);
                    if (childAggregate != null) {
                        childrenAggregateList.add(childAggregate);
                    }
                }
            }
        }

        // Hidden leaves are removed by SQL. Prune their now-empty category
        // ancestors so the frontend cannot mistake an empty category for a
        // selectable model.
        if (!Boolean.TRUE.equals(currentNode.getIsLeaf()) && childrenAggregateList.isEmpty()) {
            return null;
        }


        // 构建并返回聚合对象
        return AIModelConfigAggregate.builder()
                .data(currentEntity)
                .childrenEntityList(childrenAggregateList)
                .build();
    }

}
