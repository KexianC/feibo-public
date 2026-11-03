package com.hzmwmt.domain.ai.service;

import com.hzmwmt.domain.ai.model.aggregate.AIModelConfigAggregate;
import com.hzmwmt.domain.ai.model.aggregate.ChatProcessAggregate;
import com.hzmwmt.domain.ai.model.entity.AIConversationEntity;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.types.common.CursorPageResponseData;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IChatService
 * @Date: 2025/8/26 1:00
 */
public interface IChatService {

    SseEmitter completions(ChatProcessAggregate chatProcessAggregate);

    CursorPageResponseData<AIConversationEntity> queryAiConversationIndex(Long userId, Long lastCursorId, Integer size);

    CursorPageResponseData<AIMessageEntity> queryAiMessageIndex(Long userId, Long conversationId, Long lastCursorId, Integer size);

    List<AIModelConfigAggregate> queryAiModelConfigIndex();

    void updateAiConversationTitle(AIConversationEntity aiConversationEntity);

    void deleteAiConversation(AIConversationEntity aiConversationEntity);

    List<String> getAiRecommendSearch();

}
