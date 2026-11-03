package com.hzmwmt.domain.service.order.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hzmwmt.domain.entity.order.TAdminOrder;
import com.hzmwmt.domain.entity.user.TUser;
import com.hzmwmt.domain.mapper.order.AdminOrderMapper;
import com.hzmwmt.domain.service.order.AdminOrderService;
import com.hzmwmt.domain.service.user.AdminUserService;
import com.hzmwmt.domain.vo.AdminOrderVO;
import com.hzmwmt.domain.trade.model.valobj.OrderStatusEnumVO;
import com.hzmwmt.types.annotations.OperationLog;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 订单服务实现类
 * 实现订单管理相关业务功能
 */
@Service
public class AdminOrderServiceImpl extends ServiceImpl<AdminOrderMapper, TAdminOrder> implements AdminOrderService {

    @Resource
    private AdminUserService adminUserService;

    @Override
    public TAdminOrder getByOrderId(Long orderId) {
        return baseMapper.selectByOrderId(orderId);
    }

    @Override
    public List<TAdminOrder> getByUserId(Long userId) {
        return baseMapper.selectByUserId(userId);
    }

    @Override
    public boolean createOrder(TAdminOrder order) {
        // 设置创建时间和更新时间
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        // 设置初始状态为待支付
        if (order.getStatus() == null) {
            order.setStatus(0);
        }
        // 设置创建锁为0 (false)
        order.setCreatingLock((byte) 0);
        return save(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(operationType = "修改", description = "后台受控更新订单状态", recordResult = false, critical = true)
    public boolean updateOrderStatus(Long orderId, Integer status) {
        if (orderId == null || status == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }

        TAdminOrder current = baseMapper.selectByOrderId(orderId);
        if (current == null || current.getStatus() == null) {
            throw new AppException(ResponseCode.E0005.getCode(), "订单不存在");
        }

        OrderStatusEnumVO currentStatus = OrderStatusEnumVO.getByCode(current.getStatus());
        OrderStatusEnumVO targetStatus = OrderStatusEnumVO.getByCode(status);
        if (currentStatus == targetStatus) {
            return true;
        }

        boolean allowed = (targetStatus == OrderStatusEnumVO.CLOSE
                && (currentStatus == OrderStatusEnumVO.CREATE
                    || currentStatus == OrderStatusEnumVO.PAY_WAIT
                    || currentStatus == OrderStatusEnumVO.REFUND_WAIT))
                || (targetStatus == OrderStatusEnumVO.DEAL_DONE
                    && currentStatus == OrderStatusEnumVO.PAY_SUCCESS);
        if (!allowed) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "不允许的订单状态迁移");
        }

        TAdminOrder update = new TAdminOrder();
        update.setStatus(targetStatus.getCode());
        update.setUpdateTime(LocalDateTime.now());
        boolean updated = update(update, new LambdaUpdateWrapper<TAdminOrder>()
                .eq(TAdminOrder::getOrderId, orderId)
                .eq(TAdminOrder::getStatus, currentStatus.getCode()));
        if (!updated) {
            throw new AppException(ResponseCode.REPEATED_OPERATION_EXCEPTION);
        }
        return true;
    }

    @Override
    public List<TAdminOrder> getExpiredOrders() {
        return baseMapper.selectExpiredOrders();
    }

    @Override
    public IPage<TAdminOrder> pageOrder(Integer pageNum, Integer pageSize, Long orderId, Integer status, Long userId, String username) {
        Page<TAdminOrder> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TAdminOrder> wrapper = new QueryWrapper<>();

        // 添加查询条件
        if (orderId != null) {
            wrapper.eq("order_id", orderId);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }

        // 根据用户名查询
        if (username != null && !username.isEmpty()) {
            // 先查询匹配的用户ID列表
            List<Long> userIds = adminUserService.getUserIdsByUsernameLike(username);
            if (!userIds.isEmpty()) {
                wrapper.in("user_id", userIds);
            } else {
                // 如果没有匹配的用户，返回空结果
                return new Page<>(pageNum, pageSize);
            }
        }

        // 按创建时间倒序排列
        wrapper.orderByDesc("create_time");

        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public List<TAdminOrder> list() {
        return super.list();
    }

    @Override
    public TAdminOrder getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    public boolean save(TAdminOrder entity) {
        return super.save(entity);
    }

    @Override
    public boolean updateById(TAdminOrder entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public IPage<AdminOrderVO> pageOrderVO(Integer pageNum, Integer pageSize, Long orderId, Integer status, Long userId, String username) {
        // 先查询符合条件的订单列表
        IPage<TAdminOrder> orderPage = pageOrder(pageNum, pageSize, orderId, status, userId, username);

        // 创建VO分页对象
        IPage<AdminOrderVO> voPage = new Page<>(pageNum, pageSize);
        voPage.setTotal(orderPage.getTotal());

        // 转换为VO列表
        List<AdminOrderVO> voList = new ArrayList<>();
        for (TAdminOrder order : orderPage.getRecords()) {
            AdminOrderVO vo = new AdminOrderVO();
            BeanUtils.copyProperties(order, vo);

            // 设置用户名
            if (order.getUserId() != null) {
                Optional<TUser> userOptional = adminUserService.getUserByBusinessUserId(order.getUserId());
                userOptional.ifPresent(user -> vo.setUsername(user.getNickName()));
            }

            voList.add(vo);
        }

        // 如果提供了用户名参数，进行过滤
        if (username != null && !username.isEmpty()) {
            voList = voList.stream()
                    .filter(vo -> vo.getUsername() != null && vo.getUsername().contains(username))
                    .collect(java.util.stream.Collectors.toList());
        }

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }
}
