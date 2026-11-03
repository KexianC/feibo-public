package com.hzmwmt.monitoring;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

class SystemMonitorServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void cacheExpiresAtFifteenSecondsAndConcurrentReadersShareOneSample() throws Exception {
        AtomicLong now = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        SystemMonitorService.Probe probe = new SystemMonitorService.Probe("jvm", () -> {
            calls.incrementAndGet();
            return new MonitorReadings("jvm").put("threadCount", 4).build();
        });
        try (SystemMonitorService service = new SystemMonitorService(List.of(probe), now::get, clock, Duration.ofSeconds(1))) {
            var clients = Executors.newFixedThreadPool(8);
            try {
                var responses = clients.invokeAll(java.util.stream.IntStream.range(0, 16)
                        .<java.util.concurrent.Callable<SystemMonitorSnapshot>>mapToObj(i -> service::snapshot).toList());
                SystemMonitorSnapshot initial = responses.get(0).get();
                for (var response : responses) assertThat(response.get()).isSameAs(initial);
                assertThat(calls.get()).isEqualTo(1);
                assertThat(initial.sampledAt()).isEqualTo("2026-09-14T00:00:00Z");
                now.set(TimeUnit.SECONDS.toNanos(15) - 1);
                assertThat(service.snapshot()).isSameAs(initial);
                now.incrementAndGet();
                assertThat(service.snapshot()).isNotSameAs(initial);
                assertThat(calls.get()).isEqualTo(2);
            } finally { clients.shutdownNow(); }
        }
    }

    @Test
    void timedOutProbeDoesNotBlockHealthyComponentsOrCreateRepeatedDriverCalls() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger databaseCalls = new AtomicInteger();
        AtomicLong now = new AtomicLong();
        SystemMonitorService.Probe blocked = new SystemMonitorService.Probe("database", () -> {
            databaseCalls.incrementAndGet();
            started.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            return new MonitorReadings("database").put("latency", 99).build();
        });
        SystemMonitorService.Probe healthy = new SystemMonitorService.Probe("redis", () -> new MonitorReadings("redis").put("latency", 0).build());
        try (SystemMonitorService service = new SystemMonitorService(List.of(blocked, healthy), now::get, clock, Duration.ofMillis(80))) {
            long wallStarted = System.nanoTime();
            SystemMonitorSnapshot first = service.snapshot();
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(Duration.ofNanos(System.nanoTime() - wallStarted)).isLessThan(Duration.ofSeconds(2));
            assertThat(first.components().get(0).status()).isEqualTo("UNKNOWN");
            assertThat(first.components().get(0).metrics()).allMatch(m -> m.value() == null);
            assertThat(first.components().get(1).metrics().get(0).value()).isEqualTo(0);
            now.addAndGet(TimeUnit.SECONDS.toNanos(15));
            SystemMonitorSnapshot second = service.snapshot();
            assertThat(second.components().get(0).message()).contains("上一次采集尚未完成");
            assertThat(databaseCalls.get()).isEqualTo(1);
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (blocked.busy.get() && System.nanoTime() < deadline) Thread.onSpinWait();
            assertThat(blocked.busy.get()).isFalse();
            // A late completion cannot replace the snapshot already delivered to the browser.
            assertThat(service.snapshot()).isSameAs(second);
            now.addAndGet(TimeUnit.SECONDS.toNanos(15));
            assertThat(service.snapshot().components().get(0).metrics().get(0).value()).isEqualTo(99);
            assertThat(databaseCalls.get()).isEqualTo(2);
        } finally { release.countDown(); }
    }

    @Test
    void unexpectedProbeExceptionIsIsolatedAndNeverLeaksItsMessage() {
        try (SystemMonitorService service = new SystemMonitorService(List.of(
                new SystemMonitorService.Probe("database", () -> { throw new IllegalStateException("password=secret jdbc:mysql://private/db"); }),
                new SystemMonitorService.Probe("jvm", () -> new MonitorReadings("jvm").put("threadCount", 3).build())
        ), System::nanoTime, clock, Duration.ofSeconds(1))) {
            SystemMonitorSnapshot snapshot = service.snapshot();
            assertThat(snapshot.components().get(0).status()).isEqualTo("UNKNOWN");
            assertThat(snapshot.toString()).doesNotContain("password", "secret", "jdbc:mysql", "private/db");
            assertThat(snapshot.components().get(1).metrics()).anyMatch(m -> m.key().equals("threadCount") && m.value().equals(3));
        }
    }
}
