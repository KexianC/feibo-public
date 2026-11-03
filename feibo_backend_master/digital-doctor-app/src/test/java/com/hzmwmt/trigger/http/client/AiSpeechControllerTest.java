package com.hzmwmt.trigger.http.client;

import com.hzmwmt.api.dto.SpeechSynthesisRequestDTO;
import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.service.tts.TtsService;
import com.hzmwmt.domain.service.tts.TtsUsageGuard;
import com.hzmwmt.trigger.http.client.handler.ExceptionHandler;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiSpeechControllerTest {

    @Test
    void returnsNonCacheableMp3AfterExplicitIdentityCheck() {
        TtsService ttsService = mock(TtsService.class);
        IAuthService authService = mock(IAuthService.class);
        byte[] audio = new byte[]{10, 20, 30};
        when(ttsService.synthesize("请注意休息。")).thenReturn(audio);
        when(authService.queryValidatedUser()).thenReturn(
                AuthUserEntity.builder().userId(42L).build());
        SpeechSynthesisRequestDTO request = new SpeechSynthesisRequestDTO();
        request.setText("请注意休息。");
        TtsUsageGuard usageGuard = new TtsUsageGuard(8, 30, 12_000, 60_000);

        ResponseEntity<byte[]> response = new AiSpeechController(ttsService, authService, usageGuard)
                .synthesize(request);

        verify(authService).queryValidatedUser();
        verify(ttsService).synthesize("请注意休息。");
        assertEquals(MediaType.parseMediaType("audio/mpeg"), response.getHeaders().getContentType());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals(audio.length, response.getHeaders().getContentLength());
        assertArrayEquals(audio, response.getBody());
    }

    @Test
    void requestDtoRejectsBlankAndOversizedText() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        SpeechSynthesisRequestDTO blank = new SpeechSynthesisRequestDTO();
        blank.setText("   ");
        SpeechSynthesisRequestDTO oversized = new SpeechSynthesisRequestDTO();
        char[] chars = new char[SpeechSynthesisRequestDTO.MAX_TEXT_LENGTH + 1];
        Arrays.fill(chars, '医');
        oversized.setText(new String(chars));

        assertFalse(validator.validate(blank).isEmpty());
        assertFalse(validator.validate(oversized).isEmpty());
    }

    @Test
    void providerFailureReturnsJsonInsteadOfCollapsingIntoUnknown500() throws Exception {
        TtsService ttsService = mock(TtsService.class);
        IAuthService authService = mock(IAuthService.class);
        when(authService.queryValidatedUser()).thenReturn(
                AuthUserEntity.builder().userId(42L).build());
        when(ttsService.synthesize("test reply")).thenThrow(new AppException(
                ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(),
                "provider detail must not escape"));
        AiSpeechController controller = new AiSpeechController(
                ttsService,
                authService,
                new TtsUsageGuard(8, 30, 12_000, 60_000));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/ai/tts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.parseMediaType("audio/mpeg"), MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"test reply\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode()));
    }
}
