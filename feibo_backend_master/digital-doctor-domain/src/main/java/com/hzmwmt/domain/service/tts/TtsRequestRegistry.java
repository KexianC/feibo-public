package com.hzmwmt.domain.service.tts;

import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import com.hzmwmt.types.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Iterator;
import java.util.function.LongSupplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Bounded, user-isolated, five-minute in-memory retry cache. Never persisted. */
@Slf4j
@Service
public class TtsRequestRegistry {
    private static final long TTL_MS = 300_000L;
    private static final int MAX_ENTRIES = 512;
    private static final long MAX_AUDIO_BYTES = 64L * 1024 * 1024;
    private static final long MAX_USER_AUDIO_BYTES = 16L * 1024 * 1024;
    private final TtsService service;
    private final TtsUsageGuard usage;
    private final LongSupplier clock;
    private final long ttlMillis;
    private final int maxEntries;
    private final long maxAudioBytes;
    private final long maxUserAudioBytes;
    private final int maxCancelledSessions;
    private final Map<RequestKey, Entry> requests = new HashMap<>();
    private final Map<SessionKey, Long> cancelledSessions = new HashMap<>();
    private long audioBytes;

    @Autowired
    public TtsRequestRegistry(TtsService service, TtsUsageGuard usage) {
        this(service, usage, System::currentTimeMillis, TTL_MS, MAX_ENTRIES, MAX_AUDIO_BYTES, MAX_USER_AUDIO_BYTES, 2048);
    }

    TtsRequestRegistry(TtsService service, TtsUsageGuard usage, LongSupplier clock,
                       long ttlMillis, int maxEntries, long maxAudioBytes, long maxUserAudioBytes,
                       int maxCancelledSessions) {
        this.service = service;
        this.usage = usage;
        this.clock = clock;
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
        this.maxAudioBytes = maxAudioBytes;
        this.maxUserAudioBytes = maxUserAudioBytes;
        this.maxCancelledSessions = maxCancelledSessions;
    }

