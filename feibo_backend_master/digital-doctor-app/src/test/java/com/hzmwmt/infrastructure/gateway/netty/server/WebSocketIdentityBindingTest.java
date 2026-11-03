package com.hzmwmt.infrastructure.gateway.netty.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hzmwmt.domain.callback.ASRCallback;
import com.hzmwmt.domain.service.asr.impl.DashScopeASRService;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class WebSocketIdentityBindingTest {

    private static final String ASR_EXCEPTION_TO_STRING_SECRET = "asr-exception-toString-secret";

    @Test
    void legacyLoginFrameCannotReplaceHandshakeIdentity() {
        ApplicationContext applicationContext = stubContext(null).applicationContext();
        EmbeddedChannel channel = new EmbeddedChannel(new NettyServerHandler(applicationContext));
        channel.attr(WebSocketHandshakeAuthHandler.AUTHENTICATED_USER_ID).set(42L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"login\",\"userId\":99}"));

        CloseWebSocketFrame close = channel.readOutbound();
        assertEquals(1008, close.statusCode());
        close.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void matchingLegacyLoginStillReturnsJsonAcknowledgement() {
        ApplicationContext applicationContext = stubContext(null).applicationContext();
        EmbeddedChannel channel = new EmbeddedChannel(new NettyServerHandler(applicationContext));
        channel.attr(WebSocketHandshakeAuthHandler.AUTHENTICATED_USER_ID).set(42L);

        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"login\",\"userId\":42}"));

        TextWebSocketFrame response = channel.readOutbound();
        JsonObject payload = JsonParser.parseString(response.text()).getAsJsonObject();
        assertEquals("login", payload.get("type").getAsString());
        assertEquals("authenticated", payload.get("content").getAsString());
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void unauthenticatedAudioCannotStartAsr() {
        StubContext stubContext = stubContext(new RecordingAsrService());
        EmbeddedChannel channel = new EmbeddedChannel(
                new NettyServerHandler(stubContext.applicationContext()));

        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{1, 2, 3})));

        CloseWebSocketFrame close = channel.readOutbound();
        assertEquals(1008, close.statusCode());
        close.release();
        assertEquals(0, stubContext.beanLookupCount().get());
        channel.finishAndReleaseAll();
    }

    @Test
    void asrErrorReturnsGenericMessageWithoutLeakingProviderDetails(CapturedOutput output) {
        AsrSession session = startAsr();
        EmbeddedChannel channel = session.channel();
        String sensitiveMessage = "provider request failed: apiKey=secret-key; endpoint=http://internal-asr";

        session.callback().onError(new SensitiveAsrException(sensitiveMessage));

        TextWebSocketFrame response = channel.readOutbound();
        JsonObject payload = JsonParser.parseString(response.text()).getAsJsonObject();
        assertEquals("asr_error", payload.get("type").getAsString());
        assertEquals("\u8bed\u97f3\u8bc6\u522b\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5", payload.get("content").getAsString());
        assertFalse(response.text().contains(sensitiveMessage));
        String logs = output.getAll();
        assertTrue(logs.contains("type=SensitiveAsrException"));
        assertFalse(logs.contains(sensitiveMessage));
        assertFalse(logs.contains(ASR_EXCEPTION_TO_STRING_SECRET));
        assertFalse(logs.contains("\tat "));
        response.release();

        TextWebSocketFrame complete = channel.readOutbound();
        JsonObject completePayload = JsonParser.parseString(complete.text()).getAsJsonObject();
        assertEquals("asr_complete", completePayload.get("type").getAsString());
        complete.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void asrResultUsesJsonSerializationForQuotesBackslashesAndControlCharacters() {
        AsrSession session = startAsr();
        EmbeddedChannel channel = session.channel();
        String untrustedText = "backslash\\ quote\" CR\rLF\nTAB\tBACKSPACE\bFORMFEED\f"
                + (char) 0 + (char) 1 + "\",\"type\":\"forged\",\"content\":\"pwned";

        session.callback().onPartialResult(untrustedText);

        TextWebSocketFrame response = channel.readOutbound();
        JsonObject payload = JsonParser.parseString(response.text()).getAsJsonObject();
        assertEquals(2, payload.size());
        assertEquals("asr_partial", payload.get("type").getAsString());
        assertEquals(untrustedText, payload.get("content").getAsString());
        assertFalse(response.text().chars().anyMatch(character -> character < 0x20));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void stopAsrReleasesCurrentServiceAndAllowsAnotherRecordingOnSameConnection() {
        RecordingAsrService first = new RecordingAsrService();
        RecordingAsrService second = new RecordingAsrService();
        StubContext stubContext = stubContextSequence(first, second);
        EmbeddedChannel channel = authenticatedChannel(stubContext);

        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(voicedPcm())));
        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"stop_asr\"}"));
        assertEquals(1, first.finishCount);
        assertEquals(0, first.stopCount);
        first.callback.onComplete();

        TextWebSocketFrame complete = channel.readOutbound();
        assertEquals("asr_complete", frameType(complete));
        complete.release();
        assertEquals(1, first.stopCount);

        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(voicedPcm())));

        assertEquals(2, stubContext.beanLookupCount().get());
        assertEquals(1, first.framesSent);
        assertEquals(1, second.framesSent);
        assertEquals(0, second.stopCount);
        channel.finishAndReleaseAll();
    }

    @Test
    void providerCompletionReleasesSessionAndAllowsAnotherRecording() {
        RecordingAsrService first = new RecordingAsrService();
        RecordingAsrService second = new RecordingAsrService();
        StubContext stubContext = stubContextSequence(first, second);
        EmbeddedChannel channel = authenticatedChannel(stubContext);

        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(voicedPcm())));
        first.callback.onComplete();

        TextWebSocketFrame complete = channel.readOutbound();
        assertEquals("asr_complete", frameType(complete));
        complete.release();
        assertEquals(1, first.stopCount);

        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(voicedPcm())));

        assertEquals(2, stubContext.beanLookupCount().get());
        assertEquals(1, second.framesSent);
        channel.finishAndReleaseAll();
    }

    @Test
    void providerErrorReleasesSessionAndAllowsAnotherRecording() {
        RecordingAsrService first = new RecordingAsrService();
        RecordingAsrService second = new RecordingAsrService();
        StubContext stubContext = stubContextSequence(first, second);
        EmbeddedChannel channel = authenticatedChannel(stubContext);

        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(voicedPcm())));
        first.callback.onError(new IllegalStateException("provider failed"));

        TextWebSocketFrame error = channel.readOutbound();
        TextWebSocketFrame complete = channel.readOutbound();
        assertEquals("asr_error", frameType(error));
        assertEquals("asr_complete", frameType(complete));
        error.release();
        complete.release();
        assertEquals(1, first.stopCount);

        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(voicedPcm())));

        assertEquals(2, stubContext.beanLookupCount().get());
        assertEquals(1, second.framesSent);
        channel.finishAndReleaseAll();
    }

    @Test
    void staleProviderCallbackCannotReleaseOrNotifyOverNewRecording() {
        RecordingAsrService first = new RecordingAsrService();
        RecordingAsrService second = new RecordingAsrService();
        StubContext stubContext = stubContextSequence(first, second);
        EmbeddedChannel channel = authenticatedChannel(stubContext);

        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(voicedPcm())));
        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"cancel_asr\"}"));
        TextWebSocketFrame stopComplete = channel.readOutbound();
        stopComplete.release();
        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(voicedPcm())));

        first.callback.onError(new IllegalStateException("late provider error"));
        first.callback.onComplete();
        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(voicedPcm())));

        assertNull(channel.readOutbound());
        assertEquals(2, stubContext.beanLookupCount().get());
        assertEquals(0, second.stopCount);
        assertEquals(2, second.framesSent);
        channel.finishAndReleaseAll();
    }

    @Test
    void websocketExceptionStopsAndClearsCurrentAsrService() {
        RecordingAsrService asrService = new RecordingAsrService();
        StubContext stubContext = stubContext(asrService);
        EmbeddedChannel channel = authenticatedChannel(stubContext);
        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(voicedPcm())));

        channel.pipeline().fireExceptionCaught(new IllegalStateException("socket failed"));
        channel.runPendingTasks();

        assertEquals(1, asrService.stopCount);
        assertFalse(channel.isActive());
        channel.finishAndReleaseAll();
    }

    private AsrSession startAsr() {
        RecordingAsrService asrService = new RecordingAsrService();
        StubContext stubContext = stubContext(asrService);
        EmbeddedChannel channel = authenticatedChannel(stubContext);
        channel.writeInbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(voicedPcm())));
        assertEquals(1, stubContext.beanLookupCount().get());
        assertEquals(1, asrService.framesSent);
        assertNotNull(asrService.callback);
        return new AsrSession(channel, asrService.callback);
    }

    private EmbeddedChannel authenticatedChannel(StubContext stubContext) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new NettyServerHandler(stubContext.applicationContext()));
        channel.attr(WebSocketHandshakeAuthHandler.AUTHENTICATED_USER_ID).set(42L);
        return channel;
    }

    private String frameType(TextWebSocketFrame frame) {
        return JsonParser.parseString(frame.text())
                .getAsJsonObject()
                .get("type")
                .getAsString();
    }

    private StubContext stubContext(DashScopeASRService asrService) {
        return stubContextSequence(asrService);
    }

    private StubContext stubContextSequence(DashScopeASRService... asrServices) {
        AtomicInteger beanLookupCount = new AtomicInteger();
        ApplicationContext applicationContext = (ApplicationContext) Proxy.newProxyInstance(
                ApplicationContext.class.getClassLoader(),
                new Class<?>[]{ApplicationContext.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "StubApplicationContext";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> throw new UnsupportedOperationException(method.toString());
                        };
                    }
                    if (method.getName().equals("getBean")
                            && args != null
                            && args.length == 1
                            && args[0] == DashScopeASRService.class) {
                        int lookupIndex = beanLookupCount.getAndIncrement();
                        if (lookupIndex >= asrServices.length) {
                            throw new IllegalStateException("Unexpected ASR service lookup");
                        }
                        return asrServices[lookupIndex];
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        return new StubContext(applicationContext, beanLookupCount);
    }

    private record AsrSession(EmbeddedChannel channel, ASRCallback callback) {
    }

    private static byte[] voicedPcm() {
        byte[] pcm = new byte[960 * 2];
        for (int index = 0; index < 960; index++) {
            short sample = (short) (1200 * Math.sin(index * 2 * Math.PI * 300 / 16000));
            pcm[index * 2] = (byte) sample;
            pcm[index * 2 + 1] = (byte) (sample >>> 8);
        }
        return pcm;
    }

    private record StubContext(
            ApplicationContext applicationContext,
            AtomicInteger beanLookupCount) {
    }

    private static final class RecordingAsrService extends DashScopeASRService {
        private ASRCallback callback;
        private int framesSent;
        private int stopCount;
        private int finishCount;

        @Override
        public synchronized void start(ASRCallback callback) {
            this.callback = callback;
        }

        @Override
        public void sendAudioFrame(byte[] audioData) {
            framesSent++;
        }

        @Override
        public synchronized void stop() {
            stopCount++;
        }

        @Override
        public synchronized void finish() {
            finishCount++;
        }
    }

    private static final class SensitiveAsrException extends Exception {
        private SensitiveAsrException(String message) {
            super(message);
        }

        @Override
        public String toString() {
            return ASR_EXCEPTION_TO_STRING_SECRET;
        }
    }
}
