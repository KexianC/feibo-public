package com.hzmwmt.domain.ai.service.chat.rule.filter;

import com.hzmwmt.domain.ai.adapter.repository.IAIRepository;
import com.hzmwmt.domain.ai.annotation.LogicStrategy;
import com.hzmwmt.domain.ai.model.aggregate.AIChatRuleCommandAggregate;
import com.hzmwmt.domain.ai.model.entity.RuleLogicEntity;
import com.hzmwmt.domain.ai.model.valobj.LogicCheckTypeVO;
import com.hzmwmt.domain.ai.service.chat.rule.IAIChatRulerLogicFilter;
import com.hzmwmt.domain.ai.service.chat.rule.factory.AIChatValidationFactory;
import com.hzmwmt.types.enums.ResourceUserTypeEnumVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName UserAccountQuotaFilter
 * @Date: 2025/9/20 15:02
 */
@Slf4j
@Component
@LogicStrategy(logicMode = AIChatValidationFactory.LogicModel.USER_QUOTA_DEDUCTION)
public class UserAccountQuotaFilter implements IAIChatRulerLogicFilter {

    @Resource
    private IAIRepository aiRepository;

    @Override
    public RuleLogicEntity<AIChatRuleCommandAggregate> filter(AIChatRuleCommandAggregate aggregate) throws Exception {
        // 只有高级模型才需要扣费
        if (Boolean.FALSE.equals(aggregate.getAiModelConfigLeafEntity().getType())) {
            return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                    .type(LogicCheckTypeVO.SUCCESS)
                    .data(aggregate)
                    .build();
        }

        ResourceUserTypeEnumVO userType = aggregate.getResourceUserTypeEnumVO();

        Long resourceDetailId;
        if (userType.equals(ResourceUserTypeEnumVO.DOCTOR)) {
            resourceDetailId = aiRepository.deductDoctorAiChatAccountQuota(aggregate.getUserId());
        } else {
            resourceDetailId = aiRepository.deductDoctorAssistantAiChatAccountQuota(aggregate.getUserId());
        }

        if (resourceDetailId == null) {
            return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                    .type(LogicCheckTypeVO.REFUSE)
                    .info("账户余额不足")
                    .build();
        }

        aggregate.setAiResourceDetailId(resourceDetailId);

        return RuleLogicEntity.<AIChatRuleCommandAggregate>builder()
                .type(LogicCheckTypeVO.SUCCESS)
                .data(aggregate)
                .build();
    }

}
