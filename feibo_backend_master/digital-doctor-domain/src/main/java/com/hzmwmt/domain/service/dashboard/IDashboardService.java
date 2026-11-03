package com.hzmwmt.domain.service.dashboard;

import java.util.List;
import java.util.Map;

public interface IDashboardService {

    /**
     * 获取用户总览数据
     */
    Map<String, Object> getUserOverview();

    /**
     * 获取实时订单监控数据
     */
    Map<String, Object> getRealTimeOrderMonitoring();

    /**
     * 获取商品销售排行
     */
    List<Map<String, Object>> getProductSalesRanking();

    /**
     * 获取充值金额统计
     */
    Map<String, Object> getRechargeStatistics();

    /**
     * 获取区域代理数据
     */
    List<Map<String, Object>> getRegionAgentData();
}