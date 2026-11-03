package com.hzmwmt.infrastructure.gateway.netty.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hzmwmt.domain.callback.ASRCallback;
import com.hzmwmt.domain.service.asr.impl.DashScopeASRService;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class NettyAsrSessionLifecycleTest {
    @Test
    void silentDcAndBriefClickAudioNeverStartsSupplierOrProducesTranscripts() {
        try (Harness h = new Harness()) {
            h.start("quiet-1");
            for (int i = 0; i < 20; i++) h.audio(new byte[640]);
            byte[] dc = new byte[3200];
            for (int i = 0; i < dc.length; i += 2) { dc[i] = 100; dc[i + 1] = 10; }
            h.audio(dc);
            h.audio(voice(20));
            h.audio(new byte[640]);
            h.audio(voice(20));
            h.audio(new byte[640]);
            assertTrue(h.services.isEmpty());
            assertNull(h.channel.readOutbound());
            h.advance(30_000);
            h.event("asr_stopping", "quiet-1", "silence_timeout");
            h.event("asr_complete", "quiet-1", "silence_timeout");
            h.audio(voice(100));
            assertTrue(h.services.isEmpty(), "Late binary must not reopen an identified session");
        }
    }

    @Test
    void sustainedVoiceStartsAfter60msAndReplaysOnlyBoundedPreroll() {
        try (Harness h = new Harness()) {
            h.start("voice-1");
            for (int i = 0; i < 40; i++) h.audio(new byte[640]);
            h.audio(voice(20));
            h.audio(voice(20));
            assertTrue(h.services.isEmpty());
            h.audio(voice(20));
            assertEquals(1, h.services.size());
            assertEquals(9600 + 640, h.current().audioBytes);
            h.current().callback.onPartialResult("我服用二甲双胍");
            assertEquals("我服用二甲双胍", h.event("asr_partial", "voice-1", null).get("content").getAsString());
        }
    }

    @Test
    void normalStopRetainsFinalWordsBeforeCompleteAndDoesNotAcceptMoreAudio() {
        try (Harness h = new Harness()) {
            h.start("finish-1"); h.audio(voice(60));
            FakeAsr service = h.current();
            service.callback.onPartialResult("最近血糖");
            h.event("asr_partial", "finish-1", null);
            h.command("stop_asr", "finish-1");
            h.event("asr_stopping", "finish-1", "stopped");
            assertEquals(1, service.finishCount);
            assertEquals(0, service.stopCount);
            h.audio(voice(60));
            assertEquals(1920, service.audioBytes);
            service.callback.onFinalResult("最近血糖偏高。");
            assertEquals("最近血糖偏高。", h.event("asr_final", "finish-1", null).get("content").getAsString());
            service.callback.onComplete();
            h.event("asr_complete", "finish-1", "stopped");
            assertEquals(1, service.stopCount);
            service.callback.onPartialResult("late");
            assertNull(h.channel.readOutbound());
        }
    }

    @Test
    void supplierFinalTimeoutIsBoundedWithoutInventingFinalText() {
        try (Harness h = new Harness()) {
            h.start("timeout-1"); h.audio(voice(60));
            FakeAsr service = h.current();
            service.callback.onPartialResult("已显示的文字");
            h.event("asr_partial", "timeout-1", null);
            h.command("stop_asr", "timeout-1");
            h.event("asr_stopping", "timeout-1", "stopped");
            h.advance(2999);
            assertNull(h.channel.readOutbound());
            h.advance(1);
            h.event("asr_complete", "timeout-1", "finish_timeout");
            assertEquals(1, service.stopCount);
            service.callback.onFinalResult("too late");
            assertNull(h.channel.readOutbound());
        }
    }

    @Test
    void replacementCancellationAndOldCallbacksCannotChangeNewSession() {
        try (Harness h = new Harness()) {
            h.start("old-1"); h.audio(voice(60));
            FakeAsr old = h.current();
            h.command("start_asr", "new-1");
            h.event("asr_complete", "old-1", "replaced");
            h.event("asr_started", "new-1", null);
            h.audio(voice(60));
            FakeAsr current = h.current();
            h.command("cancel_asr", "old-1");
            h.event("asr_complete", "old-1", "not_active");
            h.command("stop_asr", null);
            h.event("asr_complete", null, "not_active");
            old.callback.onPartialResult("stale partial");
            old.callback.onFinalResult("stale final");
            old.callback.onError(new IllegalStateException("stale error"));
            old.callback.onComplete();
            assertNull(h.channel.readOutbound());
            assertEquals(1, old.stopCount);
            assertEquals(0, current.stopCount);
            current.callback.onFinalResult("当前会话");
            h.event("asr_final", "new-1", null);
            h.command("cancel_asr", "new-1");
            h.event("asr_complete", "new-1", "cancelled");
            assertEquals(0, current.finishCount);
            assertEquals(1, current.stopCount);
        }
    }

    @Test
    void closedSessionIdIsRejectedAndEveryErrorIsCorrelated() {
        try (Harness h = new Harness()) {
            h.start("id-1");
            h.command("cancel_asr", "id-1"); h.event("asr_complete", "id-1", "cancelled");
            h.command("start_asr", "id-1");
            h.event("asr_error", "id-1", "session_closed");
            h.event("asr_complete", "id-1", "session_closed");
            h.start("id-2"); h.audio(voice(60));
            h.current().callback.onError(new IllegalArgumentException("provider secret"));
            JsonObject error = h.event("asr_error", "id-2", "provider_error");
            assertFalse(error.toString().contains("provider secret"));
            h.event("asr_complete", "id-2", "provider_error");
        }
    }

    @Test
    void transcriptsAndClientActivityClaimsCannotRenewServerSilenceTimer() {
        try (Harness h = new Harness()) {
            h.start("silent-after-voice"); h.audio(voice(60));
            h.advance(20_000);
            h.current().callback.onPartialResult("this is not PCM activity");
            h.event("asr_partial", "silent-after-voice", null);
            h.command("asr_activity", "silent-after-voice");
            h.audio(new byte[640]);
            h.advance(10_000);
            h.event("asr_stopping", "silent-after-voice", "silence_timeout");
            assertEquals(1, h.current().finishCount);
            h.current().callback.onComplete();
            h.event("asr_complete", "silent-after-voice", "silence_timeout");
        }
    }

    @Test
    void continuousVoiceNeverExtendsFiveMinuteDeadline() {
        try (Harness h = new Harness()) {
            h.start("long-1"); h.audio(voice(60));
            for (int i = 0; i < 14; i++) { h.advance(20_000); h.audio(voice(60)); }
            assertNull(h.channel.readOutbound());
            h.advance(20_000);
            h.event("asr_stopping", "long-1", "max_duration");
            assertEquals(1, h.current().finishCount);
            h.advance(3_000);
            h.event("asr_complete", "long-1", "finish_timeout");
            assertEquals(1, h.current().stopCount);
        }
    }

    @Test
    void incomingAudioCannotRaceDelayedHardDeadlineTask() {
        try (Harness h = new Harness()) {
            h.start("delayed-1"); h.audio(voice(60));
            // The server event loop has not yet had a chance to run scheduled tasks.
            h.now.set(TimeUnit.MINUTES.toNanos(5));
            h.audio(voice(60));
            h.event("asr_stopping", "delayed-1", "max_duration");
            assertEquals(1920, h.current().audioBytes);
        }
    }

    @Test
    void pcmDurationBudgetAlsoStopsAudioSentFasterThanRealtime() {
        try (Harness h = new Harness()) {
            h.start("fast-audio-1"); h.audio(voice(60));
            for (int i = 0; i < 147; i++) h.audio(new byte[64 * 1024]);
            h.event("asr_stopping", "fast-audio-1", "max_duration");
            assertTrue(h.current().audioBytes <= 16_000 * 2 * 300);
            assertEquals(1, h.current().finishCount);
            h.current().callback.onComplete();
            h.event("asr_complete", "fast-audio-1", "max_duration");
        }
    }

    @Test
    void cancelledTimersCannotCloseAReplacementRecording() {
        try (Harness h = new Harness()) {
            h.start("timer-old"); h.audio(voice(60)); h.advance(29_000);
            h.command("start_asr", "timer-new");
            h.event("asr_complete", "timer-old", "replaced");
            h.event("asr_started", "timer-new", null); h.audio(voice(60));
            h.advance(1_000);
            assertNull(h.channel.readOutbound());
            assertEquals(0, h.current().stopCount);
            h.current().callback.onFinalResult("新的录音仍在进行");
            h.event("asr_final", "timer-new", null);
        }
    }

    @Test
    void invalidPcmDoesNotReachSupplierAndReleasesSession() {
        try (Harness h = new Harness()) {
            h.start("invalid-1"); h.audio(new byte[3]);
            h.event("asr_error", "invalid-1", "invalid_audio");
            h.event("asr_complete", "invalid-1", "invalid_audio");
            assertTrue(h.services.isEmpty());
        }
    }

    @Test
    void disconnectCancelsAllTimersAndDropsLateProviderResults() {
        try (Harness h = new Harness()) {
            h.start("disconnect-1"); h.audio(voice(60));
            FakeAsr service = h.current();
            h.channel.close();
            assertEquals(1, service.stopCount);
            service.callback.onFinalResult("late after disconnect");
            h.advance(400_000);
            assertNull(h.channel.readOutbound());
            assertEquals(1, service.stopCount);
        }
    }

    @Test
    void legacyClientStillStartsOnAudioAndReceivesFinalBeforeComplete() {
        try (Harness h = new Harness()) {
            h.audio(voice(60));
            h.command("stop_asr", null);
            assertNull(h.channel.readOutbound());
            h.current().callback.onFinalResult("旧客户端尾句");
            h.event("asr_final", null, null);
            h.current().callback.onComplete();
            h.event("asr_complete", null, "stopped");
            h.audio(voice(60));
            assertEquals(2, h.services.size());
        }
    }

    @Test
    void legacySilenceLimitBlocksContinuousPcmUntilAStopBoundary() {
        try (Harness h = new Harness()) {
            h.audio(new byte[640]);
            h.advance(30_000);
            h.event("asr_complete", null, "silence_timeout");
            for (int i = 0; i < 20; i++) { h.audio(voice(60)); h.advance(100); }
            assertTrue(h.services.isEmpty(), "Continuous legacy PCM must not bypass the server silence limit");
            assertNull(h.channel.readOutbound());
            h.command("stop_asr", null);
            h.event("asr_complete", null, "not_active");
            h.audio(voice(60));
            assertEquals(1, h.services.size(), "The legacy stop handshake allows the next recording");
        }
    }

    @Test
    void legacyFiveMinuteLimitDoesNotReopenOnMoreBinaryFrames() {
        try (Harness h = new Harness()) {
            h.audio(voice(60));
            for (int i = 0; i < 14; i++) { h.advance(20_000); h.audio(voice(60)); }
            h.advance(20_000);
            FakeAsr previous = h.current();
            assertEquals(1, previous.finishCount);
            previous.callback.onComplete();
            h.event("asr_complete", null, "max_duration");
            for (int i = 0; i < 20; i++) { h.audio(voice(60)); h.advance(100); }
            assertEquals(1, h.services.size());
            h.command("stop_asr", "unrelated-session");
            h.event("asr_complete", "unrelated-session", "not_active");
            h.audio(voice(60));
            assertEquals(1, h.services.size(), "An unrelated stop cannot unlock a legacy recording");
            h.command("stop_asr", null);
            h.event("asr_complete", null, "not_active");
            h.audio(voice(60));
            assertEquals(2, h.services.size());
        }
    }

    @Test
    void legacyClientTimeoutStopDuringServerFinishAllowsItsNextRecording() {
        try (Harness h = new Harness()) {
            h.audio(voice(60)); h.advance(30_000);
            assertEquals(1, h.current().finishCount);
            // Existing pages already send this when their own 30 s/5 min
            // timer fires, possibly while the server is preserving final words.
            h.command("stop_asr", null);
            assertNull(h.channel.readOutbound());
            h.current().callback.onFinalResult("旧页面尾句");
            h.event("asr_final", null, null);
            h.current().callback.onComplete();
            h.event("asr_complete", null, "silence_timeout");
            h.audio(voice(60));
            assertEquals(2, h.services.size());
        }
    }

    @Test
    void legacyPcmDurationLimitStaysLockedAfterFinishTimeoutButExplicitCancelRecovers() {
        try (Harness h = new Harness()) {
            h.audio(voice(60));
            for (int i = 0; i < 147; i++) h.audio(new byte[64 * 1024]);
            assertEquals(1, h.current().finishCount);
            h.advance(3_000);
            h.event("asr_complete", null, "finish_timeout");
            h.audio(voice(60));
            assertEquals(1, h.services.size());
            h.command("cancel_asr", null);
            h.event("asr_complete", null, "not_active");
            h.audio(voice(60));
            assertEquals(2, h.services.size());
        }
    }

    private static byte[] voice(int milliseconds) {
        int samples = milliseconds * 16;
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            short sample = (short) (1200 * Math.sin(i * 2 * Math.PI * 300 / 16000));
            pcm[i * 2] = (byte) sample;
            pcm[i * 2 + 1] = (byte) (sample >>> 8);
        }
        return pcm;
    }

    private static final class Harness implements AutoCloseable {
        final AtomicLong now = new AtomicLong();
        final List<FakeAsr> services = new ArrayList<>();
        final EmbeddedChannel channel;

        Harness() {
            ApplicationContext context = (ApplicationContext) Proxy.newProxyInstance(
                    ApplicationContext.class.getClassLoader(), new Class<?>[]{ApplicationContext.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getBean") && args[0] == DashScopeASRService.class) {
                            FakeAsr service = new FakeAsr(); services.add(service); return service;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
            channel = new EmbeddedChannel(new NettyServerHandler(context, now::get));
            channel.attr(WebSocketHandshakeAuthHandler.AUTHENTICATED_USER_ID).set(42L);
        }

        void start(String id) { command("start_asr", id); event("asr_started", id, null); }
        void command(String type, String id) {
            JsonObject payload = new JsonObject(); payload.addProperty("type", type);
            if (id != null) payload.addProperty("sessionId", id);
            channel.writeInbound(new TextWebSocketFrame(payload.toString()));
        }
        void audio(byte[] pcm) { channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(pcm))); }
        FakeAsr current() { return services.get(services.size() - 1); }
        void advance(long ms) {
            now.addAndGet(TimeUnit.MILLISECONDS.toNanos(ms));
            channel.advanceTimeBy(ms, TimeUnit.MILLISECONDS);
            channel.runScheduledPendingTasks(); channel.runPendingTasks();
        }
        JsonObject event(String type, String id, String reason) {
            TextWebSocketFrame frame = channel.readOutbound(); assertNotNull(frame, "Missing " + type);
            JsonObject payload;
            try { payload = JsonParser.parseString(frame.text()).getAsJsonObject(); }
            finally { frame.release(); }
            assertEquals(type, payload.get("type").getAsString());
            assertEquals(id, payload.has("sessionId") ? payload.get("sessionId").getAsString() : null);
            assertEquals(reason, payload.has("reason") ? payload.get("reason").getAsString() : null);
            return payload;
        }
        @Override public void close() { channel.finishAndReleaseAll(); }
    }

    private static final class FakeAsr extends DashScopeASRService {
        ASRCallback callback; int stopCount; int finishCount; int audioBytes;
        @Override public synchronized void start(ASRCallback callback) { this.callback = callback; }
        @Override public synchronized void finish() { finishCount++; }
        @Override public synchronized void stop() { stopCount++; }
        @Override public void sendAudioFrame(byte[] frame) { audioBytes += frame.length; }
    }
}
