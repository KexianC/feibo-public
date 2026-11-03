package com.hzmwmt.monitoring;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.hzmwmt.monitoring.SystemMonitorSnapshot.Component;

final class RuntimeMonitorProbe {
    private final OperatingSystemMXBean os;
    private boolean cpuSampled;

    RuntimeMonitorProbe() { this(ManagementFactory.getOperatingSystemMXBean()); }
    RuntimeMonitorProbe(OperatingSystemMXBean os) { this.os = os; }

    Component server() {
        MonitorReadings readings = new MonitorReadings("server")
                .put("logicalProcessors", os.getAvailableProcessors(), "JVM 可见的逻辑处理器数量，受容器配置影响")
                .put("systemLoad1m", os.getSystemLoadAverage(), "运行与等待执行的系统任务平均数；不是百分比，部分操作系统不支持");
        if (os instanceof com.sun.management.OperatingSystemMXBean extended) {
            double cpu = extended.getCpuLoad();
            boolean validCpu = cpuSampled && cpu >= 0;
            readings.put("cpuPercent", validCpu ? Math.round(cpu * 10000.0) / 100.0 : null,
                    !cpuSampled ? "首次采样，等待下一次采样获得 CPU 区间值"
                            : cpu < 0 ? "运行环境未提供 CPU 区间统计" : "JVM 可见的系统近期 CPU 使用率；受运行环境影响");
            cpuSampled = true;
            long total = extended.getTotalMemorySize();
            long free = extended.getFreeMemorySize();
            Long used = total > 0 && free >= 0 && free <= total ? total - free : null;
            Double usage = MonitorReadings.percent(used, total);
            readings.put("physicalTotal", total > 0 ? total : null)
                    .put("physicalUsed", used, "总量减空闲量，包含操作系统缓存；JVM 可见范围受容器配置影响")
                    .put("physicalUsedPercent", usage, "物理内存已用量 / 总量，包含操作系统缓存");
            if ((usage != null && usage >= 95) || (validCpu && cpu >= 0.9)) readings.status("DEGRADED", "CPU 达到 90% 或物理内存使用达到 95%；请结合持续采样观察");
        }
        return readings.build();
    }

    Component jvm() {
        MonitorReadings readings = new MonitorReadings("jvm");
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Double usage = MonitorReadings.percent(heap.getUsed(), heap.getMax());
        readings.put("heapUsed", heap.getUsed()).put("heapMax", heap.getMax())
                .put("heapUsedPercent", usage)
                .put("nonHeapUsed", ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed())
                .put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount())
                .put("uptime", ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
        long count = 0, time = 0;
        boolean hasCount = false, hasTime = false;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() >= 0) { count += gc.getCollectionCount(); hasCount = true; }
            if (gc.getCollectionTime() >= 0) { time += gc.getCollectionTime(); hasTime = true; }
        }
        readings.put("gcCount", hasCount ? count : null, "支持统计的垃圾回收器自应用启动以来累计值")
                .put("gcTime", hasTime ? time : null, "支持统计的垃圾回收器自应用启动以来累计值");
        if (usage != null && usage >= 90) readings.status("DEGRADED", "JVM 堆内存使用达到上限的 90%");
        return readings.build();
    }

    Component pool(DataSource dataSource) {
        MonitorReadings readings = new MonitorReadings("connectionPool");
        if (dataSource == null) return readings.unavailable("UNKNOWN", "应用未配置数据库连接池").build();
        try {
            HikariDataSource hikari = dataSource instanceof HikariDataSource h ? h
                    : dataSource.isWrapperFor(HikariDataSource.class) ? dataSource.unwrap(HikariDataSource.class) : null;
            if (hikari == null) return readings.unavailable("UNKNOWN", "当前数据源不提供 Hikari 连接池指标").build();
            readings.put("maximum", hikari.getMaximumPoolSize());
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool == null) return readings.status("UNKNOWN", "连接池尚未初始化，暂无实时连接统计").build();
            int pending = pool.getThreadsAwaitingConnection();
            readings.put("active", pool.getActiveConnections()).put("idle", pool.getIdleConnections())
                    .put("total", pool.getTotalConnections()).put("pending", pending);
            if (pending > 0) readings.status("DEGRADED", "存在等待数据库连接的请求");
            return readings.build();
        } catch (Exception ignored) {
            return readings.unavailable("UNKNOWN", "当前连接池指标采集失败").build();
        }
    }

    Component storage() {
        MonitorReadings readings = new MonitorReadings("storage");
        try {
            // Inspect only the application's own fixed working volume; paths are never included in responses.
            FileStore store = Files.getFileStore(Path.of(System.getProperty("user.dir")));
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            Double used = total > 0 && usable >= 0 && usable <= total ? MonitorReadings.percent(total - usable, total) : null;
            readings.put("diskTotal", total > 0 ? total : null).put("diskUsable", usable)
                    .put("diskUsedPercent", used, "应用工作目录所在卷；总量减应用可用空间，包含保留空间；不代表远端对象存储");
            if (used != null && used >= 90) readings.status("DEGRADED", "应用所在磁盘卷不可用空间达到 90%");
            return readings.build();
        } catch (IOException | RuntimeException ignored) {
            return readings.unavailable("UNKNOWN", "应用所在磁盘卷统计不可用").build();
        }
    }
}
