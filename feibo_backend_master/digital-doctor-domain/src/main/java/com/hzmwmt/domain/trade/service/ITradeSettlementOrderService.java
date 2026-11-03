package com.hzmwmt.domain.trade.service;

import com.hzmwmt.domain.trade.model.entity.PaymentSettlementEntity;
import com.hzmwmt.domain.trade.model.entity.TradePaySuccessEntity;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ITradeSettlementOrderService
 * @Date: 2025/8/29 9:20
 */
public interface ITradeSettlementOrderService {
    void settlementTradePayOrder(TradePaySuccessEntity tradePaySuccessEntity);

    PaymentSettlementEntity settlementPaymentOrder(Map<String, String> allParam) throws Exception;
}
