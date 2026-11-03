package com.hzmwmt.domain.trade.service;

import com.hzmwmt.domain.trade.model.entity.CreateOrderEntity;
import com.hzmwmt.domain.trade.model.entity.PayOrderEntity;

import java.util.List;
import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ITradeLockOrderService
 * @Date: 2025/8/26 16:05
 */
public interface ITradeLockOrderService {
    PayOrderEntity lockTradeOrder(CreateOrderEntity createOrderEntity);

    List<String> queryNoPayNotifyTrxIds();

    Map<String, String> queryPaymentStatus(String trxId);
}
