package com.hzmwmt.domain.trade.service.settlement.trial.node;

import com.google.common.eventbus.EventBus;
import com.hzmwmt.domain.trade.event.PaySuccessMessageEvent;
import com.hzmwmt.domain.trade.model.entity.OrderEntity;
import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.model.entity.TradePaySuccessEntity;
import com.hzmwmt.domain.trade.service.settlement.trial.AbstractPaymentNotifySupport;
import com.hzmwmt.domain.trade.service.settlement.trial.factory.PaymentNotifyStrategyFactory;
import com.hzmwmt.types.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;


/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName SettlementNode
 * @Date: 2025/9/2 13:10
 */
@Slf4j
@Service
public class SettlementNode extends AbstractPaymentNotifySupport {
    private final EndNode endNode;
    @Resource
    private PaySuccessMessageEvent paySuccessMessageEvent;
    @Resource
    private EventBus eventBus;

    public SettlementNode(EndNode endNode) {
        this.endNode = endNode;
    }

    @Override
    public PaymentSettlementEntity apply(Map<String, String> requestParameter, PaymentNotifyStrategyFactory.DynamicContext dynamicContext) throws Exception {
        OrderEntity orderEntity = dynamicContext.getOrderEntity();
        Long orderId = orderEntity.getOrderId();
        log.info("支付通知规则树-支付成功节点 | orderId: {} 开始处理...", orderId);
        //修改状态
        int count = tradeRepository.updateOrderStatusToPaySuccess(
                OrderEntity.builder()
                        .orderId(orderId)
                        .payTime(orderEntity.getPayTime())
                        .build()
        );
        //乐观锁检测
        if (count == 1 || com.hzmwmt.domain.trade.model.valobj.OrderStatusEnumVO.PAY_SUCCESS.equals(orderEntity.getOrderStatusEnumVO())) {
            log.info("支付通知规则树-支付成功节点 | orderId: {} 乐观锁更新状态成功，开始处理订单结算。", orderId);

            //直接使用tradeRepository处理订单结算业务逻辑
            TradePaySuccessEntity tradePaySuccessEntity = TradePaySuccessEntity.builder()
                    .userId(orderEntity.getUserId())
                    .productId(orderEntity.getProductId())
                    .orderId(orderId)
                    .productTypeEnumVO(orderEntity.getProductTypeEnumVO())
                    .build();

            tradeRepository.settlementTradePayOrder(tradePaySuccessEntity);

            // 发布支付成功消息事件，用于发送通知
            PaySuccessMessageEvent.PaySuccessMessage paySuccessMessage = PaySuccessMessageEvent.PaySuccessMessage.builder()
                    .userId(orderEntity.getUserId())
                    .orderId(orderId)
                    .productId(orderEntity.getProductId())
                    .productName(orderEntity.getProductName())
                    .build();
            // 直接发布内部消息对象，因为监听器期望接收的就是这个类型
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            eventBus.post(paySuccessMessage);
                        }
                    });
            log.info("支付成功消息事件已发布 | userId: {}, orderId: {}", orderEntity.getUserId(), orderId);

            // 结算成功日志
            log.info("支付通知规则树-支付成功节点 | orderId: {} 订单结算处理完成", orderId);
        } else {
            // 乐观锁更新失败日志，这通常意味着收到了重复的回调通知
            log.warn("支付通知规则树-支付成功节点 | orderId: {} 乐观锁更新状态失败（更新0行），判定为重复通知，已忽略。", orderId);
        }

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<Map<String, String>, PaymentNotifyStrategyFactory.DynamicContext, PaymentSettlementEntity> get(Map<String, String> requestParameter, PaymentNotifyStrategyFactory.DynamicContext dynamicContext) {
        return endNode;
    }
}
