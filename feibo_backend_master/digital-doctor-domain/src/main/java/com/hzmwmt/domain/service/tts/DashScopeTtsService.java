package com.hzmwmt.domain.service.tts;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisTextType;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import io.reactivex.Flowable;
import io.reactivex.subscribers.DisposableSubscriber;

/**
 * Alibaba Cloud Model Studio speech synthesis for Zhibi Yiyan.
 *
 * <p>A new SDK synthesizer is created for every request because the DashScope
 * client is stateful. The API key and provider options always stay on the
 * server; neither answer text nor audio is logged or persisted here.</p>
 */
@Slf4j
@Service
public class DashScopeTtsService implements TtsService {

    static final int MAX_TEXT_LENGTH = 600;
    private static final int MAX_AUDIO_BYTES = 16 * 1024 * 1024;
    private static final long MIN_TIMEOUT_MILLIS = 5_000L;
    private static final long MAX_TIMEOUT_MILLIS = 120_000L;

    private final String apiKey;
    private final String model;
    private final String voice;
    private final int maxTextLength;
    private final long timeoutMillis;
    private final SynthesisClient synthesisClient;

    @Autowired
    public DashScopeTtsService(
            @Value("${aliyun.dashscope.apiKey:}") String apiKey,
            @Value("${aliyun.dashscope.tts.model:qwen-audio-3.0-tts-flash}") String model,
            @Value("${aliyun.dashscope.tts.voice:longanfengyue}") String voice,
            @Value("${aliyun.dashscope.tts.max-text-length:600}") int maxTextLength,
            @Value("${aliyun.dashscope.tts.timeout-millis:60000}") long timeoutMillis) {
        this(apiKey, model, voice, maxTextLength, timeoutMillis,
                null);
    }

    DashScopeTtsService(
            String apiKey,
            String model,
            String voice,
            int maxTextLength,
            long timeoutMillis,
            SynthesisClient synthesisClient) {
        this.apiKey = normalize(apiKey);
        this.model = normalize(model);
        this.voice = normalize(voice);
        this.maxTextLength = Math.min(Math.max(maxTextLength, 1), MAX_TEXT_LENGTH);
        this.timeoutMillis = Math.min(
                Math.max(timeoutMillis, MIN_TIMEOUT_MILLIS),
                MAX_TIMEOUT_MILLIS);
        this.synthesisClient = synthesisClient;
    }

    @Override
    public byte[] synthesize(String text) {
        return synthesize(text, new TtsCancellation());
    }

    @Override
    public byte[] synthesize(String text, TtsCancellation cancellation) {
        cancellation.check();
        String normalizedText = normalize(text);
        validateConfiguration();
        if (!StringUtils.hasText(normalizedText)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "待朗读文本不能为空");
        }
        if (normalizedText.length() > maxTextLength) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "单次待朗读文本超出长度限制");
        }

        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(model)
                .voice(voice)
                .textType(SpeechSynthesisTextType.PLAIN_TEXT)
                .format(SpeechSynthesisAudioFormat.MP3_24000HZ_MONO_256KBPS)
                .languageHints(List.of("zh"))
                .volume(50)
                .speechRate(1.0f)
                .pitchRate(1.0f)
                .parameter("enable_aigc_tag", true)
                .build();

        try {
            ByteBuffer audioBuffer = synthesisClient != null
                    ? synthesisClient.synthesize(param, normalizedText, timeoutMillis)
                    : synthesizeWithSdk(param, normalizedText, timeoutMillis, cancellation);
            cancellation.check();
            byte[] audio = copyAudio(audioBuffer);
            if (audio.length == 0 || audio.length > MAX_AUDIO_BYTES) {
                throw new IllegalStateException("invalid audio payload size");
            }
            return audio;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            cancellation.check();
            // Provider errors may contain request details. Log only the type and
            // return a stable, non-sensitive message to the browser.
            log.warn("DashScope TTS request failed: type={}",
                    exception.getClass().getSimpleName());
            throw new AppException(
                    ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(),
                    "语音合成服务暂时不可用",
                    exception);
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(apiKey)
                || !StringUtils.hasText(model)
                || !StringUtils.hasText(voice)) {
            throw new AppException(
                    ResponseCode.SERVICE_ERROR.getCode(),
                    "语音合成服务未配置");
        }
    }

    private static ByteBuffer synthesizeWithSdk(
            SpeechSynthesisParam param,
            String text,
            long timeoutMillis,
            TtsCancellation cancellation) throws Exception {
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
        CompletableFuture<ByteBuffer> completed = new CompletableFuture<>();
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        DisposableSubscriber<SpeechSynthesisResult> subscriber = new DisposableSubscriber<>() {
            @Override
            public void onNext(SpeechSynthesisResult result) {
                if (cancellation.isCancelled()) { dispose(); return; }
                ByteBuffer frame = result.getAudioFrame();
                if (frame == null) return;
                ByteBuffer readable = frame.asReadOnlyBuffer();
                synchronized (audio) {
                    if ((long) audio.size() + readable.remaining() > MAX_AUDIO_BYTES) {
                        completed.completeExceptionally(new IllegalStateException("invalid audio payload size"));
                        dispose();
                        return;
                    }
                    byte[] bytes = new byte[readable.remaining()];
                    readable.get(bytes);
                    audio.write(bytes, 0, bytes.length);
                }
            }

            @Override
            public void onError(Throwable failure) { completed.completeExceptionally(failure); }

            @Override
            public void onComplete() {
                synchronized (audio) { completed.complete(ByteBuffer.wrap(audio.toByteArray())); }
            }
        };
        // The synchronous SDK call swallows InterruptedException internally. A
        // disposable subscription lets cancellation settle our bounded wait
        // without interrupting (or clearing interruption on) a servlet pool thread.
        try (AutoCloseable ignored = cancellation.onCancel(() -> {
            completed.completeExceptionally(new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(), "本次语音播报已取消"));
            subscriber.dispose();
            closeSynthesizer(synthesizer, "cancelled");
        })) {
            cancellation.check();
            Flowable<SpeechSynthesisResult> stream = synthesizer.callAsFlowable(text);
            cancellation.check();
            stream.subscribe(subscriber);
            cancellation.check();
            try {
                return completed.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            } catch (ExecutionException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("upstream speech synthesis failed", failure.getCause());
            }
        } finally {
            subscriber.dispose();
            closeSynthesizer(synthesizer, "complete");
        }
    }

    private static void closeSynthesizer(SpeechSynthesizer synthesizer, String reason) {
        try {
            if (synthesizer.getDuplexApi() != null) synthesizer.getDuplexApi().close(1000, reason);
        } catch (Exception ignored) { /* Preserve the original result or cancellation. */ }
    }

    private static byte[] copyAudio(ByteBuffer source) {
        if (source == null) {
            return new byte[0];
        }
        ByteBuffer readable = source.asReadOnlyBuffer();
        readable.rewind();
        byte[] audio = new byte[readable.remaining()];
        readable.get(audio);
        return audio;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    interface SynthesisClient {
        ByteBuffer synthesize(SpeechSynthesisParam param, String text, long timeoutMillis);
    }
}
