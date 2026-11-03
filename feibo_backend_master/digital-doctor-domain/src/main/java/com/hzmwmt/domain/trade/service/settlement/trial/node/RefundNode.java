package com.hzmwmt.domain.trade.service.settlement.trial.node;

import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.service.settlement.trial.AbstractPaymentNotifySupport;
import com.hzmwmt.domain.trade.service.settlement.trial.factory.PaymentNotifyStrategyFactory;
import com.hzmwmt.types.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 后续开发
 * @ClassName RefundNode
 * @Date: 2025/9/2 19:01
 */
@Slf4j
@Service
public class RefundNode extends AbstractPaymentNotifySupport {
    private final EndNode endNode;

    public RefundNode(EndNode endNode) {
        this.endNode = endNode;
    }

    @Override
    public PaymentSettlementEntity apply(Map<String, String> requestParameter, PaymentNotifyStrategyFactory.DynamicContext dynamicContext) throws Exception {
        String orderId = dynamicContext.getCommand().getCusOrderId();
        log.error("进入退款流程订单号为:{}", orderId);
        //请求支付接口退款，后续开发
        tradeRepository.updateOrderStatusToRefundWait(dynamicContext.getOrderEntity().getOrderId());
        //退款处理 可能只是保存不请求退款要人工审查人工退款

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<Map<String, String>, PaymentNotifyStrategyFactory.DynamicContext, PaymentSettlementEntity> get(Map<String, String> requestParameter, PaymentNotifyStrategyFactory.DynamicContext dynamicContext) {
        return endNode;
    }
}
