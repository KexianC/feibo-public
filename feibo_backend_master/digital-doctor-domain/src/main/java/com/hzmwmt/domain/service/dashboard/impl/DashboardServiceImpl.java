package com.hzmwmt.domain.service.dashboard.impl;

import com.hzmwmt.domain.mapper.dashboard.DashboardMapper;
import com.hzmwmt.domain.service.dashboard.IDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardServiceImpl implements IDashboardService {

    @Autowired
    private DashboardMapper dashboardMapper;

    @Override
    public Map<String, Object> getUserOverview() {
        Map<String, Long> userData = dashboardMapper.getUserOverview();
        Map<String, Object> result = new HashMap<>(userData);
        return result;
    }

    @Override
    public Map<String, Object> getRealTimeOrderMonitoring() {
        Map<String, Object> result = new HashMap<>();
        result.put("recent5MinutesOrderCount", dashboardMapper.getRecent5MinutesOrderCount());
        result.put("latestOrders", dashboardMapper.getLatestOrders());
        return result;
    }

    @Override
    public List<Map<String, Object>> getProductSalesRanking() {
        return dashboardMapper.getProductSalesRanking();
    }

    @Override
    public Map<String, Object> getRechargeStatistics() {
        Map<String, Object> result = new HashMap<>();
        result.put("todayRechargeTotal", dashboardMapper.getTodayRechargeTotal());
        result.put("monthRechargeTotal", dashboardMapper.getMonthRechargeTotal());
        result.put("recent7DaysRechargeTrend", dashboardMapper.getRecent7DaysRechargeTrend());
        return result;
    }

    @Override
    public List<Map<String, Object>> getRegionAgentData() {
        return dashboardMapper.getRegionAgentData();
    }
}