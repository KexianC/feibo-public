package com.hzmwmt.domain.service.asr.impl;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.hzmwmt.domain.callback.ASRCallback;
import com.hzmwmt.domain.service.asr.ASRService;
import io.reactivex.processors.UnicastProcessor;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One real supplier stream per recording. The WebSocket session controls
 * graceful completion and bounded cancellation; instances are never shared.
 */
@Service
@Scope("prototype") // 关键：改为多例，因为每个用户的语音流是独立的
public class DashScopeASRService implements ASRService {

    private static final Logger log = LoggerFactory.getLogger(DashScopeASRService.class);
    private static final String MODEL_NAME = "fun-asr-realtime";
    private static final int SAMPLE_RATE = 16000;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Recognition recognizer;
    private Disposable subscription;
    private boolean inputFinished;
    private long generation;
    // 使用 UnicastProcessor 作为桥梁，连接 WebSocket 输入和 DashScope 输出
    private UnicastProcessor<ByteBuffer> audioProcessor;
    @Value("${aliyun.dashscope.apiKey}")
    private String apiKey;

    /**
     * 1. 开启识别任务
     */
    @Override
    public synchronized void start(ASRCallback callback) {
        if (isRunning.get()) {
            return;
        }
        isRunning.set(true);
        inputFinished = false;
        long runGeneration = ++generation;

        try {
            log.info(">>> 初始化语音识别客户端...");
            recognizer = new Recognition();

            // 初始化音频数据处理流（支持背压）
            audioProcessor = UnicastProcessor.create();

            RecognitionParam param = RecognitionParam.builder()
                    .apiKey(apiKey)
                    .model(MODEL_NAME)
                    .format("pcm")
                    .sampleRate(SAMPLE_RATE)
                    .build();

            // 发起流式调用
            // 此时 audioProcessor 还没有数据，等到 websocket receiveAudio 进来后就有数据了
            Disposable started = openRecognition(param, audioProcessor)
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            result -> handleResult(result, callback, runGeneration),
                            error -> {
                                if (!isCurrentRun(runGeneration)) return;
                                log.error("ASR Error: type={}", error.getClass().getSimpleName());
                                completeRun(runGeneration, callback, error instanceof Exception
                                        ? (Exception) error : new IllegalStateException("ASR provider failed"));
                            },
                            () -> {
                                if (!isCurrentRun(runGeneration)) return;
                                log.info("ASR Complete");
                                completeRun(runGeneration, callback, null);
                            }
                    );
            if (isCurrentRun(runGeneration)) subscription = started;
            else started.dispose();

        } catch (Exception e) {
            log.error("语音识别启动失败: type={}", e.getClass().getSimpleName());
            completeRun(runGeneration, callback, e);
        }
    }

    /** Kept overridable so lifecycle tests can use a controlled, non-network upstream. */
    protected Flowable<RecognitionResult> openRecognition(
            RecognitionParam param, Flowable<ByteBuffer> audioFrames) throws Exception {
        return recognizer.streamCall(param, audioFrames);
    }

    /**
     * 2. 接收来自 WebSocket 的音频数据
     * 在 WebSocket 的 onMessage 中调用此方法
     */
    public synchronized void sendAudioFrame(byte[] audioData) {
        if (isRunning.get() && !inputFinished && audioProcessor != null) {
            // 将 byte[] 包装为 ByteBuffer 并推入流中
            //以此触发 streamCall 内部的发送逻辑
            audioProcessor.onNext(ByteBuffer.wrap(audioData));
        }
    }

    /**
     * End the audio input, but keep the result stream alive for the final words.
     * The WebSocket session owns the bounded completion timeout; stop() is the
     * separate immediate-cancel/resource-release path.
     */
    public synchronized void finish() {
        if (!isRunning.get() || inputFinished) return;
        inputFinished = true;
        if (audioProcessor != null) {
            audioProcessor.onComplete();
            audioProcessor = null;
        }
    }

    /**
     * 3. 停止任务
     * 在 WebSocket 的 onClose / onError 中调用
     */
    @Override
    public synchronized void stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return;
        }
        generation++;
        inputFinished = true;

        if (subscription != null) {
            subscription.dispose();
            subscription = null;
        }

        // 告诉 DashScope 音频流结束了
        if (audioProcessor != null) {
            audioProcessor.onComplete();
            audioProcessor = null;
        }

        // 关闭连接
        closeRecognition();
        log.info(">>> 语音识别服务已停止");
    }

    protected void closeRecognition() {
        if (recognizer != null) {
            try {
                if (recognizer.getDuplexApi() != null) {
                    recognizer.getDuplexApi().close(1000, "Bye");
                }
            } catch (Exception e) {
                // ignore
            }
            recognizer = null;
        }
    }

    private synchronized boolean isCurrentRun(long runGeneration) {
        return isRunning.get() && generation == runGeneration;
    }

    private synchronized void completeRun(long runGeneration, ASRCallback callback, Exception error) {
        if (!isCurrentRun(runGeneration)) return;
        // Release this run before notifying consumers. A consumer may start a
        // new recording in its completion callback; never stop that new run.
        stop();
        if (callback == null) return;
        if (error != null) callback.onError(error);
        else callback.onComplete();
    }

    private synchronized void handleResult(RecognitionResult result, ASRCallback callback, long runGeneration) {
        if (!isCurrentRun(runGeneration) || callback == null || result == null
                || result.getSentence() == null || result.getSentence().isHeartbeat()) return;
        String text = result.getSentence().getText();
        if (text == null || text.isBlank()) return;
        if (result.isSentenceEnd()) {
            callback.onFinalResult(text);
        } else {
            callback.onPartialResult(text);
        }
    }
}
