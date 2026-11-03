package com.hzmwmt.domain.trade.model.entity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName PaymentTransactionEntity
 * @Date: 2025/9/1 17:05
 */
public class PaymentTransactionEntity {
    /**
     * 支付流水业务ID (雪花ID)
     */
    private Long paymentTransactionId;

    /**
     * 关联的订单业务ID
     */
    private Long orderId;

    /**
     * 支付用户ID (冗余字段用于优化查询)
     */
    private Long userId;

    /**
     * 流水类型: 1=支付, 2=退款
     */
    private Integer transactionType;

    /**
     * 流水状态: 1=处理中, 2=成功, 3=失败
     */
    private Integer transactionStatus;

    /**
     * 交易金额 (元), 退款为负数
     */
    private BigDecimal amount;

    /**
     * 支付方式, 如 WECHAT_PAY, ALI_PAY
     */
    private String paymentMethod;

    /**
     * 外部交易流水号 (支付平台返回, 如trxid)
     */
    private String externalTransactionId;

    /**
     * 渠道响应码
     */
    private String channelResponseCode;

    /**
     * 渠道响应信息
     */
    private String channelResponseMsg;

    /**
     * 创建时间
     */
    private Date createTime;


}
