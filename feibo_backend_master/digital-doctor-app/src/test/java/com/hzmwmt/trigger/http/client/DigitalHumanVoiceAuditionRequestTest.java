package com.hzmwmt.trigger.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.api.dto.DigitalHumanVoiceAuditionRequestDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigitalHumanVoiceAuditionRequestTest {
    private static final String CLONED_SPEAKER =
            "galaxy_fastv7||||galaxy_fastv7||||11db287fb8dab21a1250f8fc32b9d24b";

    @Test
    void reportedClonePayloadUsesTheSpeakerRegisteredWithTheProvider() throws Exception {
        String json = """
                {"text":"欢迎来到飞驳数字医生工作台",
                 "speaker":"galaxy_fastv7||||galaxy_fastv7||||11db287fb8dab21a1250f8fc32b9d24b",
                 "audioType":"mp3","pitch":0,"speed":1,"volume":1}
                """;
        DigitalHumanVoiceAuditionRequestDTO dto = new ObjectMapper()
                .readValue(json, DigitalHumanVoiceAuditionRequestDTO.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(dto).isEmpty());
        }
        assertEquals("galaxy_fastv7||||11db287fb8dab21a1250f8fc32b9d24b", dto.getSpeaker());
        assertFalse(dto.isUnsupportedFields());
    }

    @ParameterizedTest
    @ValueSource(strings = {"galaxy_fastv2_moqingyan", "11db287fb8dab21a1250f8fc32b9d24b",
            "model.v7-1||||clone_123", "  galaxy_fastv7||||clone_123  "})
    void acceptsStandardAndCompositeSpeakers(String speaker) {
        var dto = request(speaker);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(dto).isEmpty());
        }
        assertEquals(speaker.trim(), dto.getSpeaker());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "model|clone", "model|||clone", "model|||||clone",
            "||||clone", "model||||", "model||||||||clone", "model clone",
            "https://example.com/voice", "model;clone", "model\nclone"})
    void rejectsMalformedSpeakers(String speaker) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(request(speaker)).isEmpty());
        }
    }

    @Test
    void stillRejectsNullAndOversizedSpeakers() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(request(null)).isEmpty());
            assertFalse(factory.getValidator().validate(request("a".repeat(129))).isEmpty());
        }
    }

    @Test
    void duplicatePrefixNormalizationIsIdempotentAndDoesNotAlterDistinctSegments() {
        var dto = request("galaxy_fastv7||||" + CLONED_SPEAKER);
        String canonical = "galaxy_fastv7||||11db287fb8dab21a1250f8fc32b9d24b";
        assertEquals(canonical, dto.getSpeaker());
        dto.setSpeaker(dto.getSpeaker());
        assertEquals(canonical, dto.getSpeaker());
        String distinct = "galaxy_fastv7||||galaxy_fastv8||||11db287fb8dab21a1250f8fc32b9d24b";
        dto.setSpeaker(distinct);
        assertEquals(distinct, dto.getSpeaker());
        dto.setSpeaker("galaxy_fastv7||||galaxy_fastv7||||not-a-clone-id");
        assertEquals("galaxy_fastv7||||galaxy_fastv7||||not-a-clone-id", dto.getSpeaker());
    }

    private DigitalHumanVoiceAuditionRequestDTO request(String speaker) {
        var dto = new DigitalHumanVoiceAuditionRequestDTO();
        dto.setText("试听测试");
        dto.setSpeaker(speaker);
        return dto;
    }
}
