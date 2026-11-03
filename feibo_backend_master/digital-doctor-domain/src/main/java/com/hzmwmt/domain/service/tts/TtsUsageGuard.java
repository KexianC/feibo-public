package com.hzmwmt.domain.service.tts;

import com.hzmwmt.types.exception.RateLimitException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounds paid TTS usage per authenticated user and caps in-process provider concurrency.
 * No medical text is retained; only counters keyed by the authenticated user id are kept.
 */
@Service
public class TtsUsageGuard {

    private static final int CLEANUP_INTERVAL = 256;
    private static final int CLEANUP_MINIMUM_SIZE = 1024;

    private final Semaphore concurrencyPermits;
    private final int maxRequestsPerWindow;
    private final int maxCharactersPerWindow;
    private final long windowMillis;
    private final ConcurrentMap<Long, UsageWindow> usageByUser = new ConcurrentHashMap<>();
    private final AtomicLong acquireCount = new AtomicLong();
    private final AtomicInteger waiters = new AtomicInteger();

    public TtsUsageGuard(
            @Value("${aliyun.dashscope.tts.max-concurrency:8}") int maxConcurrency,
            @Value("${aliyun.dashscope.tts.user-max-requests:30}") int maxRequestsPerWindow,
            @Value("${aliyun.dashscope.tts.user-max-characters:12000}") int maxCharactersPerWindow,
            @Value("${aliyun.dashscope.tts.usage-window-millis:60000}") long windowMillis) {
        this.concurrencyPermits = new Semaphore(clamp(maxConcurrency, 1, 64), true);
        this.maxRequestsPerWindow = clamp(maxRequestsPerWindow, 1, 1_000);
        this.maxCharactersPerWindow = clamp(maxCharactersPerWindow, 1, 1_000_000);
        this.windowMillis = clamp(windowMillis, 1_000L, 3_600_000L);
    }

    public Permit acquire(Long userId, int characterCount) {
        return acquire(userId, characterCount, new TtsCancellation());
    }

    public Permit acquire(Long userId, int characterCount, TtsCancellation cancellation) {
        if (userId == null) {
            throw new RateLimitException("语音合成请求无法识别用户");
        }
        cancellation.check();
        boolean acquired = false;
        if (waiters.incrementAndGet() > 32) {
            waiters.decrementAndGet();
            throw new RateLimitException("语音合成服务繁忙，请稍后点击继续朗读");
        }
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            do {
                cancellation.check();
                acquired = concurrencyPermits.tryAcquire(100, TimeUnit.MILLISECONDS);
            } while (!acquired && System.nanoTime() < deadline);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RateLimitException("语音合成等待已中断，请稍后再试");
        } finally {
            waiters.decrementAndGet();
        }
        if (!acquired) throw new RateLimitException("语音合成服务繁忙，请稍后点击继续朗读");
        if (cancellation.isCancelled()) {
            concurrencyPermits.release();
            cancellation.check();
        }

        long now = System.currentTimeMillis();
        UsageWindow usage = usageByUser.computeIfAbsent(userId, ignored -> new UsageWindow(now));
        if (!usage.tryConsume(
                now,
                Math.max(characterCount, 0),
                windowMillis,
                maxRequestsPerWindow,
                maxCharactersPerWindow)) {
            concurrencyPermits.release();
            throw new RateLimitException("语音合成请求过于频繁，请稍后再试");
        }

        cleanupStaleWindows(now);
        return new Permit(concurrencyPermits);
    }

    private void cleanupStaleWindows(long now) {
        long count = acquireCount.incrementAndGet();
        if (usageByUser.size() < CLEANUP_MINIMUM_SIZE || count % CLEANUP_INTERVAL != 0) {
            return;
        }
        long staleBefore = now - (windowMillis * 2);
        for (Map.Entry<Long, UsageWindow> entry : usageByUser.entrySet()) {
            if (entry.getValue().lastSeenBefore(staleBefore)) {
                usageByUser.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.min(Math.max(value, minimum), maximum);
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.min(Math.max(value, minimum), maximum);
    }

    private static final class UsageWindow {
        private long startedAt;
        private long lastSeenAt;
        private int requests;
        private int characters;

        private UsageWindow(long now) {
            this.startedAt = now;
            this.lastSeenAt = now;
        }

        private synchronized boolean tryConsume(
                long now,
                int characterCount,
                long windowMillis,
                int maxRequests,
                int maxCharacters) {
            lastSeenAt = now;
            if (now - startedAt >= windowMillis) {
                startedAt = now;
                requests = 0;
                characters = 0;
            }
            if (requests >= maxRequests || characters + characterCount > maxCharacters) {
                return false;
            }
            requests += 1;
            characters += characterCount;
            return true;
        }

        private synchronized boolean lastSeenBefore(long cutoff) {
            return lastSeenAt < cutoff;
        }
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }
}
