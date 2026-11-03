package com.hzmwmt.domain.trade.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName PaymentHandlerStrategyEnumVO
 * @Date: 2025/9/29 22:52
 */
@Getter
@AllArgsConstructor
public enum PaymentHandlerStrategyEnumVO {

    // ================================================================
    // 场景一：当我方订单状态为【等待支付】或【创建完成】时
    // ================================================================
    PAY_WAIT_SUCCESS("等待支付-收到成功", "settlementNode", "结算处理") {
        @Override
        public boolean matches(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus) {
            return (OrderStatusEnumVO.PAY_WAIT.equals(orderStatus) || OrderStatusEnumVO.CREATE.equals(orderStatus))
                    && PaymentStatusEnumVO.SUCCESS.equals(paymentStatus);
        }
    },

    PAY_WAIT_PROCESSING("等待支付-处理中", "endNode", "结束处理") {
        @Override
        public boolean matches(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus) {
            return (OrderStatusEnumVO.PAY_WAIT.equals(orderStatus) || OrderStatusEnumVO.CREATE.equals(orderStatus))
                    && PaymentStatusEnumVO.PROCESSING.equals(paymentStatus);
        }
    },

    PAY_WAIT_UNPAID("等待支付-未支付", "endNode", "结束处理") {
        @Override
        public boolean matches(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus) {
            return (OrderStatusEnumVO.PAY_WAIT.equals(orderStatus) || OrderStatusEnumVO.CREATE.equals(orderStatus))
                    && PaymentStatusEnumVO.UNPAID.equals(paymentStatus);
        }
    },

    PAY_WAIT_FAILED("等待支付-支付失败", "failureNode", "失败处理") {
        @Override
        public boolean matches(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus) {
            return (OrderStatusEnumVO.PAY_WAIT.equals(orderStatus) || OrderStatusEnumVO.CREATE.equals(orderStatus))
                    && PaymentStatusEnumVO.FAILED.equals(paymentStatus);
        }
    },

    // ================================================================
    // 场景二：当我方订单状态为【已支付成功】或【已完成】时
    // ================================================================
    PAY_SUCCESS_RETRY("支付成功-重试发放", "settlementNode", "补偿结算") {
        @Override
        public boolean matches(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus) {
            return OrderStatusEnumVO.PAY_SUCCESS.equals(orderStatus) && PaymentStatusEnumVO.SUCCESS.equals(paymentStatus);
        }
    },
    PAY_SUCCESS_ANY("支付成功-任何状态", "endNode", "结束处理") {
        @Override
        public boolean matches(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus) {
            return OrderStatusEnumVO.PAY_SUCCESS.equals(orderStatus) || OrderStatusEnumVO.DEAL_DONE.equals(orderStatus);
        }
    },

    // ================================================================
    // 场景三：当我方订单状态为已关闭时
    // ================================================================
    CLOSE_SUCCESS("关闭订单-收到成功", "refundNode", "退款处理") {
        @Override
        public boolean matches(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus) {
            return OrderStatusEnumVO.CLOSE.equals(orderStatus) && PaymentStatusEnumVO.SUCCESS.equals(paymentStatus);
        }
    },

    CLOSE_OTHER("关闭订单-其他状态", "endNode", "结束处理") {
        @Override
        public boolean matches(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus) {
            return OrderStatusEnumVO.CLOSE.equals(orderStatus) && !PaymentStatusEnumVO.SUCCESS.equals(paymentStatus);
        }
    },

    // ================================================================
    // 场景四：当订单状态为【等待退款】时
    // ================================================================
    REFUND_WAIT_ANY("等待退款-任何状态", "endNode", "结束处理") {
        @Override
        public boolean matches(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus) {
            return OrderStatusEnumVO.REFUND_WAIT.equals(orderStatus);
        }
    },

    // ================================================================
    // 默认错误处理
    // ================================================================
    ERROR("错误处理", "errorNode", "错误处理") {
        @Override
        public boolean matches(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus) {
            return false;
        }
    };

    private final String desc;
    private final String strategyBean;
    private final String strategyDesc;

    /**
     * 根据订单状态和支付状态获取对应的处理策略
     *
     * @param orderStatus   订单状态
     * @param paymentStatus 支付状态
     * @return 处理策略，如果没有匹配的策略则返回 ERROR
     */
    public static PaymentHandlerStrategyEnumVO getStrategy(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus) {
        for (PaymentHandlerStrategyEnumVO strategy : values()) {
            if (strategy.matches(orderStatus, paymentStatus)) {
                return strategy;
            }
        }
        return ERROR;
    }

    /**
     * 抽象匹配方法，由各个策略实现具体的匹配逻辑
     *
     * @param orderStatus   订单状态
     * @param paymentStatus 支付状态
     * @return 是否匹配
     */
    public abstract boolean matches(OrderStatusEnumVO orderStatus, PaymentStatusEnumVO paymentStatus);


}
