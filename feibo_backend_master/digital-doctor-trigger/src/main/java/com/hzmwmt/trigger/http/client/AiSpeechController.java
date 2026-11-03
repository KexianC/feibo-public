package com.hzmwmt.trigger.http.client;

import com.hzmwmt.api.dto.SpeechSynthesisRequestDTO;
import com.hzmwmt.api.dto.SpeechSynthesisCancelRequestDTO;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.service.tts.TtsService;
import com.hzmwmt.domain.service.tts.TtsUsageGuard;
import com.hzmwmt.domain.service.tts.TtsRequestRegistry;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated speech-synthesis proxy for Zhibi Yiyan. */
@RestController
@RequestMapping("/api/v1/ai")
public class AiSpeechController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final TtsService ttsService;
    private final IAuthService authService;
    private final TtsUsageGuard usageGuard;
    private final TtsRequestRegistry requests;

    public AiSpeechController(
            TtsService ttsService,
            IAuthService authService,
            TtsUsageGuard usageGuard) {
        this(ttsService, authService, usageGuard, new TtsRequestRegistry(ttsService, usageGuard));
    }

    @Autowired
    public AiSpeechController(TtsService ttsService, IAuthService authService,
                              TtsUsageGuard usageGuard, TtsRequestRegistry requests) {
        this.ttsService = ttsService;
        this.authService = authService;
        this.usageGuard = usageGuard;
        this.requests = requests;
    }

    @PostMapping(
            value = "/tts",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> synthesize(@Valid @RequestBody SpeechSynthesisRequestDTO request) {
        // Keep an explicit identity check in addition to the global security rule.
        Long userId = authService.queryValidatedUser().getUserId();
        byte[] audio;
        if (request.getSessionId() != null || request.getRequestId() != null) {
            if (request.getSessionId() == null || request.getRequestId() == null)
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "语音会话与请求标识必须同时提供");
            audio = requests.synthesize(userId, request.getSessionId(), request.getRequestId(), request.getText());
        } else {
            try (TtsUsageGuard.Permit ignored = usageGuard.acquire(userId, request.getText().length())) {
                audio = ttsService.synthesize(request.getText());
            }
        }

        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .contentLength(audio.length)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(audio);
    }

    @PostMapping(value = "/tts/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> cancel(@Valid @RequestBody SpeechSynthesisCancelRequestDTO request) {
        Long userId = authService.queryValidatedUser().getUserId();
        requests.cancel(userId, request.getSessionId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
