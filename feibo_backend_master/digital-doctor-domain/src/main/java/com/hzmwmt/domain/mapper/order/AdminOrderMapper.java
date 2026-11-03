package com.hzmwmt.domain.mapper.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.order.TAdminOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 订单Mapper接口
 * 提供订单数据访问功能
 */
@Mapper
public interface AdminOrderMapper extends BaseMapper<TAdminOrder> {

    /**
     * 根据订单业务ID查询订单
     *
     * @param orderId 订单业务ID
     * @return 订单信息
     */
    TAdminOrder selectByOrderId(Long orderId);

    /**
     * 根据用户ID查询订单列表
     *
     * @param userId 用户ID
     * @return 订单列表
     */
    List<TAdminOrder> selectByUserId(Long userId);

    /**
     * 查询过期订单
     *
     * @return 过期订单列表
     */
    List<TAdminOrder> selectExpiredOrders();
}
