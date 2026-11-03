package com.hzmwmt.domain.controller.dashboard;

import com.hzmwmt.domain.service.dashboard.IDashboardService;
import com.hzmwmt.domain.mapper.dashboard.DashboardStatisticsMapper;
import com.hzmwmt.domain.common.Result;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import java.util.HashMap;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasAuthority('admin.console') or hasRole('SUPER_ADMIN')")
@Tag(name = "DashboardController", description = "数据大屏统计接口")
public class DashboardController {

    @Autowired
    private IDashboardService dashboardService;

    @Autowired
    private DashboardStatisticsMapper statisticsMapper;

    @GetMapping("/login-summary")
    @PreAuthorize("hasAuthority('audit.read') or hasRole('SUPER_ADMIN')")
    public Result<Map<String, Object>> loginSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) return Result.error(400, "结束日期不能早于开始日期");
        var start = startDate == null ? null : startDate.atStartOfDay();
        var end = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        Map<String, Object> result = new HashMap<>(statisticsMapper.loginTotals(start, end));
        result.put("ranking", statisticsMapper.loginRanking(start, end));
        return Result.success(result);
    }

    @GetMapping("/audit-summary")
    @PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
    public Result<Map<String, Object>> auditSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) return Result.error(400, "结束日期不能早于开始日期");
        return Result.success(statisticsMapper.auditTotals(startDate == null ? null : startDate.atStartOfDay(),
                endDate == null ? null : endDate.plusDays(1).atStartOfDay()));
    }

    @GetMapping("/conversation-summary")
    @PreAuthorize("hasAuthority('user.read') or hasRole('SUPER_ADMIN')")
    public Result<Map<String, Object>> conversationSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) return Result.error(400, "结束日期不能早于开始日期");
        return Result.success(statisticsMapper.conversationTotals(startDate == null ? null : startDate.atStartOfDay(),
                endDate == null ? null : endDate.plusDays(1).atStartOfDay()));
    }

    @Operation(summary = "获取用户总览数据", description = "获取总用户数、今日新增用户、活跃用户等统计信息")
    @GetMapping("/user-overview")
    public Map<String, Object> getUserOverview() {
        return dashboardService.getUserOverview();
    }

    @Operation(summary = "获取实时订单监控数据", description = "获取最近5分钟订单数和最新订单列表")
    @GetMapping("/real-time-orders")
    public Map<String, Object> getRealTimeOrderMonitoring() {
        return dashboardService.getRealTimeOrderMonitoring();
    }

    @Operation(summary = "获取商品销售排行", description = "获取销量前10的商品列表")
    @GetMapping("/product-ranking")
    public List<Map<String, Object>> getProductSalesRanking() {
        return dashboardService.getProductSalesRanking();
    }

    @Operation(summary = "获取充值金额统计", description = "获取今日/本月充值总额和最近7天充值趋势")
    @GetMapping("/recharge-statistics")
    public Map<String, Object> getRechargeStatistics() {
        return dashboardService.getRechargeStatistics();
    }

    @Operation(summary = "获取区域代理数据", description = "获取各地区代理的业绩统计")
    @GetMapping("/region-agent-data")
    public List<Map<String, Object>> getRegionAgentData() {
        return dashboardService.getRegionAgentData();
    }
}
