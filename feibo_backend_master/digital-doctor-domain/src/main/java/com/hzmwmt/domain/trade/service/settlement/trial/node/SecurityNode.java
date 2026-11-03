package com.hzmwmt.domain.trade.service.settlement.trial.node;

import com.hzmwmt.domain.trade.model.entity.OrderEntity;
import com.hzmwmt.domain.trade.model.entity.PaySettlementRuleCommandEntity;
import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.service.settlement.trial.AbstractPaymentNotifySupport;
import com.hzmwmt.domain.trade.service.settlement.trial.factory.PaymentNotifyStrategyFactory.DynamicContext;
import com.hzmwmt.types.design.framework.tree.StrategyHandler;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName SecurityNode
 * @Date: 2025/9/2 13:04
 */
@Slf4j
@Service
public class SecurityNode extends AbstractPaymentNotifySupport {
    private final ConsistencyNode consistencyNode;

    public SecurityNode(ConsistencyNode consistencyNode) {
        this.consistencyNode = consistencyNode;
    }

    @Override
    public PaymentSettlementEntity apply(Map<String, String> payload, DynamicContext context) throws Exception {
        PaySettlementRuleCommandEntity command = context.getCommand();
        String orderId = command.getCusOrderId();
        String trxId = command.getTrxId();
        log.info("支付通知过滤-安全与身份认证 | orderId:{}", orderId);
        //外部身份校验
        tradePort.authenticateNotification(payload);
        //内部身份校验
        OrderEntity orderEntity = tradeRepository.queryOrderByOrderId(Long.valueOf(orderId));
        if (orderEntity == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "订单不存在");
        }
        if (!Objects.equals(orderEntity.getExternalTransactionId(), trxId)) {
            log.info("支付通知过滤-安全与身份认证 | orderId:{} 发送trxId:", orderId);
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "交易号不匹配");
        }
        context.setOrderEntity(orderEntity);

        return router(payload, context);
    }

    @Override
    public StrategyHandler<Map<String, String>, DynamicContext, PaymentSettlementEntity> get(Map<String, String> payload, DynamicContext context) {
        return consistencyNode;
    }
}
