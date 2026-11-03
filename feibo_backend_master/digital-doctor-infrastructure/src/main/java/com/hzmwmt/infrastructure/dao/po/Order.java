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
 * @ClassName Order
 * @Date: 2025/8/26 14:58
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order")
public class Order {

    /**
     * 主键ID, 自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单业务ID (雪花ID)
     */
    private Long orderId;

    /**
     * 外部支付ID
     */
    private String externalTransactionId;

    /**
     * 商品类型: 0=会员套餐, 1=充电包
     */
    private Byte productType;

    /**
     * 购买用户ID
     */
    private Long userId;

    /**
     * 商品业务ID
     */
    private Long productId;

    /**
     * 支付链接
     */
    private String url;

    /**
     * 订单总金额 (元)
     */
    private BigDecimal totalAmount;

    /**
     * 订单状态
     */
    private Integer status;

    /**
     * 商品名
     */
    private String productName;

    /** Immutable entitlement terms captured when this order was created. */
    private String entitlementSnapshot;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 支付成功时间
     */
    private Date payTime;

    /**
     * 有效时间
     */
    private Date validTime;

    /**
     * 唯一索引锁
     */
    private Byte creatingLock;
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
