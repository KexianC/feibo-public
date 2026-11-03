package com.hzmwmt.monitoring;

import org.redisson.api.Node;
import org.redisson.api.RedissonClient;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hzmwmt.monitoring.SystemMonitorSnapshot.Component;

final class RedisMonitorProbe {
    private final RedissonClient client;

    RedisMonitorProbe(RedissonClient client) { this.client = client; }

    Component sample() {
        MonitorReadings readings = new MonitorReadings("redis");
        if (client == null) return readings.unavailable("UNKNOWN", "应用未配置 Redis 客户端").build();
        boolean connected = false;
        try {
            Collection<Node> nodes = client.getNodesGroup().getNodes();
            if (nodes.size() != 1) return readings.unavailable("UNKNOWN", "当前监控仅支持应用已配置的单节点 Redis").build();
            Node node = nodes.iterator().next();
            long started = System.nanoTime();
            if (!node.ping(1, TimeUnit.SECONDS)) return readings.unavailable("DOWN", "Redis PING 探测失败").build();
            connected = true;
            readings.put("latency", (System.nanoTime() - started) / 1_000_000.0);
            Map<String, String> info = node.info(Node.InfoSection.DEFAULT);
            putInfo(readings, info, "usedMemory", "used_memory");
            putInfo(readings, info, "maxMemory", "maxmemory");
            putInfo(readings, info, "connectedClients", "connected_clients");
            putInfo(readings, info, "blockedClients", "blocked_clients");
            putInfo(readings, info, "operationsPerSecond", "instantaneous_ops_per_sec");
            Long maximum = MonitorReadings.nonNegativeLong(info.get("maxmemory"));
            Long used = MonitorReadings.nonNegativeLong(info.get("used_memory"));
            Long blocked = MonitorReadings.nonNegativeLong(info.get("blocked_clients"));
            if (maximum != null && maximum == 0) readings.put("maxMemory", 0L, "0 表示 Redis 未配置内存上限");
            if (maximum != null && maximum > 0 && used != null && used >= maximum * 0.9) {
                readings.status("DEGRADED", "Redis 内存使用达到配置上限的 90%");
            } else if (blocked != null && blocked > 0) {
                readings.status("DEGRADED", "存在阻塞客户端；也可能是业务正常使用阻塞命令，请结合业务确认");
            }
        } catch (RuntimeException ignored) {
            if (!connected) return readings.unavailable("DOWN", "Redis 连接或 PING 探测失败").build();
            readings.status("DEGRADED", "Redis 连通；INFO 统计不可用或当前账号无读取权限");
        }
        return readings.build();
    }

    private void putInfo(MonitorReadings readings, Map<String, String> info, String metric, String field) {
        Long value = MonitorReadings.nonNegativeLong(info.get(field));
        readings.put(metric, value, value == null ? "Redis 未提供此指标或当前账号无统计读取权限" : "Redis 实例统计");
    }
}