    public byte[] synthesize(Long userId, String sessionId, String requestId, String text) {
        validateId(sessionId);
        validateId(requestId);
        if (userId == null || text == null) throw invalid("语音请求参数无效");
        SessionKey sessionKey = new SessionKey(userId, sessionId);
        RequestKey key = new RequestKey(userId, requestId);
        String hash = digest(text);
        Entry entry;
        boolean owner;
        synchronized (this) {
            purgeExpired();
            if (cancelledSessions.containsKey(sessionKey)
                    || requests.values().stream().anyMatch(e -> e.session.equals(sessionKey) && e.cancellation.isCancelled()))
                throw invalid("本次语音播报已取消");
            entry = requests.get(key);
            owner = entry == null;
            if (owner) {
                if (requests.size() >= maxEntries) throw new RateLimitException("语音缓存繁忙，请稍后再试");
                entry = new Entry(sessionKey, hash, clock.getAsLong() + ttlMillis);
                requests.put(key, entry);
            } else if (!entry.session.equals(sessionKey) || !entry.textHash.equals(hash)) {
                throw invalid("同一语音请求标识不能对应不同内容");
            } else if (entry.evicted) {
                throw invalid("该段语音缓存已过期或释放，请使用单条重播重新开始");
            }
        }
        if (!owner) return await(entry);
        long started = System.nanoTime();
        try (TtsUsageGuard.Permit ignored = usage.acquire(userId, text.length(), entry.cancellation)) {
            byte[] audio = service.synthesize(text, entry.cancellation);
            entry.cancellation.check();
            synchronized (this) {
                entry.cancellation.check();
                long userBytes = requests.values().stream().filter(e -> e.session.userId.equals(userId))
                        .mapToLong(e -> e.cachedBytes).sum();
                entry.expiresAt = clock.getAsLong() + ttlMillis;
                if (audioBytes + audio.length <= maxAudioBytes && userBytes + audio.length <= maxUserAudioBytes) {
                    entry.cachedBytes = audio.length;
                    audioBytes += audio.length;
                } else {
                    // Preserve a tombstone: eviction must not silently charge a retry twice.
                    Entry tombstone = new Entry(sessionKey, hash, entry.expiresAt + ttlMillis);
                    tombstone.evicted = true;
                    requests.put(key, tombstone);
                }
                entry.result.complete(audio);
            }
            log.info("voice_tts outcome=success elapsed_ms={} audio_bytes={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), audio.length);
            return audio;
        } catch (RuntimeException failure) {
            synchronized (this) { requests.remove(key, entry); }
            entry.result.completeExceptionally(failure);
            log.info("voice_tts outcome={} elapsed_ms={}", entry.cancellation.isCancelled() ? "cancelled" : "failed",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            throw failure;
        }
    }

    public void cancel(Long userId, String sessionId) {
        validateId(sessionId);
        if (userId == null) throw invalid("无法识别语音用户");
        SessionKey key = new SessionKey(userId, sessionId);
        ArrayList<Entry> affected = new ArrayList<>();
        ArrayList<Runnable> cleanups = new ArrayList<>();
        synchronized (this) {
            purgeExpired();
            boolean atCapacity = !cancelledSessions.containsKey(key) && cancelledSessions.size() >= maxCancelledSessions;
            boolean knownSession = requests.values().stream().anyMatch(entry -> entry.session.equals(key));
            if (atCapacity && !knownSession) throw new RateLimitException("语音取消请求过于频繁");
            long expiresAt = clock.getAsLong() + ttlMillis * 2;
            if (!atCapacity) cancelledSessions.put(key, expiresAt);
            for (Iterator<Map.Entry<RequestKey, Entry>> iterator = requests.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<RequestKey, Entry> item = iterator.next();
                Entry entry = item.getValue();
                if (!entry.session.equals(key)) continue;
                audioBytes -= entry.cachedBytes;
                affected.add(entry);
                cleanups.add(entry.cancellation.prepareCancel());
                entry.result.completeExceptionally(invalid("本次语音播报已取消"));
                if (atCapacity) {
                    // Unknown cancellation IDs cannot prevent stopping real work.
                    // Reuse its already-bounded request slot as a cancellation gate.
                    Entry marker = new Entry(key, entry.textHash, expiresAt);
                    marker.evicted = true;
                    marker.cancellation.cancel();
                    marker.result.completeExceptionally(invalid("本次语音播报已取消"));
                    item.setValue(marker);
                } else iterator.remove();
            }
        }
        // Never call the upstream while holding the registry lock.
        cleanups.forEach(Runnable::run);
        log.info("voice_tts outcome=session_cancelled affected_segments={}", affected.size());
    }

    @Scheduled(fixedDelay = 30_000L)
    public synchronized void purgeExpired() {
        long now = clock.getAsLong();
        for (Iterator<Map.Entry<RequestKey, Entry>> iterator = requests.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<RequestKey, Entry> item = iterator.next();
            Entry entry = item.getValue();
            if (now < entry.expiresAt || (!entry.evicted && !entry.result.isDone())) continue;
            audioBytes -= entry.cachedBytes;
            if (!entry.evicted && entry.cachedBytes > 0) {
                // Make expiration explicit for delayed retries, rather than silently
                // invoking the paid provider again with an expired request identity.
                Entry tombstone = new Entry(entry.session, entry.textHash, now + ttlMillis);
                tombstone.evicted = true;
                item.setValue(tombstone);
            } else iterator.remove();
        }
        cancelledSessions.entrySet().removeIf(item -> item.getValue() <= now);
    }

    private static byte[] await(Entry entry) {
        try {
            entry.cancellation.check();
            byte[] audio = entry.result.get(130, TimeUnit.SECONDS);
            entry.cancellation.check();
            return audio;
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw invalid("语音等待已中断");
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw invalid("语音合成失败，请稍后再试");
        } catch (java.util.concurrent.TimeoutException timeout) {
            throw invalid("语音合成等待超时，请稍后再试");
        }
    }

    private static void validateId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) throw invalid("语音请求标识无效");
    }

    private static AppException invalid(String message) {
        return new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
    }

    private static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private record RequestKey(Long userId, String requestId) {}
    private record SessionKey(Long userId, String sessionId) {}
    private static final class Entry {
        final SessionKey session;
        final String textHash;
        long expiresAt;
        final TtsCancellation cancellation = new TtsCancellation();
        final CompletableFuture<byte[]> result = new CompletableFuture<>();
        long cachedBytes;
        boolean evicted;
        Entry(SessionKey session, String textHash, long expiresAt) {
            this.session = session; this.textHash = textHash; this.expiresAt = expiresAt;
        }
    }
}
