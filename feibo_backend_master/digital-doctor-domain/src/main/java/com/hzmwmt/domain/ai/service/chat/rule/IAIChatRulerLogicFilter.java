package com.hzmwmt.domain.ai.service.chat.rule;

import com.hzmwmt.domain.ai.model.aggregate.AIChatRuleCommandAggregate;
import com.hzmwmt.domain.ai.model.entity.RuleLogicEntity;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 专门给ai问答的责任链接口
 * @ClassName IAIChatRulerLogicFilter
 * @Date: 2025/9/21 14:32
 */
public interface IAIChatRulerLogicFilter {
    RuleLogicEntity<AIChatRuleCommandAggregate> filter(AIChatRuleCommandAggregate aiChatRuleCommandAggregate) throws Exception;
}
