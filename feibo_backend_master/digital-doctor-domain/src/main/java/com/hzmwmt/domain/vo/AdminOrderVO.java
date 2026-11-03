package com.hzmwmt.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理员端订单列表展示的视图对象，包含订单信息和用户名称
 */
@Data
@Schema(description = "管理员端订单列表展示的视图对象")
public class AdminOrderVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "订单业务ID (雪花ID)")
    private Long orderId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "外部交易ID")
    private String externalTransactionId;

    @Schema(description = "支付链接")
    private String url;

    @Schema(description = "产品类型: 0=会员套餐, 1=充电包")
    private Byte productType;

    @Schema(description = "产品ID")
    private Long productId;

    @Schema(description = "总金额")
    private BigDecimal totalAmount;

    @Schema(description = "订单状态")
    private Integer status;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "创建锁")
    private Byte creatingLock;

    @Schema(description = "有效时间")
    private LocalDateTime validTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}