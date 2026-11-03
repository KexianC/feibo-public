package com.hzmwmt.domain.ai.service.chat;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ChatService
 * @Date: 2025/8/26 1:09
 */

import com.hzmwmt.domain.ai.model.aggregate.AIChatRuleCommandAggregate;
import com.hzmwmt.domain.ai.model.aggregate.AIModelConfigAggregate;
import com.hzmwmt.domain.ai.model.entity.AIConversationEntity;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.entity.RuleLogicEntity;
import com.hzmwmt.domain.ai.model.valobj.LogicCheckTypeVO;
import com.hzmwmt.domain.ai.service.chat.rule.IAIChatRulerLogicFilter;
import com.hzmwmt.domain.ai.service.chat.rule.factory.AIChatValidationFactory;
import com.hzmwmt.types.common.CursorPageResponseData;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ChatService extends AbstractChatService {

    @Override
    public CursorPageResponseData<AIConversationEntity> queryAiConversationIndex(Long userId, Long lastCursorId, Integer size) {
        return repository.queryAiConversationIndex(userId, lastCursorId, size);
    }


    @Override
    public CursorPageResponseData<AIMessageEntity> queryAiMessageIndex(Long userId, Long conversationId, Long lastCursorId, Integer size) {
        return repository.queryAiMessageIndex(userId, conversationId, lastCursorId, size);
    }

    @Override
    public List<AIModelConfigAggregate> queryAiModelConfigIndex() {
        return repository.queryAiModelConfigIndex();
    }

    @Override
    public void updateAiConversationTitle(AIConversationEntity aiConversationEntity) {
        int updateCount = repository.updateAiConversationTitle(aiConversationEntity);
        if (1 != updateCount) {
            throw conversationNotFound();
        }
    }

    @Override
    public void deleteAiConversation(AIConversationEntity aiConversationEntity) {
        int deleteCount = repository.deleteAiConversation(aiConversationEntity);
        if (1 != deleteCount) {
            throw conversationNotFound();
        }
    }

    private AppException conversationNotFound() {
        return new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "会话不存在");
    }


    @Override
    protected RuleLogicEntity<AIChatRuleCommandAggregate> doCheckLogic(AIChatRuleCommandAggregate aiChatRuleCommandAggregate, String... logics) {
        try {
            Map<String, IAIChatRulerLogicFilter> logicFilterMap = aiChatValidationFactory.openLogicFilter();
            RuleLogicEntity<AIChatRuleCommandAggregate> entity = null;
            for (String code : logics) {
                if (AIChatValidationFactory.LogicModel.NULL.getCode().equals(code)) continue;
                entity = logicFilterMap.get(code).filter(aiChatRuleCommandAggregate);
                if (!LogicCheckTypeVO.SUCCESS.equals(entity.getType())) return entity;
            }
            return entity != null ? entity : RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                    .type(LogicCheckTypeVO.SUCCESS).data(aiChatRuleCommandAggregate).build();
        } catch (Exception e) {
            log.error("AI 对话校验链执行失败，异常类型: {}", e.getClass().getSimpleName());
            return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                    .type(LogicCheckTypeVO.REFUSE)
                    .info(ResponseCode.SERVICE_ERROR.getInfo())
                    .build();
        }
    }
}
