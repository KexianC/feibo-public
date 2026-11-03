package com.hzmwmt.domain.controller.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hzmwmt.domain.common.Result;
import com.hzmwmt.domain.entity.order.TAdminOrder;
import com.hzmwmt.domain.service.order.AdminOrderService;
import com.hzmwmt.domain.vo.AdminOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单管理控制器
 * 提供后台订单管理相关接口
 */
@Slf4j
@Validated
@Tag(name = "后台订单管理", description = "订单相关接口（管理员端）")
@RestController
@PreAuthorize("hasAuthority('finance.manage') or hasRole('SUPER_ADMIN')")
@RequestMapping("/api/admin/order")
public class AdminOrderController {

    @Resource
    private AdminOrderService adminOrderService;

    @Operation(summary = "分页查询订单列表", description = "支持按订单号、状态、用户ID、用户名筛选")
    @GetMapping("/page")
    public Result<IPage<AdminOrderVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "订单业务ID") @RequestParam(required = false) @Positive Long orderId,
            @Parameter(description = "订单状态") @RequestParam(required = false) @Min(0) @Max(5) Integer status,
            @Parameter(description = "用户ID") @RequestParam(required = false) @Positive Long userId,
            @Parameter(description = "用户名") @RequestParam(required = false) @Size(max = 64) String username
    ) {
        IPage<AdminOrderVO> resultPage = adminOrderService.pageOrderVO(pageNum, pageSize, orderId, status, userId, username);
        return Result.success(resultPage);
    }

    @Operation(summary = "查询订单详情", description = "根据订单业务ID查询订单完整信息")
    @GetMapping("/detail")
    public Result<TAdminOrder> getByOrderId(
            @Parameter(description = "订单业务ID") @RequestParam @Positive Long orderId
    ) {
        TAdminOrder order = adminOrderService.getByOrderId(orderId);
        return Result.success(order);
    }

    @Operation(summary = "根据主键ID查询订单详情", description = "根据主键ID查询订单完整信息")
    @GetMapping("/{id}")
    public Result<TAdminOrder> getById(
            @Parameter(description = "主键ID") @PathVariable @Positive Long id
    ) {
        TAdminOrder order = adminOrderService.getById(id);
        return Result.success(order);
    }

    @Operation(summary = "更新订单状态", description = "管理员手动更新订单状态（如取消、确认）")
    @PutMapping("/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Boolean> updateStatus(
            @Parameter(description = "订单业务ID") @RequestParam @Positive Long orderId,
            @Parameter(description = "目标状态（3：交易完成/5：关单）") @RequestParam @Min(3) @Max(5) Integer status
    ) {
        boolean result = adminOrderService.updateOrderStatus(orderId, status);
        return Result.success(result);
    }

    @Operation(summary = "查询用户订单列表", description = "根据用户ID查询所有订单")
    @GetMapping("/user/{userId}")
    public Result<List<TAdminOrder>> getUserOrders(
            @Parameter(description = "用户ID") @PathVariable @Positive Long userId
    ) {
        List<TAdminOrder> orders = adminOrderService.getByUserId(userId);
        return Result.success(orders);
    }

    @Operation(summary = "查询过期订单", description = "查询所有已过期的订单")
    @GetMapping("/expired")
    public Result<List<TAdminOrder>> getExpiredOrders() {
        List<TAdminOrder> orders = adminOrderService.getExpiredOrders();
        return Result.success(orders);
    }
}
