package com.hzmwmt.monitoring;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

import static com.hzmwmt.monitoring.SystemMonitorSnapshot.Component;

final class DatabaseMonitorProbe {
    private static final String STATS_UNAVAILABLE = "数据库未提供此统计或当前账号无读取权限";
    private final DataSource dataSource;
    private final LongSupplier nanoTime;
    private final QueryRate queryRate = new QueryRate();

    DatabaseMonitorProbe(DataSource dataSource) { this(dataSource, System::nanoTime); }
    DatabaseMonitorProbe(DataSource dataSource, LongSupplier nanoTime) {
        this.dataSource = dataSource;
        this.nanoTime = nanoTime;
    }

    Component sample() {
        MonitorReadings readings = new MonitorReadings("database");
        if (dataSource == null) return readings.unavailable("UNKNOWN", "应用未配置数据库连接").build();
        boolean connected = false;
        long started = nanoTime.getAsLong();
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = statement(connection); ResultSet result = statement.executeQuery("SELECT 1")) {
                if (!result.next() || result.getInt(1) != 1) throw new SQLException();
            }
            connected = true;
            readings.put("latency", (nanoTime.getAsLong() - started) / 1_000_000.0,
                    "包括从应用连接池获取连接与执行只读探测的耗时");
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !(product.toLowerCase(Locale.ROOT).contains("mysql") || product.toLowerCase(Locale.ROOT).contains("mariadb"))) {
                return readings.status("DEGRADED", "数据库连通；当前数据库类型不支持 MySQL 实例统计").build();
            }
            Map<String, Long> status = globalStatus(connection);
            long statusAt = nanoTime.getAsLong();
            Long running = status.get("threads_running");
            Long connectedThreads = status.get("threads_connected");
            Long max = scalar(connection, "SELECT @@global.max_connections");
            readings.put("threadsRunning", running, running == null ? STATS_UNAVAILABLE : "整个数据库实例，包含本次监控查询")
                    .put("threadsConnected", connectedThreads, connectedThreads == null ? STATS_UNAVAILABLE : "整个数据库实例")
                    .put("maxConnections", max, max == null ? STATS_UNAVAILABLE : "整个数据库实例")
                    .put("connectionUsagePercent", MonitorReadings.percent(connectedThreads, max), "整个数据库实例；当前连接数 / 连接上限");
            Rate rate = queryRate.sample(status.get("questions"), status.get("uptime"), status.get("uptime_since_flush_status"), statusAt);
            readings.put("queriesPerSecond", rate.value(), rate.note());
            Long schemaBytes = scalar(connection,
                    "SELECT CASE WHEN DATABASE() IS NULL THEN NULL ELSE COALESCE(SUM(COALESCE(data_length,0) + COALESCE(index_length,0)),0) END FROM information_schema.tables WHERE table_schema = DATABASE()");
            readings.put("schemaBytes", schemaBytes, schemaBytes == null ? STATS_UNAVAILABLE : "当前连接业务库的表数据与索引分配空间；不含日志，统计可能延迟");
            Double usage = MonitorReadings.percent(connectedThreads, max);
            if (usage != null && usage >= 90) readings.status("DEGRADED", "数据库实例连接占用达到 90%");
        } catch (SQLException | RuntimeException ignored) {
            queryRate.reset();
            if (!connected) return readings.unavailable("DOWN", "数据库连接或只读探测失败").build();
            readings.status("DEGRADED", "数据库连通，部分统计采集失败");
        }
        return readings.build();
    }

    private Map<String, Long> globalStatus(Connection connection) {
        Map<String, Long> values = new HashMap<>();
        try (Statement statement = statement(connection); ResultSet result = statement.executeQuery(
                    "SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_running','Threads_connected','Questions','Uptime','Uptime_since_flush_status')")) {
            while (result.next()) {
                String key = result.getString(1);
                if (key != null) values.put(key.toLowerCase(Locale.ROOT), MonitorReadings.nonNegativeLong(result.getString(2)));
            }
        } catch (SQLException | RuntimeException ignored) {
            // Keep latency and other independently available fields; never return raw database errors.
        }
        return values;
    }

    private Long scalar(Connection connection, String sql) {
        try (Statement statement = statement(connection); ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) return null;
            long value = result.getLong(1);
            return result.wasNull() || value < 0 ? null : value;
        } catch (SQLException | RuntimeException ignored) { return null; }
    }

    private Statement statement(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            statement.setQueryTimeout(1);
            statement.setMaxRows(10);
            return statement;
        } catch (SQLException | RuntimeException error) {
            statement.close();
            throw error;
        }
    }

    record Rate(Double value, String note) {}

    static final class QueryRate {
        private Long previousQuestions;
        private Long previousUptime;
        private Long previousFlushUptime;
        private long previousNanos;

        Rate sample(Long questions, Long uptime, long nanos) {
            return sample(questions, uptime, null, nanos);
        }

        Rate sample(Long questions, Long uptime, Long flushUptime, long nanos) {
            if (questions == null || uptime == null) {
                reset();
                return new Rate(null, STATS_UNAVAILABLE);
            }
            Double value = null;
            String note = "首次采样，等待下一次采样计算；范围为整个数据库实例";
            if (previousQuestions != null) {
                long elapsed = nanos - previousNanos;
                // A restarted/FLUSHed instance can already have exceeded the previous absolute counters.
                // Compare elapsed lifetimes too; tolerate second rounding and the bounded status query.
                boolean lifetimeChanged = Math.abs((uptime - previousUptime) - elapsed / 1_000_000_000.0) > 3;
                boolean flushed = flushUptime != null && previousFlushUptime != null
                        && Math.abs((flushUptime - previousFlushUptime) - elapsed / 1_000_000_000.0) > 3;
                if (questions < previousQuestions || uptime < previousUptime || elapsed <= 0 || lifetimeChanged || flushed) {
                    note = "计数或采样时钟已重置，等待下一次采样";
                } else {
                    value = Math.round((questions - previousQuestions) / (elapsed / 1_000_000_000.0) * 100.0) / 100.0;
                    note = "两次有效采样之间 Questions 增量 / 实际间隔；包括本次监控产生的语句，范围为整个实例";
                }
            }
            previousQuestions = questions;
            previousUptime = uptime;
            previousFlushUptime = flushUptime;
            previousNanos = nanos;
            return new Rate(value, note);
        }

        void reset() { previousQuestions = null; previousUptime = null; previousFlushUptime = null; }
    }
}
