package com.hzmwmt.domain.service.tts;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisTextType;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DashScopeTtsServiceTest {

    @Test
    void springSelectsTheConfigurationConstructorWhenTestConstructorAlsoExists() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(DashScopeTtsService.class);
            context.refresh();

            assertNotNull(context.getBean(DashScopeTtsService.class));
        }
    }

    @Test
    void synthesizesPlainChineseMp3WithoutExposingProviderOptionsToTheCaller() {
        byte[] expectedAudio = new byte[]{1, 2, 3, 4};
        AtomicReference<SpeechSynthesisParam> capturedParam = new AtomicReference<>();
        AtomicReference<String> capturedText = new AtomicReference<>();
        AtomicLong capturedTimeout = new AtomicLong();
        DashScopeTtsService service = new DashScopeTtsService(
                "server-key",
                "qwen-audio-3.0-tts-flash",
                "longanfengyue",
                600,
                60_000,
                (param, text, timeout) -> {
                    capturedParam.set(param);
                    capturedText.set(text);
                    capturedTimeout.set(timeout);
                    return ByteBuffer.wrap(expectedAudio);
                });

        byte[] actualAudio = service.synthesize("  请注意休息。  ");

        assertArrayEquals(expectedAudio, actualAudio);
        assertEquals("请注意休息。", capturedText.get());
        assertEquals(60_000L, capturedTimeout.get());
        assertEquals("server-key", capturedParam.get().getApiKey());
        assertEquals("qwen-audio-3.0-tts-flash", capturedParam.get().getModel());
        assertEquals("longanfengyue", capturedParam.get().getVoice());
        assertEquals(SpeechSynthesisTextType.PLAIN_TEXT, capturedParam.get().getTextType());
        assertEquals(SpeechSynthesisAudioFormat.MP3_24000HZ_MONO_256KBPS,
                capturedParam.get().getFormat());
        assertEquals(Boolean.TRUE, capturedParam.get().getParameters().get("enable_aigc_tag"));
    }

    @Test
    void rejectsMissingServerConfigurationBeforeCallingProvider() {
        DashScopeTtsService service = new DashScopeTtsService(
                "",
                "qwen-audio-3.0-tts-flash",
                "longanfengyue",
                600,
                60_000,
                (param, text, timeout) -> {
                    throw new AssertionError("provider must not be called");
                });

        AppException error = assertThrows(AppException.class,
                () -> service.synthesize("你好"));

        assertEquals(ResponseCode.SERVICE_ERROR.getCode(), error.getCode());
    }

    @Test
    void rejectsOversizedSegmentsBeforeCallingProvider() {
        DashScopeTtsService service = new DashScopeTtsService(
                "server-key",
                "qwen-audio-3.0-tts-flash",
                "longanfengyue",
                10,
                60_000,
                (param, text, timeout) -> {
                    throw new AssertionError("provider must not be called");
                });

        AppException error = assertThrows(AppException.class,
                () -> service.synthesize("这是一段超过限制的文本"));

        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), error.getCode());
    }

    @Test
    void mapsProviderFailureToStableNonSensitiveError() {
        DashScopeTtsService service = new DashScopeTtsService(
                "server-key",
                "qwen-audio-3.0-tts-flash",
                "longanfengyue",
                600,
                60_000,
                (param, text, timeout) -> {
                    throw new IllegalStateException("provider detail with private text");
                });

        AppException error = assertThrows(AppException.class,
                () -> service.synthesize("私密医疗文本"));

        assertEquals(ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(), error.getCode());
        assertEquals("语音合成服务暂时不可用", error.getInfo());
        assertFalse(error.getInfo().contains("私密"));
        assertFalse(error.getInfo().contains("provider detail"));
    }
}
