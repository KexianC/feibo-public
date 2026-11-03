package com.hzmwmt.domain.trade.service.settlement.trial.node;

import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.service.settlement.trial.AbstractPaymentNotifySupport;
import com.hzmwmt.domain.trade.service.settlement.trial.factory.PaymentNotifyStrategyFactory.DynamicContext;
import com.hzmwmt.types.design.framework.tree.StrategyHandler;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ErrorNode
 * @Date: 2025/9/2 16:12
 */
@Slf4j
@Service
public class ErrorNode extends AbstractPaymentNotifySupport {

    @Override
    public PaymentSettlementEntity apply(Map<String, String> payload, DynamicContext context) throws Exception {
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
    }

    @Override
    public StrategyHandler<Map<String, String>, DynamicContext, PaymentSettlementEntity> get(Map<String, String> requestParameter, DynamicContext dynamicContext) {
        return defaultStrategyHandler;
    }
}
