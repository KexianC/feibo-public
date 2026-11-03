package com.hzmwmt.domain.digital.service.work;

import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoCompositionPayloadValidatorTest {

    @Test
    void acceptsTextDriverWithRealVoiceId() {
        String payload = """
                {"items":[{"detailList":[
                  {"contentType":"avatar","wetaSpeakerId":"avatar-1"},
                  {"contentType":"text","content":"健康科普内容","magicSpeakerId":"speaker_health_24k"}
                ]}]}
                """;

        assertDoesNotThrow(() -> VideoCompositionPayloadValidator.validate(payload));
    }

    @Test
    void rejectsTextDriverWithoutVoice() {
        String payload = """
                {"items":[{"detailList":[
                  {"contentType":"text","content":"健康科普内容"}
                ]}]}
                """;

        AppException exception = assertThrows(
                AppException.class,
                () -> VideoCompositionPayloadValidator.validate(payload));

        assertEquals("DH001", exception.getCode());
    }

    @Test
    void rejectsPlaceholderVoiceId() {
        String payload = """
                {"items":[{"detailList":[
                  {"contentType":"text","content":"健康科普内容","magicSpeakerId":"voice1"}
                ]}]}
                """;

        AppException exception = assertThrows(
                AppException.class,
                () -> VideoCompositionPayloadValidator.validate(payload));

        assertEquals("DH002", exception.getCode());
    }

    @Test
    void acceptsAudioDriverWithoutVoice() {
        String payload = """
                {"items":[{"detailList":[
                  {"contentType":"audio","content":"https://example.com/audio.wav"}
                ]}]}
                """;

        assertDoesNotThrow(() -> VideoCompositionPayloadValidator.validate(payload));
    }
}
