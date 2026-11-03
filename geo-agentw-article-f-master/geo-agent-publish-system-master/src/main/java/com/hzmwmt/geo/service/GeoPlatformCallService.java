package com.hzmwmt.geo.service;

import com.hzmwmt.geo.config.GeoProperties;
import com.hzmwmt.geo.model.dto.GeoEngineAnswer;
import com.hzmwmt.geo.service.platform.GeoPlatformAdapter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Service
public class GeoPlatformCallService {

    @Resource
    private GeoProperties geoProperties;

    private final Map<String, Semaphore> platformSemaphores = new ConcurrentHashMap<>();
    private final Map<String, Object> platformLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> platformLastCallAt = new ConcurrentHashMap<>();

    public GeoEngineAnswer ask(GeoProperties.EngineProperties engine,
                               GeoPlatformAdapter adapter,
                               String queryText) throws InterruptedException {
        return execute(engine, adapter, () -> adapter.ask(engine, queryText));
    }

    public GeoEngineAnswer diagnose(GeoProperties.EngineProperties engine,
                                    GeoPlatformAdapter adapter,
                                    String diagnosticPrompt) throws InterruptedException {
        return execute(engine, adapter, () -> adapter.diagnose(engine, diagnosticPrompt));
    }

    private GeoEngineAnswer execute(GeoProperties.EngineProperties engine,
                                    GeoPlatformAdapter adapter,
                                    Supplier<GeoEngineAnswer> action) throws InterruptedException {
        String platform = StringUtils.hasText(engine.getName()) ? engine.getName() : adapter.platformCode();
        Semaphore semaphore = platformSemaphores.computeIfAbsent(
                platform,
                key -> new Semaphore(Math.max(1, geoProperties.getPlatformConcurrency()))
        );
        semaphore.acquire();
        try {
            Object lock = platformLocks.computeIfAbsent(platform, key -> new Object());
            synchronized (lock) {
                long now = System.currentTimeMillis();
                long last = platformLastCallAt.getOrDefault(platform, 0L);
                long waitMs = geoProperties.getPlatformMinIntervalMs() - (now - last);
                if (waitMs > 0) {
                    Thread.sleep(waitMs);
                }
                platformLastCallAt.put(platform, System.currentTimeMillis());
            }
            return action.get();
        } finally {
            semaphore.release();
        }
    }
}
