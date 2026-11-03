package com.hzmwmt.domain.trade.service.settlement.trial;

import com.google.common.eventbus.EventBus;
import com.hzmwmt.domain.trade.adapter.port.ITradePort;
import com.hzmwmt.domain.trade.adapter.repository.ITradeRepository;
import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.service.settlement.trial.factory.PaymentNotifyStrategyFactory;
import com.hzmwmt.types.design.framework.tree.AbstractStrategyRouter;
import jakarta.annotation.Resource;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 节点基类，封装节点通用逻辑
 * @ClassName AbstractPaymentNotifySupport
 * @Date: 2025/9/2 12:30
 */
public abstract class AbstractPaymentNotifySupport extends AbstractStrategyRouter<Map<String, String>, PaymentNotifyStrategyFactory.DynamicContext, PaymentSettlementEntity> {
    @Resource
    protected ITradeRepository tradeRepository;
    @Resource
    protected ITradePort tradePort;
    @Resource
    protected EventBus eventBus;
}
