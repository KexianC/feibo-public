package com.hzmwmt.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.infrastructure.dao.po.Order;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName OrderMapper
 * @Date: 2025/8/26 15:59
 */
public interface OrderMapper extends BaseMapper<Order> {


    int updateOrderStatusToWaitPay(Order order);

    int updateOrderStatusToPaySuccess(Order order);

    int updateOrderStatusToClosedByFailure(@Param("orderId") Long orderId);

    int updateOrderStatusToRefundWait(@Param("orderId") Long orderId);

    int updateOrderStatusToClosedAfterRefund(@Param("orderId") Long orderId);

    int updateOrderStatusToDealDone(@Param("orderId") Long orderId);

    Order selectUnPayOrderByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId,
                                              @Param("productType") int productType);

    @org.apache.ibatis.annotations.Select("SELECT * FROM t_order WHERE order_id = #{orderId} FOR UPDATE")
    Order lockOrderByOrderId(@Param("orderId") Long orderId);


    List<String> selectNoPayNotifyTrxIds();

    @org.apache.ibatis.annotations.Update("UPDATE t_order SET update_time=CURRENT_TIMESTAMP WHERE external_transaction_id=#{trxId} AND status IN (1,2)")
    int markPaymentQueryAttempt(@Param("trxId") String trxId);

    // Expiry releases only the checkout mutex; late verified payments can still settle the original order.
    @org.apache.ibatis.annotations.Update("UPDATE t_order SET creating_lock=NULL WHERE user_id=#{userId} AND product_id=#{productId} AND status IN (0,1) AND valid_time<=CURRENT_TIMESTAMP")
    int releaseExpiredCheckoutLocks(@Param("userId") Long userId, @Param("productId") Long productId);


    Order selectOrderByOrderId(@Param("orderId") Long orderId);


}
