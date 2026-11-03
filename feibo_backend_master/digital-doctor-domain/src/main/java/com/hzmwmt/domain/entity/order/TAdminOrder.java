package com.hzmwmt.domain.entity.order;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
@Schema(name = "TAdminOrder", description = "订单实体类")
public class TAdminOrder {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "订单业务ID (雪花ID)")
    @TableField("order_id")
    private Long orderId;

    @Schema(description = "用户ID")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "外部交易ID")
    @TableField("external_transaction_id")
    private String externalTransactionId;

    @Schema(description = "支付链接")
    private String url;

    @Schema(description = "产品类型: 0=会员套餐, 1=充电包")
    @TableField("product_type")
    private Byte productType;

    @Schema(description = "产品ID")
    @TableField("product_id")
    private Long productId;

    @Schema(description = "总金额")
    @TableField("total_amount")
    private BigDecimal totalAmount;

    @Schema(description = "订单状态")
    private Integer status;

    @Schema(description = "产品名称")
    @TableField("product_name")
    private String productName;

    @Schema(description = "创建时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "支付时间")
    @TableField("pay_time")
    private LocalDateTime payTime;

    @Schema(description = "创建锁")
    @TableField("creating_lock")
    private Byte creatingLock;

    @Schema(description = "有效时间")
    @TableField("valid_time")
    private LocalDateTime validTime;

    @Schema(description = "更新时间")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}