package com.hzmwmt.domain.trade.model.entity;

import com.hzmwmt.domain.trade.model.valobj.OrderStatusEnumVO;
import com.hzmwmt.domain.trade.model.valobj.ProductTypeEnumVO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static com.hzmwmt.types.common.Constants.ORDER_VALIDITY_MINUTES;
import static com.hzmwmt.types.common.Constants.RedisConstants.KEY_ORDER_LOCK;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName OrderEntity
 * @Date: 2025/8/26 16:07
 */
@Builder
@Data
public class OrderEntity {
    /**
     * 订单业务ID (雪花ID)
     */
    private Long orderId;

    /**
     * 购买用户ID
     */
    private Long userId;

    /**
     * 外部支付ID
     */
    private String externalTransactionId;

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
    private OrderStatusEnumVO orderStatusEnumVO;

    /**
     * 商品名
     */
    private String productName;

    /**
     *
     */
    private ProductTypeEnumVO productTypeEnumVO;

    private Date payTime;
    /**
     * 有效时间
     */
    private Date validTime;

    /**
     * 创建时间
     */
    private Date createTime;

    public Long getTotalAmountInCents() {
        if (this.totalAmount == null) {
            return 0L;
        }
        // 精度处理：先将金额舍入到两位小数（符合“元”的定义），采用“四舍五入”
        BigDecimal amountInYuan = this.totalAmount.setScale(2, RoundingMode.HALF_UP);

        // 乘以 100，转换为“分”
        BigDecimal amountInCents = amountInYuan.multiply(new BigDecimal("100"));

        return amountInCents.longValue();
    }

    public Date generateValidTime(Date startTime) {
        Instant startInstant = startTime.toInstant();
        Instant validInstant = startInstant.plus(ORDER_VALIDITY_MINUTES, ChronoUnit.MINUTES);

        return Date.from(validInstant);
    }

    public boolean isExpired(Date payTime) {
        // 进行空值检查，保证健壮性
        if (payTime == null || this.validTime == null) {
            return false;
        }
        // 如果当前时间点 在 有效时间点 之后，则表示已过期
        return payTime.after(validTime);
    }

    public String generateLockKey() {
        return KEY_ORDER_LOCK + this.productId + "_" + this.userId;
    }


}
