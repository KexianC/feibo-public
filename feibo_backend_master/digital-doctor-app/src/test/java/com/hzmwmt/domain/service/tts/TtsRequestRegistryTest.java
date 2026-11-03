package com.hzmwmt.domain.service.tts;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.hzmwmt.types.exception.AppException;
import com.hzmwmt.types.exception.RateLimitException;
import io.reactivex.Flowable;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TtsRequestRegistryTest {
    private static TtsUsageGuard generousGuard() { return new TtsUsageGuard(8, 1000, 1000000, 60000); }

    private static TtsRequestRegistry smallRegistry(TtsService service, AtomicLong clock,
                                                   int entries, long totalBytes, long userBytes, int cancellations) {
        return new TtsRequestRegistry(service, generousGuard(), clock::get, 100,
                entries, totalBytes, userBytes, cancellations);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try { assertTrue(latch.await(3, TimeUnit.SECONDS), "controlled provider was not released"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }

    @Test
    void successfulReplayConsumesNeitherAnotherProviderCallNorQuota() {
        AtomicInteger calls = new AtomicInteger();
        TtsRequestRegistry registry = new TtsRequestRegistry(text -> {
            calls.incrementAndGet(); return new byte[]{1, 2};
        }, new TtsUsageGuard(2, 1, 100, 60000));
        byte[] first = registry.synthesize(1L, "session", "request", "同一句");
        assertArrayEquals(first, registry.synthesize(1L, "session", "request", "同一句"));
        assertEquals(1, calls.get());
        assertThrows(RateLimitException.class, () -> registry.synthesize(1L, "session", "next", "新句子"));
    }

    @Test
    void concurrentDuplicatesShareOneProviderResult() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        TtsRequestRegistry registry = new TtsRequestRegistry(text -> {
            calls.incrementAndGet(); providerEntered.countDown(); awaitLatch(releaseProvider);
            return new byte[]{3};
        }, new TtsUsageGuard(2, 1, 100, 60000));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            ArrayList<Future<byte[]>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(executor.submit(() -> registry.synthesize(1L, "s", "r", "同一句")));
            awaitLatch(providerEntered);
            releaseProvider.countDown();
            for (Future<byte[]> result : results) assertArrayEquals(new byte[]{3}, result.get(3, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally { releaseProvider.countDown(); executor.shutdownNow(); }
    }

    @Test
    void identityIncludesUserAndCannotBeReboundToDifferentTextOrSession() {
        AtomicInteger calls = new AtomicInteger();
        TtsRequestRegistry registry = new TtsRequestRegistry(text -> {
            calls.incrementAndGet(); return new byte[]{4};
        }, generousGuard());
        registry.synthesize(1L, "same-session", "same-request", "甲");
        assertThrows(AppException.class, () -> registry.synthesize(1L, "same-session", "same-request", "乙"));
        assertThrows(AppException.class, () -> registry.synthesize(1L, "other-session", "same-request", "甲"));
        registry.synthesize(2L, "same-session", "same-request", "乙");
        registry.cancel(1L, "same-session");
        assertArrayEquals(new byte[]{4}, registry.synthesize(2L, "same-session", "same-request", "乙"));
        assertThrows(AppException.class, () -> registry.synthesize(1L, "same-session", "new-request", "甲"));
        assertEquals(2, calls.get());
    }

    @Test
    void transientFailureAllowsOnlyExplicitCallerRetryWithTheSameIdentity() {
        AtomicInteger calls = new AtomicInteger();
        TtsRequestRegistry registry = new TtsRequestRegistry(text -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("temporary failure");
            return new byte[]{5};
        }, generousGuard());
        assertThrows(IllegalStateException.class, () -> registry.synthesize(1L, "s", "r", "一句"));
        assertEquals(1, calls.get(), "registry must not automatically retry a paid request");
        assertArrayEquals(new byte[]{5}, registry.synthesize(1L, "s", "r", "一句"));
        assertEquals(2, calls.get());
    }

    @Test
    void cancellationBeforeSynthesisClosesTheSessionGate() {
        AtomicInteger calls = new AtomicInteger();
        TtsRequestRegistry registry = new TtsRequestRegistry(text -> {
            calls.incrementAndGet(); return new byte[]{6};
        }, generousGuard());
        registry.cancel(1L, "not-arrived-yet");
        registry.cancel(1L, "not-arrived-yet");
        assertThrows(AppException.class, () -> registry.synthesize(1L, "not-arrived-yet", "r", "一句"));
        assertEquals(0, calls.get());
    }

    @Test
    void fullUnknownCancellationMapCannotPreventStoppingAnActiveUser() throws Exception {
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        TtsService service = new TtsService() {
            public byte[] synthesize(String text) { throw new AssertionError("cancellable path expected"); }
            public byte[] synthesize(String text, TtsCancellation cancellation) {
                try (AutoCloseable ignored = cancellation.onCancel(cancelled::countDown)) {
                    providerEntered.countDown(); awaitLatch(cancelled); cancellation.check();
                    return new byte[]{7};
                } catch (RuntimeException error) { throw error; }
                catch (Exception error) { throw new IllegalStateException(error); }
            }
        };
        TtsRequestRegistry registry = smallRegistry(service, new AtomicLong(), 4, 16, 8, 2);
        registry.cancel(9L, "unknown-a");
        registry.cancel(9L, "unknown-b");
        assertThrows(RateLimitException.class, () -> registry.cancel(9L, "unknown-c"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<byte[]> result = executor.submit(() -> registry.synthesize(1L, "active", "active-1", "在合成"));
            awaitLatch(providerEntered);
            assertDoesNotThrow(() -> registry.cancel(1L, "active"));
            assertThrows(java.util.concurrent.ExecutionException.class, () -> result.get(3, TimeUnit.SECONDS));
            assertThrows(AppException.class, () -> registry.synthesize(1L, "active", "active-2", "迟到的下一句"));
            assertEquals(2, ((Map<?, ?>) field(registry, "cancelledSessions")).size(), "cancellation map stays bounded");
            assertTrue(((Map<?, ?>) field(registry, "requests")).size() <= 4);
            assertEquals(0L, field(registry, "audioBytes"));
        } finally { cancelled.countDown(); executor.shutdownNow(); }
    }

    @Test
    void cacheLimitUsesATombstoneInsteadOfChargingAnEvictedRetryAgain() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TtsRequestRegistry registry = smallRegistry(text -> {
            calls.incrementAndGet(); return new byte[4];
        }, new AtomicLong(), 16, 8, 4, 8);
        registry.synthesize(1L, "s1", "r1", "第一句");
        registry.synthesize(1L, "s1", "r2", "超过用户缓存");
        assertThrows(AppException.class, () -> registry.synthesize(1L, "s1", "r2", "超过用户缓存"));
        registry.synthesize(2L, "s2", "r1", "另一用户");
        registry.synthesize(3L, "s3", "r1", "超过总缓存");
        assertThrows(AppException.class, () -> registry.synthesize(3L, "s3", "r1", "超过总缓存"));
        assertEquals(4, calls.get());
        assertEquals(8L, field(registry, "audioBytes"));
        registry.cancel(1L, "s1");
        assertEquals(4L, field(registry, "audioBytes"));
    }

    @Test
    void cacheLivesForTheFullWindowAfterCompletionAndExpirationIsExplicit() throws Exception {
        AtomicLong clock = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        TtsRequestRegistry registry = smallRegistry(text -> {
            calls.incrementAndGet(); clock.set(90); return new byte[]{8};
        }, clock, 8, 16, 8, 8);
        registry.synthesize(1L, "s", "r", "一句");
        clock.set(180);
        assertArrayEquals(new byte[]{8}, registry.synthesize(1L, "s", "r", "一句"));
        clock.set(191);
        AppException expired = assertThrows(AppException.class, () -> registry.synthesize(1L, "s", "r", "一句"));
        assertTrue(expired.getMessage().contains("过期") || expired.toString().contains("过期"));
        assertEquals(1, calls.get());
        assertEquals(0L, field(registry, "audioBytes"));
    }

    @Test
    void requestEntryCountIsBoundedBeforeCallingProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TtsRequestRegistry registry = smallRegistry(text -> {
            calls.incrementAndGet(); return new byte[]{1};
        }, new AtomicLong(), 2, 16, 8, 8);
        registry.synthesize(1L, "s", "r1", "一");
        registry.synthesize(1L, "s", "r2", "二");
        assertThrows(RateLimitException.class, () -> registry.synthesize(1L, "s", "r3", "三"));
        assertEquals(2, calls.get());
        assertEquals(2, ((Map<?, ?>) field(registry, "requests")).size());
    }

    @Test
    void waiterCannotReturnAnAlreadyCompletedAudioAfterCancellationWins() throws Exception {
        TtsRequestRegistry registry = new TtsRequestRegistry(text -> new byte[]{2}, generousGuard());
        registry.synthesize(1L, "s", "r", "一句");
        Object capturedEntry = ((Map<?, ?>) field(registry, "requests")).values().iterator().next();
        registry.cancel(1L, "s");
        // Models the exact interleaving between reading the entry under lock and await(entry).
        Method await = TtsRequestRegistry.class.getDeclaredMethod("await", capturedEntry.getClass());
        await.setAccessible(true);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> await.invoke(null, capturedEntry));
        assertInstanceOf(AppException.class, failure.getCause());
    }

    @Test
    void cancellationRegistrationAndRemovalDoNotLoseOrRepeatCleanup() throws Exception {
        TtsCancellation cancellation = new TtsCancellation();
        AtomicInteger callbacks = new AtomicInteger();
        Runnable deferred = cancellation.prepareCancel();
        try (AutoCloseable ignored = cancellation.onCancel(callbacks::incrementAndGet)) {
            assertEquals(1, callbacks.get());
            deferred.run(); cancellation.cancel();
            assertEquals(1, callbacks.get());
        }
        TtsCancellation removed = new TtsCancellation();
        AutoCloseable registration = removed.onCancel(callbacks::incrementAndGet);
        registration.close(); removed.cancel();
        assertEquals(1, callbacks.get());
    }

    private static DashScopeTtsService sdkService() {
        return new DashScopeTtsService("test-only-key", "test-model", "test-voice", 600, 5000);
    }

    private static Flowable<SpeechSynthesisResult> withCleanup(Flowable<SpeechSynthesisResult> stream,
                                                              AtomicInteger cleaned) {
        AtomicBoolean ended = new AtomicBoolean();
        io.reactivex.functions.Action cleanup = () -> { if (ended.compareAndSet(false, true)) cleaned.incrementAndGet(); };
        return stream.doOnCancel(cleanup).doOnComplete(cleanup).doOnError(error -> cleanup.run());
    }

    @Test
    void sdkCancellationBeforeStartCreatesNoProviderClient() {
        TtsCancellation cancellation = new TtsCancellation();
        cancellation.cancel();
        try (MockedConstruction<SpeechSynthesizer> clients = mockConstruction(SpeechSynthesizer.class)) {
            assertThrows(AppException.class, () -> sdkService().synthesize("一句", cancellation));
            assertTrue(clients.constructed().isEmpty());
        }
    }

    @Test
    void sdkCancellationDuringFactoryDoesNotSubscribeAfterTheGateClosed() {
        TtsCancellation cancellation = new TtsCancellation();
        AtomicInteger subscriptions = new AtomicInteger();
        try (MockedConstruction<SpeechSynthesizer> ignored = mockConstruction(SpeechSynthesizer.class, (client, context) ->
                when(client.callAsFlowable(anyString())).thenAnswer(invocation -> {
                    cancellation.cancel();
                    return Flowable.defer(() -> { subscriptions.incrementAndGet(); return Flowable.never(); });
                }))) {
            assertThrows(AppException.class, () -> sdkService().synthesize("一句", cancellation));
            assertEquals(0, subscriptions.get());
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    void sdkCancellationRacingOnSubscribeDisposesUpstreamWithoutInterruptingCaller() {
        TtsCancellation cancellation = new TtsCancellation();
        AtomicInteger disposals = new AtomicInteger();
        Flowable<SpeechSynthesisResult> stream = Flowable.<SpeechSynthesisResult>never()
                .doOnCancel(disposals::incrementAndGet)
                .doOnSubscribe(subscription -> cancellation.cancel());
        try (MockedConstruction<SpeechSynthesizer> ignored = mockConstruction(SpeechSynthesizer.class,
                (client, context) -> when(client.callAsFlowable(anyString())).thenReturn(stream))) {
            assertThrows(AppException.class, () -> sdkService().synthesize("一句", cancellation));
            assertEquals(1, disposals.get());
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    void sdkCompletionAndCancellationRaceAlwaysTerminatesAndCleansUp() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            for (int run = 0; run < 8; run++) {
                TtsCancellation cancellation = new TtsCancellation();
                CyclicBarrier race = new CyclicBarrier(2);
                AtomicInteger cleaned = new AtomicInteger();
                SpeechSynthesisResult result = mock(SpeechSynthesisResult.class);
                when(result.getAudioFrame()).thenReturn(ByteBuffer.wrap(new byte[]{1, 2, 3}));
                Flowable<SpeechSynthesisResult> stream = withCleanup(Flowable.<SpeechSynthesisResult>create(emitter -> {
                    emitter.onNext(result); race.await(2, TimeUnit.SECONDS); emitter.onComplete();
                }, io.reactivex.BackpressureStrategy.BUFFER), cleaned);
                Future<?> cancel = executor.submit(() -> {
                    try { race.await(2, TimeUnit.SECONDS); cancellation.cancel(); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                });
                try (MockedConstruction<SpeechSynthesizer> ignored = mockConstruction(SpeechSynthesizer.class,
                        (client, context) -> when(client.callAsFlowable(anyString())).thenReturn(stream))) {
                    try { assertArrayEquals(new byte[]{1, 2, 3}, sdkService().synthesize("一句", cancellation)); }
                    catch (AppException expectedCancellation) { assertTrue(cancellation.isCancelled()); }
                }
                cancel.get(3, TimeUnit.SECONDS);
                assertEquals(1, cleaned.get());
                assertFalse(Thread.currentThread().isInterrupted());
            }
        } finally { executor.shutdownNow(); }
    }

    @Test
    void sdkErrorsReleaseTheSubscriptionAndDoNotExposeProviderDetails() {
        AtomicInteger cleaned = new AtomicInteger();
        Flowable<SpeechSynthesisResult> stream = withCleanup(Flowable.<SpeechSynthesisResult>error(
                new IllegalStateException("private upstream details")), cleaned);
        try (MockedConstruction<SpeechSynthesizer> ignored = mockConstruction(SpeechSynthesizer.class,
                (client, context) -> when(client.callAsFlowable(anyString())).thenReturn(stream))) {
            AppException failure = assertThrows(AppException.class, () -> sdkService().synthesize("一句"));
            assertFalse(failure.getMessage().contains("private upstream details"));
            assertEquals(1, cleaned.get());
        }
    }

    @Test
    void sdkFramesAreConcatenatedOnceAndRespectByteBufferPosition() {
        SpeechSynthesisResult first = mock(SpeechSynthesisResult.class);
        ByteBuffer firstFrame = ByteBuffer.wrap(new byte[]{0, 1, 2});
        firstFrame.position(1);
        when(first.getAudioFrame()).thenReturn(firstFrame);
        SpeechSynthesisResult second = mock(SpeechSynthesisResult.class);
        when(second.getAudioFrame()).thenReturn(ByteBuffer.wrap(new byte[]{3, 4}));
        try (MockedConstruction<SpeechSynthesizer> ignored = mockConstruction(SpeechSynthesizer.class,
                (client, context) -> when(client.callAsFlowable(anyString())).thenReturn(Flowable.just(first, second)))) {
            assertArrayEquals(new byte[]{1, 2, 3, 4}, sdkService().synthesize("一句"));
            assertEquals(1, firstFrame.position(), "provider buffers must not be mutated");
        }
    }

    @Test
    void sdkTimeoutDisposesTheNeverEndingSubscription() {
        AtomicInteger disposals = new AtomicInteger();
        Flowable<SpeechSynthesisResult> stream = Flowable.<SpeechSynthesisResult>never().doOnCancel(disposals::incrementAndGet);
        try (MockedConstruction<SpeechSynthesizer> ignored = mockConstruction(SpeechSynthesizer.class,
                (client, context) -> when(client.callAsFlowable(anyString())).thenReturn(stream))) {
            AppException failure = assertTimeout(java.time.Duration.ofSeconds(8),
                    () -> assertThrows(AppException.class, () -> sdkService().synthesize("一句")));
            assertInstanceOf(java.util.concurrent.TimeoutException.class, failure.getCause());
            assertEquals(1, disposals.get());
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    void sdkWaitPreservesExternalThreadInterruption() {
        AtomicInteger disposals = new AtomicInteger();
        Flowable<SpeechSynthesisResult> stream = Flowable.<SpeechSynthesisResult>never().doOnCancel(disposals::incrementAndGet);
        try (MockedConstruction<SpeechSynthesizer> ignored = mockConstruction(SpeechSynthesizer.class,
                (client, context) -> when(client.callAsFlowable(anyString())).thenReturn(stream))) {
            Thread.currentThread().interrupt();
            assertThrows(AppException.class, () -> sdkService().synthesize("一句"));
            assertTrue(Thread.currentThread().isInterrupted(), "external interruption must not be swallowed");
            assertEquals(1, disposals.get());
        } finally { Thread.interrupted(); }
    }
}
