package com.hzmwmt.monitoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hzmwmt.monitoring.SystemMonitorSnapshot.*;

/** A fixed metric allowlist; exception messages, addresses and raw INFO/SQL never enter the DTO. */
final class MonitorReadings {
    private final String id;
    private final String name;
    private final Map<String, Metric> metrics = new LinkedHashMap<>();
    private String status = "UP";
    private String message;

    MonitorReadings(String id) {
        this.id = id;
        name = switch (id) {
            case "server" -> "应用所在服务器";
            case "jvm" -> "应用 JVM";
            case "database" -> "MySQL 数据库实例";
            case "connectionPool" -> "应用数据库连接池";
            case "redis" -> "Redis 实例";
            case "storage" -> "应用所在磁盘卷";
            default -> throw new IllegalArgumentException("Unknown monitor component");
        };
        switch (id) {
            case "server" -> {
                add("cpuPercent", "系统 CPU 使用率", "percent");
                add("logicalProcessors", "可用逻辑处理器", "count");
                add("systemLoad1m", "系统 1 分钟平均负载", "");
                add("physicalTotal", "物理内存总量", "bytes");
                add("physicalUsed", "物理内存已用量", "bytes");
                add("physicalUsedPercent", "物理内存使用率", "percent");
            }
            case "jvm" -> {
                add("heapUsed", "堆内存已用量", "bytes");
                add("heapMax", "堆内存上限", "bytes");
                add("heapUsedPercent", "堆内存使用率", "percent");
                add("nonHeapUsed", "非堆内存已用量", "bytes");
                add("threadCount", "当前存活线程", "count");
                add("gcCount", "累计 GC 次数", "count");
                add("gcTime", "累计 GC 耗时", "ms");
                add("uptime", "应用运行时间", "seconds");
            }
            case "database" -> {
                add("latency", "连接与探测耗时", "ms");
                add("threadsRunning", "实例当前执行线程", "count");
                add("threadsConnected", "实例当前连接", "count");
                add("maxConnections", "实例连接上限", "count");
                add("connectionUsagePercent", "实例连接占用率", "percent");
                add("queriesPerSecond", "实例每秒客户端语句", "perSecond");
                add("schemaBytes", "当前业务库数据与索引空间", "bytes");
            }
            case "connectionPool" -> {
                add("active", "使用中的连接", "count");
                add("idle", "空闲连接", "count");
                add("total", "已建立连接", "count");
                add("maximum", "连接池容量上限", "count");
                add("pending", "等待连接的线程", "count");
            }
            case "redis" -> {
                add("latency", "PING 耗时", "ms");
                add("usedMemory", "Redis 已用内存", "bytes");
                add("maxMemory", "Redis 内存限制", "bytes");
                add("connectedClients", "已连接客户端", "count");
                add("blockedClients", "阻塞客户端", "count");
                add("operationsPerSecond", "实例每秒操作", "perSecond");
            }
            case "storage" -> {
                add("diskTotal", "所在卷总空间", "bytes");
                add("diskUsable", "应用可用空间", "bytes");
                add("diskUsedPercent", "所在卷不可用空间占比", "percent");
            }
        }
    }

    private void add(String key, String label, String unit) {
        metrics.put(key, new Metric(key, label, null, unit, "当前环境未提供此指标"));
    }

    MonitorReadings put(String key, Object value) { return put(key, value, null); }

    MonitorReadings put(String key, Object value, String note) {
        Metric spec = metrics.get(key);
        if (spec == null) throw new IllegalArgumentException("Unknown monitor metric");
        if (value instanceof Number number && (!Double.isFinite(number.doubleValue()) || number.doubleValue() < 0)) {
            value = null;
        }
        metrics.put(key, new Metric(key, spec.label(), value, spec.unit(), value == null && note == null ? "当前环境未提供此指标" : note));
        return this;
    }

    MonitorReadings status(String status, String message) {
        this.status = status;
        this.message = message;
        return this;
    }

    MonitorReadings unavailable(String status, String reason) {
        this.status = status;
        this.message = reason;
        for (String key : new ArrayList<>(metrics.keySet())) put(key, null, reason);
        return this;
    }

    Component build() {
        if ("UP".equals(status) && metrics.values().stream().anyMatch(m -> m.value() == null)) {
            status = "DEGRADED";
            if (message == null) message = "部分指标暂不可用，详见指标说明";
        }
        return new Component(id, name, status, message, List.copyOf(metrics.values()));
    }

    static Double percent(Number used, Number total) {
        if (used == null || total == null || total.doubleValue() <= 0 || used.doubleValue() < 0) return null;
        return Math.round(used.doubleValue() / total.doubleValue() * 10000.0) / 100.0;
    }

    static Long nonNegativeLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0 ? parsed : null;
        } catch (RuntimeException ignored) { return null; }
    }
}
