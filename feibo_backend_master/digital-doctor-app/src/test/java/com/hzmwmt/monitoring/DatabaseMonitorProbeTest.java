package com.hzmwmt.monitoring;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseMonitorProbeTest {
    @Test
    void realProbeReadsWhitelistedStatisticsAndDistinguishesFirstSampleFromZeroRate() throws Exception {
        Fixture fixture = new Fixture();
        DatabaseMonitorProbe probe = new DatabaseMonitorProbe(fixture.source, fixture.time::get);
        var first = probe.sample();
        assertThat(metric(first, "queriesPerSecond")).isNull();
        assertThat(metric(first, "threadsRunning")).isEqualTo(0L);
        assertThat(metric(first, "schemaBytes")).isEqualTo(0L);
        assertThat(metric(first, "connectionUsagePercent")).isEqualTo(10.0);
        fixture.time.addAndGet(TimeUnit.SECONDS.toNanos(15));
        fixture.uptime.addAndGet(15);
        var second = probe.sample();
        assertThat(metric(second, "queriesPerSecond")).isEqualTo(0.0);
        assertThat(second.status()).isEqualTo("UP");
        fixture.questions.addAndGet(150);
        fixture.uptime.addAndGet(15);
        fixture.time.addAndGet(TimeUnit.SECONDS.toNanos(15));
        assertThat(metric(probe.sample(), "queriesPerSecond")).isEqualTo(10.0);
        for (Statement statement : fixture.statements) {
            verify(statement).setQueryTimeout(1);
            verify(statement).setMaxRows(10);
            verify(statement).close();
        }
        verify(fixture.connection, times(3)).close();
    }

    @Test
    void deniedStatisticsAreDegradedWhileSuccessfulPingRemainsVisibleAndSafe() throws Exception {
        Fixture fixture = new Fixture();
        fixture.denyStatistics = true;
        var component = new DatabaseMonitorProbe(fixture.source, fixture.time::get).sample();
        assertThat(component.status()).isEqualTo("DEGRADED");
        assertThat(metric(component, "latency")).isEqualTo(0.0);
        assertThat(metric(component, "queriesPerSecond")).isNull();
        assertThat(metric(component, "schemaBytes")).isNull();
        assertThat(component.toString()).doesNotContain("password", "secret", "private-host");
    }

    @Test
    void brokenConnectionIsDownWithNullValuesAndAbsentConfigurationIsUnknown() throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("password=secret private-host"));
        var component = new DatabaseMonitorProbe(source).sample();
        assertThat(component.status()).isEqualTo("DOWN");
        assertThat(component.metrics()).allMatch(m -> m.value() == null && m.note() != null);
        assertThat(component.toString()).doesNotContain("secret", "private-host");
        assertThat(new DatabaseMonitorProbe(null).sample().status()).isEqualTo("UNKNOWN");
    }

    @Test
    void connectionPressureIsVisibleAndUnsupportedDatabaseIsNotReportedFullyHealthy() throws Exception {
        Fixture fixture = new Fixture();
        fixture.connections = 95;
        assertThat(new DatabaseMonitorProbe(fixture.source).sample().message()).contains("90%");
        when(fixture.metadata.getDatabaseProductName()).thenReturn("OtherDB");
        var other = new DatabaseMonitorProbe(fixture.source).sample();
        assertThat(other.status()).isEqualTo("DEGRADED");
        assertThat(metric(other, "threadsRunning")).isNull();
    }

    @Test
    void counterResetUptimeResetMissingSampleAndInvalidClockRequireANewInterval() {
        DatabaseMonitorProbe.QueryRate rate = new DatabaseMonitorProbe.QueryRate();
        assertThat(rate.sample(100L, 10L, 0).value()).isNull();
        assertThat(rate.sample(110L, 11L, 1_000_000_000L).value()).isEqualTo(10.0);
        assertThat(rate.sample(1L, 12L, 2_000_000_000L).value()).isNull();
        assertThat(rate.sample(5L, 1L, 3_000_000_000L).value()).isNull();
        assertThat(rate.sample(9L, 2L, 3_000_000_000L).value()).isNull();
        assertThat(rate.sample(null, 3L, 4_000_000_000L).value()).isNull();
        assertThat(rate.sample(15L, 4L, 5_000_000_000L).value()).isNull();
        assertThat(rate.sample(15L, 5L, 6_000_000_000L).value()).isEqualTo(0.0);
    }

    @Test
    void restartOrFlushAfterCountersHaveRegrownStillInvalidatesTheInterval() {
        DatabaseMonitorProbe.QueryRate restarted = new DatabaseMonitorProbe.QueryRate();
        restarted.sample(1000L, 10L, 0);
        assertThat(restarted.sample(5000L, 14L, 15_000_000_000L).value()).isNull();
        assertThat(restarted.sample(5150L, 29L, 30_000_000_000L).value()).isEqualTo(10.0);
        DatabaseMonitorProbe.QueryRate flushed = new DatabaseMonitorProbe.QueryRate();
        flushed.sample(1000L, 1000L, 10L, 0);
        assertThat(flushed.sample(5000L, 1015L, 14L, 15_000_000_000L).value()).isNull();
        assertThat(flushed.sample(5150L, 1030L, 29L, 30_000_000_000L).value()).isEqualTo(10.0);
    }

    static Object metric(SystemMonitorSnapshot.Component component, String key) {
        return component.metrics().stream().filter(metric -> metric.key().equals(key)).findFirst().orElseThrow().value();
    }

    static class Fixture {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        final AtomicLong time = new AtomicLong();
        final AtomicLong questions = new AtomicLong(1000);
        final AtomicLong uptime = new AtomicLong(100);
        final List<Statement> statements = new ArrayList<>();
        boolean denyStatistics;
        long connections = 10;

        Fixture() throws SQLException {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getDatabaseProductName()).thenReturn("MySQL");
            when(connection.createStatement()).thenAnswer(call -> {
                Statement statement = mock(Statement.class);
                statements.add(statement);
                when(statement.executeQuery(anyString())).thenAnswer(query -> {
                    String sql = query.getArgument(0);
                    if (sql.equals("SELECT 1")) return rows(new Object[][]{{1L}});
                    if (denyStatistics) throw new SQLException("password=secret private-host denied");
                    if (sql.startsWith("SHOW GLOBAL STATUS")) return rows(new Object[][]{
                            {"Threads_running", 0L}, {"Threads_connected", connections}, {"Questions", questions.get()}, {"Uptime", uptime.get()}});
                    if (sql.equals("SELECT @@global.max_connections")) return rows(new Object[][]{{100L}});
                    if (sql.contains("information_schema.tables")) return rows(new Object[][]{{0L}});
                    throw new AssertionError("Unexpected SQL in fixed probe");
                });
                return statement;
            });
        }

        private ResultSet rows(Object[][] values) throws SQLException {
            ResultSet result = mock(ResultSet.class);
            AtomicInteger row = new AtomicInteger(-1);
            when(result.next()).thenAnswer(call -> row.incrementAndGet() < values.length);
            when(result.getString(anyInt())).thenAnswer(call -> String.valueOf(values[row.get()][(int) call.getArgument(0) - 1]));
            when(result.getLong(anyInt())).thenAnswer(call -> ((Number) values[row.get()][(int) call.getArgument(0) - 1]).longValue());
            when(result.getInt(anyInt())).thenAnswer(call -> ((Number) values[row.get()][(int) call.getArgument(0) - 1]).intValue());
            return result;
        }
    }
}
