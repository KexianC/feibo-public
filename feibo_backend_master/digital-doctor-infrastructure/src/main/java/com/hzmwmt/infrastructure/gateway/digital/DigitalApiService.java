package com.hzmwmt.infrastructure.gateway.digital;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hzmwmt.infrastructure.gateway.digital.dto.VideoComposeResponseDTO;
import com.hzmwmt.infrastructure.properties.DigitalHumanApiProperties;
import com.hzmwmt.domain.digital.model.entity.VideoCompositionSubmissionEntity;
import com.hzmwmt.domain.digital.service.work.VideoCompositionRejectedException;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
@Slf4j
public class DigitalApiService implements IDigitalApiService {
    private static final String VIDEO_COMPOSE_PATH = "/metaman/open/video/cmp";
    private static final String VIDEO_RESULT_PATH = "/metaman/open/video/result/{taskId}";
    private static final String VOICE_AUDITION_URL = "https://open.mobvoi.com/cmp/audition";

    @Resource
    @Qualifier("digitalWebClient")
    private WebClient webClient;
    @Resource
    private DigitalHumanApiProperties apiProperties;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private DigitalHumanCallbackVerifier callbackVerifier;

    @Override
    public VideoCompositionSubmissionEntity createVideoComposition(String requestBodyJson) {
        DigitalHumanCallbackVerifier.IssuedCredential callbackCredential = callbackVerifier.issue();
        String callbackUrl = authenticatedCallbackUrl(callbackCredential);
        String requestBody = VideoCompositionRequestPreparer.prepare(
                objectMapper, requestBodyJson, callbackUrl);
        // Persist the same business payload without the callback credential.
        String auditRequestBody = VideoCompositionRequestPreparer.prepare(
                objectMapper, requestBodyJson, apiProperties.getCallbackUrl());
        OpenPlatformAuth auth = createAuth();
        try {
            VideoComposeResponseDTO response = webClient.post()
                    .uri(VIDEO_COMPOSE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> addAuthHeaders(headers, auth))
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(VideoComposeResponseDTO.class)
                    .block();
            if (response != null && response.getCode() == 200 && response.getData() != null) {
                String taskId = response.getData();
                callbackVerifier.bind(callbackCredential, taskId);
                return VideoCompositionSubmissionEntity.builder()
                        .externalId(taskId)
                        .providerRequestBody(auditRequestBody)
                        .providerRequestSha256(sha256(requestBody))
                        .build();
            }
            if (response != null && response.getCode() != 200) throw new VideoCompositionRejectedException();
            throw remoteFailure("Digital-human video composition failed");
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw remoteFailure("Digital-human video composition failed", e);
        }
    }

    @Override
    public JsonNode queryVideoResult(String taskId) {
        OpenPlatformAuth auth = createAuth();
        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path(VIDEO_RESULT_PATH).build(taskId))
                    .headers(headers -> addAuthHeaders(headers, auth))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return requireProviderData(response, 200, "Video result query failed");
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw remoteFailure("Video result query failed", e);
        }
    }

    @Override
    public JsonNode auditionVoice(String text, String speaker, String audioType,
                                  double speed, double volume, double pitch) {
        OpenPlatformAuth auth = createAuth();
        ObjectNode ttsParam = objectMapper.createObjectNode();
        ttsParam.put("text", text);
        ttsParam.put("speaker", speaker);
        ttsParam.put("audio_type", audioType);
        ttsParam.put("speed", speed);
        ttsParam.put("volume", volume);
        ttsParam.put("pitch", pitch);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("appkey", auth.appKey);
        payload.put("signature", auth.signature);
        payload.put("timestamp", auth.timestamp);
        payload.set("ttsParam", ttsParam);
        payload.put("text", text);
        payload.put("speaker", speaker);
        payload.put("audio_type", audioType);
        payload.put("speed", speed);
        payload.put("volume", volume);
        payload.put("pitch", pitch);

        try {
            JsonNode response = webClient.post()
                    .uri(VOICE_AUDITION_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> addAuthHeaders(headers, auth))
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return requireProviderData(response, 0, "Voice audition failed");
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw remoteFailure("Voice audition failed", e);
        }
    }

    private OpenPlatformAuth createAuth() {
        String appKey = apiProperties.getAppKey();
        String appSecret = apiProperties.getAppSecret();
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(appSecret)) {
            throw remoteFailure("Digital-human provider credentials are not configured");
        }
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String plainText = appKey + "+" + appSecret + "+" + timestamp;
        String signature = DigestUtils.md5DigestAsHex(plainText.getBytes(StandardCharsets.UTF_8));
        return new OpenPlatformAuth(appKey, timestamp, signature);
    }

    String authenticatedCallbackUrl(DigitalHumanCallbackVerifier.IssuedCredential credential) {
        String callbackUrl = apiProperties.getCallbackUrl();
        if (!StringUtils.hasText(callbackUrl) || credential == null) {
            throw remoteFailure("Digital-human callback authentication is not configured");
        }
        URI uri;
        try {
            uri = URI.create(callbackUrl);
        } catch (IllegalArgumentException e) {
            throw remoteFailure("Digital-human callback URL is invalid", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw remoteFailure("Digital-human callback URL must use HTTPS");
        }
        return UriComponentsBuilder.fromUri(uri)
                .replaceQueryParam("callbackId", credential.id())
                .replaceQueryParam("callbackToken", credential.token())
                .build()
                .encode()
                .toUriString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void addAuthHeaders(HttpHeaders headers, OpenPlatformAuth auth) {
        headers.set("appKey", auth.appKey);
        headers.set("timestamp", auth.timestamp);
        headers.set("signature", auth.signature);
    }


    private JsonNode requireProviderData(JsonNode response, int successCode, String failureMessage) {
        if (response == null || response.path("code").asInt(Integer.MIN_VALUE) != successCode
                || response.get("data") == null || response.get("data").isNull()) {
            throw remoteFailure(failureMessage);
        }
        return response.get("data");
    }

    private AppException remoteFailure(String message) {
        return new AppException(ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(), message);
    }

    private AppException remoteFailure(String message, Exception cause) {
        log.warn("Digital-human provider request failed; operation={}, errorType={}",
                message, cause.getClass().getSimpleName());
        return new AppException(ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode(), message, cause);
    }

    private static final class OpenPlatformAuth {
        private final String appKey;
        private final String timestamp;
        private final String signature;

        private OpenPlatformAuth(String appKey, String timestamp, String signature) {
            this.appKey = appKey;
            this.timestamp = timestamp;
            this.signature = signature;
        }
    }
}
