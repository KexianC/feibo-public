package com.hzmwmt.domain.trade.service.settlement.trial.node;

import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.model.valobj.OrderStatusEnumVO;
import com.hzmwmt.domain.trade.model.valobj.PaymentHandlerStrategyEnumVO;
import com.hzmwmt.domain.trade.model.valobj.PaymentStatusEnumVO;
import com.hzmwmt.domain.trade.service.settlement.trial.AbstractPaymentNotifySupport;
import com.hzmwmt.domain.trade.service.settlement.trial.factory.PaymentNotifyStrategyFactory.DynamicContext;
import com.hzmwmt.types.design.framework.tree.StrategyHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 决策方法节点
 * @ClassName SwitchNode
 * @Date: 2025/9/2 14:08
 */
@Slf4j
@Service
public class SwitchNode extends AbstractPaymentNotifySupport implements ApplicationContextAware {

    private static ApplicationContext applicationContext;
    // 策略节点映射缓存
    private final Map<String, AbstractPaymentNotifySupport> strategyNodeMap = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        // 在 ApplicationContext 设置后立即初始化节点映射
        initHandlerNodeMap();
    }


    /**
     * 初始化执行节点节点映射
     */
    private void initHandlerNodeMap() {
        strategyNodeMap.put("settlementNode", applicationContext.getBean(SettlementNode.class));
        strategyNodeMap.put("endNode", applicationContext.getBean(EndNode.class));
        strategyNodeMap.put("failureNode", applicationContext.getBean(FailureNode.class));
        strategyNodeMap.put("refundNode", applicationContext.getBean(RefundNode.class));
        strategyNodeMap.put("errorNode", applicationContext.getBean(ErrorNode.class));
    }

    @Override
    public PaymentSettlementEntity apply(Map<String, String> requestParameter, DynamicContext dynamicContext) throws Exception {
        // 决策节点本身不执行业务，直接路由
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<Map<String, String>, DynamicContext, PaymentSettlementEntity> get(Map<String, String> payload, DynamicContext context) {
        OrderStatusEnumVO orderStatus = context.getOrderEntity().getOrderStatusEnumVO();
        String trxStatus = context.getCommand().getTrxStatus();

        // 将外部状态码转换为内部支付状态枚举
        PaymentStatusEnumVO paymentStatus = PaymentStatusEnumVO.getByCode(trxStatus);

        log.info("支付通知规则树-状态分发节点 | orderId:{}, 订单状态:{}, 支付状态:{}",
                context.getOrderEntity().getOrderId(), orderStatus, paymentStatus);

        // 通过策略枚举获取匹配的处理策略
        PaymentHandlerStrategyEnumVO strategy = PaymentHandlerStrategyEnumVO.getStrategy(orderStatus, paymentStatus);

        log.info("支付通知规则树-状态分发节点 | orderId:{}, 匹配策略:{}, 目标节点:{}",
                context.getOrderEntity().getOrderId(), strategy.getDesc(), strategy.getStrategyBean());

        // 通过工厂获取对应的处理节点
        return strategyNodeMap.getOrDefault(strategy.getStrategyBean(), strategyNodeMap.get("errorNode"));
    }

}
