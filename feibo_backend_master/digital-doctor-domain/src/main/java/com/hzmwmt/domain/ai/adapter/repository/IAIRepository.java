package com.hzmwmt.domain.ai.adapter.repository;

import com.hzmwmt.domain.ai.model.aggregate.AIModelConfigAggregate;
import com.hzmwmt.domain.ai.model.aggregate.CreateChatHistoryAggregate;
import com.hzmwmt.domain.ai.model.entity.AIConversationEntity;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.entity.AIModelConfigEntity;
import com.hzmwmt.types.common.CursorPageResponseData;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IAIRepository
 * @Date: 2025/8/26 1:12
 */
public interface IAIRepository {


    CursorPageResponseData<AIConversationEntity> queryAiConversationIndex(Long userId, Long lastCursorId, Integer size);

    CursorPageResponseData<AIMessageEntity> queryAiMessageIndex(Long userId, Long conversationId, Long lastCursorId, Integer size);

    List<AIMessageEntity> queryAiMessageHistory(Long userId, Long conversationId, Long configModelId, Boolean isAllHistory);

    Long saveChatHistory(CreateChatHistoryAggregate createChatHistoryAggregate);


    Boolean validateAiConversationId(Long userId, Long aiConversationId);


    Long deductDoctorAiChatAccountQuota(Long userId);

    Long deductDoctorAssistantAiChatAccountQuota(Long userId);

    AIModelConfigEntity queryAiModelLeafConfig(Long modelId);

    List<AIModelConfigAggregate> queryAiModelConfigIndex();

    int updateAiConversationTitle(AIConversationEntity aiConversationEntity);

    int deleteAiConversation(AIConversationEntity aiConversationEntity);
}
