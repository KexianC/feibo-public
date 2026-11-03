package com.hzmwmt.domain.ai.service.chat.rule.filter;

import com.hzmwmt.domain.ai.adapter.repository.IAIRepository;
import com.hzmwmt.domain.ai.annotation.LogicStrategy;
import com.hzmwmt.domain.ai.model.aggregate.AIChatRuleCommandAggregate;
import com.hzmwmt.domain.ai.model.entity.AIMessageEntity;
import com.hzmwmt.domain.ai.model.entity.AIModelConfigEntity;
import com.hzmwmt.domain.ai.model.entity.RuleLogicEntity;
import com.hzmwmt.domain.ai.model.valobj.LogicCheckTypeVO;
import com.hzmwmt.domain.ai.service.chat.rule.IAIChatRulerLogicFilter;
import com.hzmwmt.domain.ai.service.chat.rule.factory.AIChatValidationFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ParameterValidationFilter
 * @Date: 2025/9/21 14:21
 */
@Slf4j
@Component
@LogicStrategy(logicMode = AIChatValidationFactory.LogicModel.PARAMETER_VALIDATION)
public class ParameterValidationFilter implements IAIChatRulerLogicFilter {
    @Resource
    private IAIRepository aiRepository;

    @Override
    public RuleLogicEntity<AIChatRuleCommandAggregate> filter(AIChatRuleCommandAggregate aggregate) throws Exception {
        log.info("AI对话校验链 - 参数校验开始，UserID: {}", aggregate.getUserId());
        AIMessageEntity aiMessageEntity = aggregate.getAiMessageEntity();
        if (aiMessageEntity.getAiConversationId() != null) {
            Boolean validatedAiConversationId = aiRepository.validateAiConversationId(
                    aggregate.getUserId(),
                    aiMessageEntity.getAiConversationId()
            );
            if (!Boolean.TRUE.equals(validatedAiConversationId)) {
                return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                        .type(LogicCheckTypeVO.REFUSE)
                        .info("会话不存在")
                        .build();
            }
        }

        AIModelConfigEntity queryAiModelConfig = aiRepository.queryAiModelLeafConfig(aiMessageEntity.getAiModelConfigId());
        if (queryAiModelConfig == null) {
            return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                    .type(LogicCheckTypeVO.REFUSE)
                    .info("模型参数异常")
                    .build();
        }
        aggregate.setAiModelConfigLeafEntity(queryAiModelConfig);
        return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                .type(LogicCheckTypeVO.SUCCESS)
                .data(aggregate)
                .build();
    }
}
