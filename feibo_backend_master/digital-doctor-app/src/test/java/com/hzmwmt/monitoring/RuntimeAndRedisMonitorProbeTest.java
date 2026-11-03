package com.hzmwmt.monitoring;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.Test;
import org.redisson.api.Node;
import org.redisson.api.NodesGroup;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hzmwmt.monitoring.DatabaseMonitorProbeTest.metric;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeAndRedisMonitorProbeTest {
    @Test
    void cpuFirstSampleAndUnsupportedValuesAreNullButRealIdleZeroIsKept() {
        com.sun.management.OperatingSystemMXBean os = mock(com.sun.management.OperatingSystemMXBean.class);
        when(os.getAvailableProcessors()).thenReturn(4);
        when(os.getSystemLoadAverage()).thenReturn(-1.0);
        when(os.getCpuLoad()).thenReturn(0.0, 0.0, -1.0);
        when(os.getTotalMemorySize()).thenReturn(1000L);
        when(os.getFreeMemorySize()).thenReturn(1000L);
        RuntimeMonitorProbe probe = new RuntimeMonitorProbe(os);
        var first = probe.server();
        assertThat(metric(first, "cpuPercent")).isNull();
        assertThat(metric(first, "physicalUsedPercent")).isEqualTo(0.0);
        assertThat(metric(first, "systemLoad1m")).isNull();
        assertThat(metric(probe.server(), "cpuPercent")).isEqualTo(0.0);
        assertThat(metric(probe.server(), "cpuPercent")).isNull();
    }

    @Test
    void actualLocalJvmAndDiskHaveRealPositiveValuesWithoutPathsOrProcessDetails() {
        RuntimeMonitorProbe probe = new RuntimeMonitorProbe();
        var jvm = probe.jvm();
        assertThat((Number) metric(jvm, "heapUsed")).isNotNull();
        assertThat(((Number) metric(jvm, "heapUsed")).longValue()).isPositive();
        assertThat(((Number) metric(jvm, "threadCount")).longValue()).isPositive();
        var disk = probe.storage();
        assertThat(((Number) metric(disk, "diskTotal")).longValue()).isPositive();
        assertThat(disk.toString()).doesNotContain(System.getProperty("user.dir"));
        assertThat(jvm.toString()).doesNotContain(System.getProperty("java.home"));
    }

    @Test
    void firstCpuObservationDoesNotRaisePressureWarningUntilAnIntervalIsAvailable() {
        com.sun.management.OperatingSystemMXBean os = mock(com.sun.management.OperatingSystemMXBean.class);
        when(os.getCpuLoad()).thenReturn(0.95);
        when(os.getTotalMemorySize()).thenReturn(1000L);
        when(os.getFreeMemorySize()).thenReturn(900L);
        RuntimeMonitorProbe probe = new RuntimeMonitorProbe(os);
        assertThat(probe.server().message()).doesNotContain("CPU 达到");
        assertThat(probe.server().message()).contains("CPU 达到");
    }

    @Test
    void poolReadsOnlyMxBeanAndReportsWaitingRequestsWithoutBorrowingAConnection() throws Exception {
        HikariDataSource source = mock(HikariDataSource.class);
        HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
        when(source.getMaximumPoolSize()).thenReturn(20);
        when(source.getHikariPoolMXBean()).thenReturn(pool);
        when(pool.getActiveConnections()).thenReturn(20);
        when(pool.getTotalConnections()).thenReturn(20);
        when(pool.getIdleConnections()).thenReturn(0);
        when(pool.getThreadsAwaitingConnection()).thenReturn(2);
        var component = new RuntimeMonitorProbe().pool(source);
        assertThat(component.status()).isEqualTo("DEGRADED");
        assertThat(metric(component, "pending")).isEqualTo(2);
        assertThat(metric(component, "idle")).isEqualTo(0);
        verify(source, never()).getConnection();
        when(source.getHikariPoolMXBean()).thenReturn(null);
        assertThat(new RuntimeMonitorProbe().pool(source).status()).isEqualTo("UNKNOWN");
    }

    @Test
    void redisKeepsRealZerosAndOnlyReturnsAllowlistedInfoValues() {
        RedisFixture fixture = new RedisFixture();
        when(fixture.node.info(Node.InfoSection.DEFAULT)).thenReturn(Map.of(
                "used_memory", "12", "maxmemory", "0", "connected_clients", "1", "blocked_clients", "0",
                "instantaneous_ops_per_sec", "0", "config_file", "/private/secret.conf", "master_host", "private-host"));
        var component = new RedisMonitorProbe(fixture.client).sample();
        assertThat(component.status()).isEqualTo("UP");
        assertThat(metric(component, "maxMemory")).isEqualTo(0L);
        assertThat(metric(component, "operationsPerSecond")).isEqualTo(0L);
        assertThat(component.toString()).doesNotContain("secret.conf", "private-host", "config_file");
        verify(fixture.node, never()).getAddr();
    }

    @Test
    void redisInfoDenialDoesNotClaimOfflineAndPingFailureDoesNotClaimHealthy() {
        RedisFixture fixture = new RedisFixture();
        when(fixture.node.info(Node.InfoSection.DEFAULT)).thenThrow(new IllegalStateException("secret redis://password@private-host"));
        var component = new RedisMonitorProbe(fixture.client).sample();
        assertThat(component.status()).isEqualTo("DEGRADED");
        assertThat(metric(component, "latency")).isNotNull();
        assertThat(metric(component, "usedMemory")).isNull();
        assertThat(component.toString()).doesNotContain("secret", "redis://", "private-host");
        when(fixture.node.ping(1, TimeUnit.SECONDS)).thenReturn(false);
        assertThat(new RedisMonitorProbe(fixture.client).sample().status()).isEqualTo("DOWN");
        assertThat(new RedisMonitorProbe(null).sample().status()).isEqualTo("UNKNOWN");
    }

    @Test
    void missingMalformedInfoAndUnsupportedTopologyNeverProduceFakeZeros() {
        RedisFixture fixture = new RedisFixture();
        when(fixture.node.info(Node.InfoSection.DEFAULT)).thenReturn(Map.of("used_memory", "NaN", "maxmemory", "-1"));
        var component = new RedisMonitorProbe(fixture.client).sample();
        assertThat(component.status()).isEqualTo("DEGRADED");
        assertThat(metric(component, "usedMemory")).isNull();
        assertThat(metric(component, "maxMemory")).isNull();
        when(fixture.nodes.getNodes()).thenReturn(List.of());
        assertThat(new RedisMonitorProbe(fixture.client).sample().status()).isEqualTo("UNKNOWN");
    }

    static class RedisFixture {
        final RedissonClient client = mock(RedissonClient.class);
        final Node node = mock(Node.class);
        @SuppressWarnings("unchecked") final NodesGroup<Node> nodes = mock(NodesGroup.class);
        RedisFixture() {
            when(client.getNodesGroup()).thenReturn(nodes);
            when(nodes.getNodes()).thenReturn(List.of(node));
            when(node.ping(1, TimeUnit.SECONDS)).thenReturn(true);
        }
    }
}
