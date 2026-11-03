package com.hzmwmt.domain.trade.adapter.port;

import com.hzmwmt.domain.trade.model.entity.OrderEntity;
import com.hzmwmt.domain.trade.model.entity.PayOrderEntity;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ITradePort
 * @Date: 2025/8/28 14:09
 */
public interface ITradePort {
    PayOrderEntity requestPayment(OrderEntity entity);

    void authenticateNotification(Map<String, String> payload);

    Map<String, String> queryPaymentStatus(String trxId);
}
