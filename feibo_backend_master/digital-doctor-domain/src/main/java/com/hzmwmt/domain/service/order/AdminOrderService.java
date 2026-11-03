package com.hzmwmt.domain.service.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hzmwmt.domain.entity.order.TAdminOrder;
import com.hzmwmt.domain.vo.AdminOrderVO;

import java.util.List;

/**
 * 订单服务接口
 * 提供订单管理相关业务功能
 */
public interface AdminOrderService extends IService<TAdminOrder> {

    /**
     * 根据订单业务ID查询订单
     *
     * @param orderId 订单业务ID
     * @return 订单信息
     */
    TAdminOrder getByOrderId(Long orderId);

    /**
     * 根据用户ID查询订单列表
     *
     * @param userId 用户ID
     * @return 订单列表
     */
    List<TAdminOrder> getByUserId(Long userId);

    /**
     * 创建新订单
     *
     * @param order 订单信息
     * @return 是否创建成功
     */
    boolean createOrder(TAdminOrder order);

    /**
     * 更新订单状态
     *
     * @param orderId 订单ID
     * @param status  新状态
     * @return 是否更新成功
     */
    boolean updateOrderStatus(Long orderId, Integer status);

    /**
     * 查询过期订单
     *
     * @return 过期订单列表
     */
    List<TAdminOrder> getExpiredOrders();

    /**
     * 分页查询订单列表
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param orderId  订单业务ID（可选）
     * @param status   订单状态（可选）
     * @param userId   用户ID（可选）
     *                 /**
     *                 分页查询订单列表
     */
    IPage<TAdminOrder> pageOrder(Integer pageNum, Integer pageSize, Long orderId, Integer status, Long userId, String username);

    /**
     * 分页查询订单VO列表（含用户名）
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param orderId  订单业务ID（可选）
     * @param status   订单状态（可选）
     * @param userId   用户ID（可选）
     * @param username 用户名（可选，用于模糊查询）
     * @return 分页订单VO列表
     */
    IPage<AdminOrderVO> pageOrderVO(Integer pageNum, Integer pageSize, Long orderId, Integer status, Long userId, String username);
}
