package com.hzmwmt.domain.service.asr.impl;

import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.audio.asr.recognition.timestamp.Sentence;
import com.hzmwmt.domain.callback.ASRCallback;
import io.reactivex.Flowable;
import io.reactivex.processors.PublishProcessor;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DashScopeAsrLifecycleTest {
    @Test
    void finishCompletesInputButKeepsSupplierOpenForFinalThenReleasesResources() throws Exception {
        ControlledService service = new ControlledService();
        RecordingCallback callback = new RecordingCallback();
        service.start(callback);
        assertTrue(service.subscribed.await(2, TimeUnit.SECONDS));
        service.sendAudioFrame(new byte[640]);
        service.results.onNext(result("最近血", false, false));
        service.finish();
        assertEquals(1, service.inputCompletions.get());
        assertEquals(0, service.closeCount.get(), "Ending audio must not close the result stream");
        assertTrue(service.results.hasSubscribers());
        service.sendAudioFrame(new byte[640]);
        assertEquals(640, service.inputBytes.get());
        service.results.onNext(result("最近血糖偏高。", true, false));
        service.results.onComplete();
        assertEquals(List.of("partial:最近血", "final:最近血糖偏高。", "complete"), callback.events);
        assertEquals(1, service.closeCount.get());
        service.stop();
        assertEquals(1, service.closeCount.get());
    }

    @Test
    void immediateCancelDisposesResultSubscriptionAndDropsLateWords() throws Exception {
        ControlledService service = new ControlledService();
        RecordingCallback callback = new RecordingCallback();
        service.start(callback);
        assertTrue(service.subscribed.await(2, TimeUnit.SECONDS));
        service.stop();
        assertFalse(service.results.hasSubscribers());
        service.results.onNext(result("late response", true, false));
        service.results.onComplete();
        assertTrue(callback.events.isEmpty());
        assertEquals(1, service.closeCount.get());
        assertEquals(1, service.inputCompletions.get());
    }

    @Test
    void heartbeatAndEmptyEventsDoNotBecomeTextButLegitimatePhrasesAreNotBlacklisted() throws Exception {
        ControlledService service = new ControlledService();
        RecordingCallback callback = new RecordingCallback();
        service.start(callback);
        assertTrue(service.subscribed.await(2, TimeUnit.SECONDS));
        service.results.onNext(result("ignored heartbeat", false, true));
        service.results.onNext(result("  ", true, false));
        service.results.onNext(new RecognitionResult());
        service.results.onNext(result("谢谢，医生，我需要复查甲状腺。", true, false));
        assertEquals(List.of("final:谢谢，医生，我需要复查甲状腺。"), callback.events);
        service.stop();
    }

    @Test
    void supplierFailureSignalsErrorAndClosesExactlyOnce() throws Exception {
        ControlledService service = new ControlledService();
        RecordingCallback callback = new RecordingCallback();
        service.start(callback);
        assertTrue(service.subscribed.await(2, TimeUnit.SECONDS));
        service.results.onError(new IllegalStateException("controlled upstream failed"));
        assertEquals(List.of("error"), callback.events);
        assertEquals(1, service.closeCount.get());
        service.stop();
        assertEquals(1, service.closeCount.get());
    }

    private static RecognitionResult result(String text, boolean finished, boolean heartbeat) {
        Sentence sentence = new Sentence();
        sentence.setText(text); sentence.setSentenceEnd(finished); sentence.setHeartbeat(heartbeat);
        RecognitionResult result = new RecognitionResult(); result.setSentence(sentence); return result;
    }

    private static final class ControlledService extends DashScopeASRService {
        final PublishProcessor<RecognitionResult> results = PublishProcessor.create();
        final AtomicInteger inputBytes = new AtomicInteger();
        final AtomicInteger inputCompletions = new AtomicInteger();
        final AtomicInteger closeCount = new AtomicInteger();
        final CountDownLatch subscribed = new CountDownLatch(1);

        @Override protected Flowable<RecognitionResult> openRecognition(
                RecognitionParam param, Flowable<ByteBuffer> audioFrames) {
            assertEquals("fun-asr-realtime", param.getModel());
            assertEquals("pcm", param.getFormat());
            assertEquals(16000, param.getSampleRate());
            audioFrames.subscribe(frame -> inputBytes.addAndGet(frame.remaining()),
                    error -> fail(error), inputCompletions::incrementAndGet);
            return results.doOnSubscribe(ignored -> subscribed.countDown());
        }
        @Override protected void closeRecognition() { closeCount.incrementAndGet(); super.closeRecognition(); }
    }

    private static final class RecordingCallback implements ASRCallback {
        final List<String> events = new ArrayList<>();
        @Override public void onPartialResult(String text) { events.add("partial:" + text); }
        @Override public void onFinalResult(String text) { events.add("final:" + text); }
        @Override public void onError(Exception error) { events.add("error"); }
        @Override public void onComplete() { events.add("complete"); }
    }
}
