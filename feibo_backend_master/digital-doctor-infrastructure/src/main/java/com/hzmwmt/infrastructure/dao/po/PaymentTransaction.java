package com.hzmwmt.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName PaymentTransaction
 * @Date: 2025/8/28 9:32
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_payment_transaction")
public class PaymentTransaction {

    /**
     * 主键ID, 自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

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
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
