package com.hzmwmt.domain.trade.adapter.repository;

import com.hzmwmt.domain.trade.model.aggregate.AIMembershipPlanAggregate;
import com.hzmwmt.domain.trade.model.entity.AIMembershipPlanEntity;
import com.hzmwmt.domain.trade.model.entity.AIRechargePackEntity;
import com.hzmwmt.domain.trade.model.entity.OrderEntity;
import com.hzmwmt.domain.trade.model.entity.TradePaySuccessEntity;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ITradeRepository
 * @Date: 2025/8/26 16:04
 */
public interface ITradeRepository {
    List<AIMembershipPlanAggregate> queryAiMemberShipPlanTradeIndex();

    List<AIRechargePackEntity> queryAiRechargePackTradeIndex();

    OrderEntity queryUnPayOrder(Long userId, Long productId, com.hzmwmt.domain.trade.model.valobj.ProductTypeEnumVO productType);

    AIMembershipPlanEntity queryAiMembershipPlan(Long aiMembershipPlanId);

    AIRechargePackEntity queryAiRechargePack(Long productId);

    Long saveOrder(OrderEntity orderEntity);

    List<String> queryNoPayNotifyTrxIds();
    void markPaymentQueryAttempt(String trxId);

    OrderEntity queryOrderByOrderId(Long orderId);

    int updateOrderStatus2WaitPay(OrderEntity orderEntity);

    int updateOrderStatusToClosedByFailure(long orderId);

    int updateOrderStatusToPaySuccess(OrderEntity orderEntity);

    int updateOrderStatusToClosedAfterRefund(long orderId);

    int updateOrderStatusToRefundWait(long orderId);

    void settlementTradePayOrder(TradePaySuccessEntity tradePaySuccessEntity);

}
