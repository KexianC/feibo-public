package com.hzmwmt.trigger.http.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.hzmwmt.api.dto.DigitalHumanVoiceAuditionRequestDTO;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.entity.user.TDigitalWork;
import com.hzmwmt.domain.digital.service.work.DigitalVideoDuration;
import com.hzmwmt.domain.digital.service.work.DigitalWorkQuotaSettlement;
import com.hzmwmt.infrastructure.properties.DigitalHumanApiProperties;
import com.hzmwmt.domain.mapper.user.AdminDigitalWorkMapper;
import com.hzmwmt.infrastructure.gateway.digital.IDigitalApiService;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Authenticated proxy for the limited digital-human Open Platform operations
 * that the browser needs. Provider credentials never leave the server.
 */
@Validated
@RestController
@RequestMapping("/api/v1/digital-human/open")
public class DigitalHumanOpenController {

    private static final Pattern TASK_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

    @Resource
    private IAuthService authService;

    @Resource
    private AdminDigitalWorkMapper adminDigitalWorkMapper;

    @Resource
    private IDigitalApiService digitalApiService;
    @Resource
    private DigitalWorkQuotaSettlement digitalWorkQuotaSettlement;
    @Resource
    private DigitalHumanApiProperties digitalHumanApiProperties;

    @GetMapping("/video/result/{taskId}")
    public Response<JsonNode> queryVideoResult(@PathVariable("taskId") String taskId) {
        String normalizedTaskId = taskId == null ? "" : taskId.trim();
        if (!TASK_ID_PATTERN.matcher(normalizedTaskId).matches()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Invalid task identifier");
        }

        Long userId = authService.queryValidatedUser().getUserId();
        TDigitalWork work = adminDigitalWorkMapper.selectOne(
                new LambdaQueryWrapper<TDigitalWork>()
                        .eq(TDigitalWork::getExternalId, normalizedTaskId)
                        .eq(TDigitalWork::getUserId, userId)
        );
        if (work == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Task not found or not accessible");
        }
        if (work.getStatus() != null && work.getStatus() == 0) {
            return success(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                    .put("taskId", normalizedTaskId).put("status", "fail")
                    .put("statusDesc", work.getFailureReason()));
        }
        JsonNode data = digitalApiService.queryVideoResult(normalizedTaskId);
        if (work.getQuotaBizKey() != null) {
            String state = data.path("status").asText().toLowerCase(Locale.ROOT);
            if (java.util.Set.of("suc", "success", "succeeded", "completed").contains(state)) {
                int seconds = DigitalVideoDuration.normalize(data.hasNonNull("duration") ? data.path("duration").asLong() : null,
                        digitalHumanApiProperties.getCallbackDurationUnit());
                digitalWorkQuotaSettlement.settle(work, true, seconds);
            } else if (java.util.Set.of("fail", "failed", "error").contains(state)) {
                digitalWorkQuotaSettlement.settle(work, false, 0);
                data = withoutVideoUrls(data);
            } else {
                data = withoutVideoUrls(data);
            }
        }
        return success(data);
    }

    private static JsonNode withoutVideoUrls(JsonNode value) {
        JsonNode copy = value.deepCopy();
        stripVideoUrls(copy);
        return copy;
    }

    private static void stripVideoUrls(JsonNode node) {
        if (node.isObject()) {
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
            var names = new java.util.ArrayList<String>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (name.toLowerCase(Locale.ROOT).contains("url")) object.remove(name);
                else stripVideoUrls(object.get(name));
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) stripVideoUrls(child);
        }
    }

    @PostMapping("/voice/audition")
    public Response<JsonNode> auditionVoice(
            @Valid @RequestBody DigitalHumanVoiceAuditionRequestDTO requestDTO) {
        authService.queryValidatedUser();
        if (requestDTO.isUnsupportedFields()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Request contains unsupported fields");
        }

        JsonNode data = digitalApiService.auditionVoice(
                requestDTO.getText().trim(),
                requestDTO.getSpeaker().trim(),
                requestDTO.getAudioType().toLowerCase(Locale.ROOT),
                requestDTO.getSpeed(),
                requestDTO.getVolume(),
                requestDTO.getPitch()
        );
        return success(data);
    }

    private static Response<JsonNode> success(JsonNode data) {
        return Response.<JsonNode>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
