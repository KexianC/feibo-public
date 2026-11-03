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
 * @ClassName FailureNode
 * @Date: 2025/9/2 15:57
 */
@Slf4j
@Service
public class FailureNode extends AbstractPaymentNotifySupport {
    private final EndNode endNode;

    public FailureNode(EndNode endNode) {
        this.endNode = endNode;
    }

    @Override
    public PaymentSettlementEntity apply(Map<String, String> requestParameter, DynamicContext dynamicContext) throws Exception {
        log.info("支付通知规则树-支付失败 | orderId:{} status:FAILED",
                dynamicContext.getCommand().getCusOrderId());
        // 有定时任务补偿就算数据库done了也不需要重试
        tradeRepository.updateOrderStatusToClosedByFailure(dynamicContext.getOrderEntity().getOrderId());

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<Map<String, String>, DynamicContext, PaymentSettlementEntity> get(Map<String, String> requestParameter, DynamicContext dynamicContext) {
        return endNode;
    }

}
