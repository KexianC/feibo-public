package com.hzmwmt.monitoring;

import jakarta.annotation.PreDestroy;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static com.hzmwmt.monitoring.SystemMonitorSnapshot.Component;

@Service
public class SystemMonitorService implements AutoCloseable {
    static final int REFRESH_SECONDS = 15;
    private final List<Probe> probes;
    private final ExecutorService executor;
    private final LongSupplier nanoTime;
    private final Clock clock;
    private final long timeoutNanos;
    private SystemMonitorSnapshot cached;
    private long cachedAt;

    @Autowired
    public SystemMonitorService(ObjectProvider<DataSource> dataSources, ObjectProvider<RedissonClient> redisClients) {
        this(defaultProbes(dataSources.getIfAvailable(), redisClients.getIfAvailable()), System::nanoTime,
                Clock.systemUTC(), Duration.ofSeconds(3));
    }

    SystemMonitorService(List<Probe> probes, LongSupplier nanoTime, Clock clock, Duration timeout) {
        this.probes = List.copyOf(probes);
        this.nanoTime = nanoTime;
        this.clock = clock;
        this.timeoutNanos = timeout.toNanos();
        this.executor = Executors.newFixedThreadPool(probes.size(), task -> {
            Thread thread = new Thread(task, "admin-system-monitor");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static List<Probe> defaultProbes(DataSource source, RedissonClient redis) {
        RuntimeMonitorProbe runtime = new RuntimeMonitorProbe();
        DatabaseMonitorProbe database = new DatabaseMonitorProbe(source);
        RedisMonitorProbe cache = new RedisMonitorProbe(redis);
        return List.of(new Probe("server", runtime::server), new Probe("jvm", runtime::jvm),
                new Probe("database", database::sample), new Probe("connectionPool", () -> runtime.pool(source)),
                new Probe("redis", cache::sample), new Probe("storage", runtime::storage));
    }

    /** One shared sample per interval. A blocked driver may occupy at most one worker per component. */
    public synchronized SystemMonitorSnapshot snapshot() {
        long now = nanoTime.getAsLong();
        if (cached != null && now - cachedAt >= 0 && now - cachedAt < TimeUnit.SECONDS.toNanos(REFRESH_SECONDS)) return cached;
        String sampledAt = clock.instant().toString();
        long deadline = System.nanoTime() + timeoutNanos;
        List<Future<Component>> pending = new ArrayList<>();
        for (Probe probe : probes) {
            if (!probe.busy.compareAndSet(false, true)) {
                pending.add(null);
                continue;
            }
            try {
                pending.add(executor.submit(() -> {
                    try { return probe.sampler.get(); }
                    finally { probe.busy.set(false); }
                }));
            } catch (RuntimeException error) {
                probe.busy.set(false);
                pending.add(null);
            }
        }
        List<Component> components = new ArrayList<>();
        for (int index = 0; index < probes.size(); index++) {
            Probe probe = probes.get(index);
            Future<Component> future = pending.get(index);
            Component component;
            if (future == null) {
                component = unavailable(probe.id, "上一次采集尚未完成，暂时无法确认");
            } else {
                try {
                    long remaining = Math.max(0, deadline - System.nanoTime());
                    component = future.get(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    component = unavailable(probe.id, "本次采集已中断，暂时无法确认");
                } catch (java.util.concurrent.TimeoutException error) {
                    component = unavailable(probe.id, "采集超过 3 秒，暂时无法确认");
                } catch (Exception error) {
                    component = unavailable(probe.id, "当前指标采集失败，暂时无法确认");
                }
                // Do not cancel and release the busy guard: drivers can ignore interruption and keep running.
            }
            components.add(component);
        }
        cached = new SystemMonitorSnapshot(sampledAt, REFRESH_SECONDS, List.copyOf(components));
        cachedAt = nanoTime.getAsLong();
        return cached;
    }

    private Component unavailable(String id, String reason) { return new MonitorReadings(id).unavailable("UNKNOWN", reason).build(); }

    @Override
    @PreDestroy
    public void close() { executor.shutdownNow(); }

    static final class Probe {
        final String id;
        final Supplier<Component> sampler;
        final AtomicBoolean busy = new AtomicBoolean();
        Probe(String id, Supplier<Component> sampler) { this.id = id; this.sampler = sampler; }
    }
}
