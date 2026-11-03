package com.hzmwmt.domain.mapper.dashboard;

import com.hzmwmt.domain.entity.order.TAdminOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    /**
     * 获取用户总览数据
     */
    @Select("SELECT " +
            "COUNT(*) AS totalUsers," +
            "SUM(CASE WHEN DATE(create_time) = CURRENT_DATE THEN 1 ELSE 0 END) AS todayNewUsers," +
            "COUNT(*) AS activeUsers " +
            "FROM t_user WHERE status = 0")
    Map<String, Long> getUserOverview();

    /**
     * 获取最近5分钟的订单数量
     */
    @Select("SELECT COUNT(*) FROM t_admin_order WHERE create_time >= DATE_SUB(NOW(), INTERVAL 5 MINUTE)")
    Long getRecent5MinutesOrderCount();

    /**
     * 获取最新订单列表
     */
    @Select("SELECT order_no, package_id AS productId, amount, create_time AS orderTime " +
            "FROM t_admin_order " +
            "ORDER BY create_time DESC " +
            "LIMIT 10")
    List<TAdminOrder> getLatestOrders();

    /**
     * 获取商品销售排行
     */
    @Select("SELECT " +
            "p.package_id AS productId," +
            "p.package_name AS productName," +
            "COUNT(o.id) AS salesVolume," +
            "SUM(o.amount) AS salesAmount " +
            "FROM t_admin_order o " +
            "JOIN t_package p ON o.package_id = p.package_id " +
            "WHERE o.status = 1 " +
            "GROUP BY p.package_id, p.package_name " +
            "ORDER BY salesVolume DESC " +
            "LIMIT 10")
    List<Map<String, Object>> getProductSalesRanking();

    /**
     * 获取今日充值总额
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_admin_order WHERE DATE(create_time) = CURRENT_DATE AND status = 1")
    BigDecimal getTodayRechargeTotal();

    /**
     * 获取本月充值总额
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_admin_order WHERE DATE_FORMAT(create_time, '%Y-%m') = DATE_FORMAT(CURRENT_DATE, '%Y-%m') AND status = 1")
    BigDecimal getMonthRechargeTotal();

    /**
     * 获取最近7天充值趋势
     */
    @Select("SELECT " +
            "DATE(create_time) AS day," +
            "COALESCE(SUM(amount), 0) AS rechargeAmount " +
            "FROM t_admin_order " +
            "WHERE create_time >= DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY) " +
            "AND status = 1 " +
            "GROUP BY DATE(create_time) " +
            "ORDER BY day")
    List<Map<String, Object>> getRecent7DaysRechargeTrend();

    /**
     * 获取区域代理数据（这里假设用户表中有区域信息）
     */
    @Select("SELECT " +
            "'华东区' AS regionName," +
            "COUNT(*) AS agentCount," +
            "COALESCE(SUM(o.amount), 0) AS totalPerformance " +
            "FROM t_admin_order o " +
            "WHERE o.status = 1 " +
            "GROUP BY '华东区' " +
            "UNION ALL " +
            "SELECT " +
            "'华北区' AS regionName," +
            "COUNT(*) AS agentCount," +
            "COALESCE(SUM(o.amount), 0) AS totalPerformance " +
            "FROM t_admin_order o " +
            "WHERE o.status = 1 " +
            "GROUP BY '华北区'")
    List<Map<String, Object>> getRegionAgentData();
}