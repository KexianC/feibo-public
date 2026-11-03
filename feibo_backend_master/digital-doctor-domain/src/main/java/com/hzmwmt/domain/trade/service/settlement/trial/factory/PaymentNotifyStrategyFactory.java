package com.hzmwmt.domain.trade.service.settlement.trial.factory;

import com.hzmwmt.domain.trade.model.entity.OrderEntity;
import com.hzmwmt.domain.trade.model.entity.PaySettlementRuleCommandEntity;
import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.service.settlement.trial.node.RootNode;
import com.hzmwmt.types.design.framework.tree.StrategyHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName PaymentNotifyStrategyFactory
 * @Date: 2025/9/2 11:50
 */


@Configuration
public class PaymentNotifyStrategyFactory {

    private final RootNode rootNode;


    // 使用构造函数注入，保证 rootNode 在工厂创建时就已准备就绪
    public PaymentNotifyStrategyFactory(RootNode rootNode) {
        this.rootNode = rootNode;

    }

    public StrategyHandler<Map<String, String>, DynamicContext, PaymentSettlementEntity> strategyHandler() {
        return rootNode;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {
        private PaySettlementRuleCommandEntity command;
        private OrderEntity orderEntity;
    }
}
