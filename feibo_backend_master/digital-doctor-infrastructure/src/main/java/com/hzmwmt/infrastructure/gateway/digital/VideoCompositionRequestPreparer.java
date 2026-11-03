package com.hzmwmt.infrastructure.gateway.digital;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates and normalizes provider business JSON before submission.
 *
 * <p>Authentication belongs exclusively in server-generated HTTP headers.
 * Supplying an authentication-like key anywhere in the business JSON is
 * rejected instead of silently persisting or forwarding it.</p>
 */
final class VideoCompositionRequestPreparer {

    private static final Set<String> FORBIDDEN_EXACT_KEYS = Set.of(
            "appkey",
            "appsecret",
            "apikey",
            "secret",
            "signature",
            "sign",
            "token",
            "accesstoken",
            "refreshtoken",
            "idtoken",
            "securitytoken",
            "authorization",
            "password",
            "accesskey",
            "credential"
    );

    private VideoCompositionRequestPreparer() {
    }

    static String prepare(ObjectMapper objectMapper, String requestBodyJson, String callbackUrl) {
        try {
            JsonNode root = objectMapper.readTree(requestBodyJson);
            if (!(root instanceof ObjectNode objectNode)) {
                throw illegalPayload("Video composition payload must be a JSON object");
            }

            rejectAuthenticationFields(objectNode, "$");
            if (StringUtils.hasText(callbackUrl)) {
                objectNode.put("callbackUrl", callbackUrl);
            }
            return objectMapper.writeValueAsString(objectNode);
        } catch (JsonProcessingException e) {
            throw new AppException(
                    ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "Invalid video composition payload",
                    e
            );
        }
    }

    private static void rejectAuthenticationFields(JsonNode node, String path) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalizedKey = normalizeKey(field.getKey());
                if (isForbidden(normalizedKey)) {
                    throw illegalPayload("Authentication field is not allowed in provider business JSON: "
                            + path + "." + field.getKey());
                }
                rejectAuthenticationFields(field.getValue(), path + "." + field.getKey());
            }
            return;
        }

        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                rejectAuthenticationFields(node.get(i), path + "[" + i + "]");
            }
        }
    }

    private static boolean isForbidden(String normalizedKey) {
        return FORBIDDEN_EXACT_KEYS.contains(normalizedKey)
                || normalizedKey.endsWith("token")
                || normalizedKey.endsWith("secret")
                || normalizedKey.endsWith("signature")
                || normalizedKey.endsWith("credential")
                || normalizedKey.endsWith("accesskey");
    }

    private static String normalizeKey(String key) {
        StringBuilder normalized = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char current = key.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                normalized.append(Character.toLowerCase(current));
            }
        }
        return normalized.toString().toLowerCase(Locale.ROOT);
    }

    private static AppException illegalPayload(String message) {
        return new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
    }
}
