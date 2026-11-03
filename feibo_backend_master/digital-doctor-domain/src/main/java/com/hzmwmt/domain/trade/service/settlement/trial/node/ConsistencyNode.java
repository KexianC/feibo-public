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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ConsistencyNode
 * @Date: 2025/9/2 13:08
 */
@Slf4j
@Service
public class ConsistencyNode extends AbstractPaymentNotifySupport {
    private final SwitchNode switchNode;

    public ConsistencyNode(SwitchNode switchNode) {
        this.switchNode = switchNode;
    }

    @Override
    public PaymentSettlementEntity apply(Map<String, String> payload, DynamicContext context) throws Exception {
        PaySettlementRuleCommandEntity command = context.getCommand();
        OrderEntity orderEntity = context.getOrderEntity();
        log.info("支付通知规则树-数据一致性校验节点 | orderId:{}", command.getCusOrderId());

        //超时一致性判断
        String payTimeString = command.getPayTime();
        if (payTimeString != null && !payTimeString.isBlank()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            Date paymentTime = sdf.parse(payTimeString);
            context.getOrderEntity().setPayTime(paymentTime);
            if (orderEntity.isExpired(paymentTime) && command.getTrxStatus().equals("0000")) {
                log.warn("订单已超时 | orderId:{}", command.getCusOrderId());
                //和本地时间不匹配，但是已经支付成功就按对方为准，后续有要求再按自己来，退款太麻烦
            }
        }

        // 金额一致性校验
        if (orderEntity.getTotalAmountInCents().longValue() != command.getTrxAmt()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "金额不一致");
        }
        return router(payload, context);
    }

    @Override
    public StrategyHandler<Map<String, String>, DynamicContext, PaymentSettlementEntity> get(Map<String, String> requestParameter, DynamicContext dynamicContext) {
        return switchNode;
    }
}