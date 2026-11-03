package com.hzmwmt.domain.trade.model.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName PayOrderEntity
 * @Date: 2025/9/1 11:55
 */
@Builder
@Data
public class PayOrderEntity {
    /**
     * 支付Id
     */
    private String trxId;
    /**
     * 支付url
     */
    private String url;
    /**
     * 订单Id
     */
    private Long orderId;
}
