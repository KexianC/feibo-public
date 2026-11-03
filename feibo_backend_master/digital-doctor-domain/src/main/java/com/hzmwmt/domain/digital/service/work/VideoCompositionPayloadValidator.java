package com.hzmwmt.domain.digital.service.work;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

/** Validates composition driver data before quota deduction or provider submission. */
final class VideoCompositionPayloadValidator {

    private static final Pattern PLACEHOLDER_VOICE_ID = Pattern.compile("(?i)^voice_?\\d+$");

    private VideoCompositionPayloadValidator() {
    }

    static void validate(String requestBody) {
        if (!StringUtils.hasText(requestBody)) {
            throw invalidPayload("合成参数不能为空");
        }

        final JSONObject payload;
        try {
            payload = JSON.parseObject(requestBody);
        } catch (JSONException exception) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "合成参数不是有效的JSON", exception);
        }

        JSONArray items = payload == null ? null : payload.getJSONArray("items");
        rejectCredentials(payload);
        if (items == null || items.isEmpty()) {
            throw invalidPayload("合成参数缺少场景内容");
        }

        boolean hasTextDriver = false;
        boolean hasAudioDriver = false;
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            JSONObject item = items.getJSONObject(itemIndex);
            JSONArray details = item == null ? null : item.getJSONArray("detailList");
            if (details == null) continue;

            for (int detailIndex = 0; detailIndex < details.size(); detailIndex++) {
                JSONObject detail = details.getJSONObject(detailIndex);
                if (detail == null) continue;
                String contentType = detail.getString("contentType");

                if ("text".equalsIgnoreCase(contentType)) {
                    hasTextDriver = true;
                    if (!StringUtils.hasText(detail.getString("content"))) {
                        throw invalidPayload("文本驱动内容不能为空");
                    }
                    validateVoice(detail.getString("magicSpeakerId"));
                } else if ("audio".equalsIgnoreCase(contentType)
                        && StringUtils.hasText(detail.getString("content"))) {
                    hasAudioDriver = true;
                }
            }
        }

        if (!hasTextDriver && !hasAudioDriver) {
            throw invalidPayload("合成参数缺少有效的文本或音频驱动内容");
        }
    }

    private static void rejectCredentials(Object node) {
        if (node instanceof JSONObject object) {
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                String key = entry.getKey().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
                if (Set.of("appkey", "appsecret", "apikey", "secret", "signature", "sign", "authorization",
                        "password", "accesskey", "credential").contains(key)
                        || key.endsWith("token") || key.endsWith("secret") || key.endsWith("signature")
                        || key.endsWith("credential") || key.endsWith("accesskey")) {
                    throw invalidPayload("合成参数不得包含认证密钥");
                }
                rejectCredentials(entry.getValue());
            }
        } else if (node instanceof JSONArray array) {
            for (Object value : array) rejectCredentials(value);
        }
    }

    private static void validateVoice(String magicSpeakerId) {
        if (!StringUtils.hasText(magicSpeakerId)) {
            throw new AppException(ResponseCode.VOICE_REQUIRED.getCode(), ResponseCode.VOICE_REQUIRED.getInfo());
        }
        if (PLACEHOLDER_VOICE_ID.matcher(magicSpeakerId.trim()).matches()) {
            throw new AppException(ResponseCode.VOICE_INVALID.getCode(), ResponseCode.VOICE_INVALID.getInfo());
        }
    }

    private static AppException invalidPayload(String message) {
        return new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
    }
}
