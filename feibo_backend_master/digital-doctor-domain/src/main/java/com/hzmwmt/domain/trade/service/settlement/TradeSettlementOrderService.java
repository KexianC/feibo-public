package com.hzmwmt.domain.trade.service.settlement;

import com.hzmwmt.domain.trade.adapter.port.ITradePort;
import com.hzmwmt.domain.trade.adapter.repository.ITradeRepository;
import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.model.entity.TradePaySuccessEntity;
import com.hzmwmt.domain.trade.service.ITradeSettlementOrderService;
import com.hzmwmt.domain.trade.service.settlement.trial.factory.PaymentNotifyStrategyFactory;
import com.hzmwmt.types.design.framework.tree.StrategyHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName TradeSettlementOrderService
 * @Date: 2025/8/29 10:55
 */
@Service
public class TradeSettlementOrderService implements ITradeSettlementOrderService {
    @Resource
    private ITradeRepository tradeRepository;
    @Resource
    private PaymentNotifyStrategyFactory paymentNotifyStrategyFactory;
    @Resource
    private ITradePort tradePort;

    @Override
    public void settlementTradePayOrder(TradePaySuccessEntity tradePaySuccessEntity) {
        tradeRepository.settlementTradePayOrder(tradePaySuccessEntity);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public PaymentSettlementEntity settlementPaymentOrder(Map<String, String> allParam) throws Exception {
        StrategyHandler<Map<String, String>, PaymentNotifyStrategyFactory.DynamicContext, PaymentSettlementEntity> mapDynamicContextPaymentSettlementEntityStrategyHandler = paymentNotifyStrategyFactory.strategyHandler();
        return mapDynamicContextPaymentSettlementEntityStrategyHandler.apply(allParam, new PaymentNotifyStrategyFactory.DynamicContext());
    }
}
