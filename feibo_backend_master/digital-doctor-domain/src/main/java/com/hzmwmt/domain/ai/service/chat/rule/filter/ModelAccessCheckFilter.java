package com.hzmwmt.domain.ai.service.chat.rule.filter;

import com.hzmwmt.domain.ai.annotation.LogicStrategy;
import com.hzmwmt.domain.ai.model.aggregate.AIChatRuleCommandAggregate;
import com.hzmwmt.domain.ai.model.entity.RuleLogicEntity;
import com.hzmwmt.domain.ai.model.valobj.LogicCheckTypeVO;
import com.hzmwmt.domain.ai.service.chat.rule.IAIChatRulerLogicFilter;
import com.hzmwmt.domain.ai.service.chat.rule.factory.AIChatValidationFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ModelAccessCheckFilter
 * @Date: 2025/9/20 16:00
 */
@Slf4j
@Component
@LogicStrategy(logicMode = AIChatValidationFactory.LogicModel.MODEL_ACCESS_CHECK)
public class ModelAccessCheckFilter implements IAIChatRulerLogicFilter {

    @Override
    public RuleLogicEntity<AIChatRuleCommandAggregate> filter(AIChatRuleCommandAggregate aggregate) throws Exception {
        //模型可能和套餐绑定  待更新
        return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                .type(LogicCheckTypeVO.SUCCESS)
                .data(aggregate)
                .build();
    }
}
