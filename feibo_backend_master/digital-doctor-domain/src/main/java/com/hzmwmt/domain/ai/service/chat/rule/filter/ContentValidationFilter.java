package com.hzmwmt.domain.ai.service.chat.rule.filter;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.hzmwmt.domain.ai.annotation.LogicStrategy;
import com.hzmwmt.domain.ai.model.aggregate.AIChatRuleCommandAggregate;
import com.hzmwmt.domain.ai.model.entity.RuleLogicEntity;
import com.hzmwmt.domain.ai.model.valobj.LogicCheckTypeVO;
import com.hzmwmt.domain.ai.service.chat.rule.IAIChatRulerLogicFilter;
import com.hzmwmt.domain.ai.service.chat.rule.factory.AIChatValidationFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.hzmwmt.types.common.Constants.AIChatConstants.CONTENT_MAX_LENGTH;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ContentValidationFilter
 * @Date: 2025/9/20 14:42
 */
@Slf4j
@Component
@LogicStrategy(logicMode = AIChatValidationFactory.LogicModel.CONTENT_VALIDATION)
public class ContentValidationFilter implements IAIChatRulerLogicFilter {

    @Resource
    private SensitiveWordBs sensitiveWordBs;

    @Override
    public RuleLogicEntity<AIChatRuleCommandAggregate> filter(AIChatRuleCommandAggregate aggregate) throws Exception {
        String content = aggregate.getAiMessageEntity().getContent();

        if (content.length() > CONTENT_MAX_LENGTH) {
            return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                    .type(LogicCheckTypeVO.REFUSE)
                    .info("内容长度超出限制")
                    .build();
        }
        if (sensitiveWordBs.contains(content)) {
            return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                    .type(LogicCheckTypeVO.REFUSE)
                    .info("内容包含敏感词")
                    .build();
        }
        return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                .type(LogicCheckTypeVO.SUCCESS)
                .data(aggregate)
                .build();
    }
}

