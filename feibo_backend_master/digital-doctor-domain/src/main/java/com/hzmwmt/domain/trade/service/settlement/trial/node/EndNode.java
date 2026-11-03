package com.hzmwmt.domain.trade.service.settlement.trial.node;

import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.service.settlement.trial.AbstractPaymentNotifySupport;
import com.hzmwmt.domain.trade.service.settlement.trial.factory.PaymentNotifyStrategyFactory.DynamicContext;
import com.hzmwmt.types.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName EndNode
 * @Date: 2025/9/2 13:09
 */
@Slf4j
@Service
public class EndNode extends AbstractPaymentNotifySupport {


    @Override
    public PaymentSettlementEntity apply(Map<String, String> requestParameter, DynamicContext dynamicContext) throws Exception {
        return PaymentSettlementEntity.builder()
                .message("success")
                .build();
    }

    @Override
    public StrategyHandler<Map<String, String>, DynamicContext, PaymentSettlementEntity> get(Map<String, String> requestParameter, DynamicContext dynamicContext) {
        // 作为终止节点，返回默认处理器表示流程结束
        return defaultStrategyHandler;
    }


}