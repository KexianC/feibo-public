package com.hzmwmt.domain.ai.service.chat.model.strategy;

import com.hzmwmt.domain.ai.adapter.port.IAIPort;
import com.hzmwmt.domain.ai.adapter.repository.IAIRepository;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AbstractModelStrategyService
 * @Date: 2025/9/18 22:04
 */

public abstract class AbstractModelStrategyService {
    @Resource
    protected IAIRepository repository;
    @Resource
    protected IAIPort aiPort;


    protected List<AIMessageEntity> queryHistoryMessageList(Long userId, Long aiConversationId, Long aiConfigId, Boolean isAllHistory) {

        List<AIMessageEntity> aiMessageEntities;
        //查询会话消息列表
        if (aiConversationId == null) {
            aiMessageEntities = new ArrayList<>();
        } else {
            aiMessageEntities = repository.queryAiMessageHistory(userId, aiConversationId, aiConfigId, isAllHistory);
        }

        return aiMessageEntities;
    }

    /**
     * 抽象方法：计算总Token数（核心）
     * 包含：当前消息Token + 格式消耗Token
     */
    public abstract int calculateTotalTokens(
            AIMessageEntity currentMessage
    );

    /**
     * 抽象方法：计算单条消息的Token数
     * 由子类实现具体模型的分词逻辑
     */
    protected abstract int calculateSingleMessageTokens(String content);

//    /**
//     * 抽象方法：计算格式消耗Token（如角色标识、分隔符等）
//     */
//    protected abstract int calculateFormatTokens(int historyMessageCount);
}
