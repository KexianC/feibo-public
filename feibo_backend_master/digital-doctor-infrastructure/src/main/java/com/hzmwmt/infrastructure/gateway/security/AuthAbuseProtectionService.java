package com.hzmwmt.infrastructure.gateway.security;

import com.hzmwmt.infrastructure.properties.AuthProtectionProperties;
import com.hzmwmt.types.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/**
 * Redis-backed fixed-window controls for login, SMS issuance and OTP guessing.
 */
@Service
@RequiredArgsConstructor
public class AuthAbuseProtectionService {

    private static final String INCREMENT_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            if current > tonumber(ARGV[1]) then
              return 0
            end
            return 1
            """;

    private final RedissonClient redissonClient;
    private final AuthProtectionProperties properties;

    public void assertSmsSendAllowed(String phoneNumber, String clientAddress) {
        consume("sms:cooldown:phone", phoneNumber, 1, properties.getSmsCooldown(), "验证码发送过于频繁，请稍后再试");
        consume("sms:hour:phone", phoneNumber, properties.getSmsPhonePerHour(), Duration.ofHours(1), "验证码发送次数过多，请稍后再试");
        consume("sms:hour:address", normalizedAddress(clientAddress), properties.getSmsAddressPerHour(), Duration.ofHours(1), "验证码发送次数过多，请稍后再试");
    }

    /**
     * Reserves one login attempt. A successful login clears only the account
     * counter; the shared network counter intentionally remains in place.
     */
    public void assertLoginAllowed(String phoneNumber, String clientAddress) {
        consume("login:window:phone", phoneNumber, properties.getLoginPhonePerWindow(), properties.getLoginWindow(), "登录尝试次数过多，请稍后再试");
        consume("login:window:address", normalizedAddress(clientAddress), properties.getLoginAddressPerWindow(), properties.getLoginWindow(), "登录尝试次数过多，请稍后再试");
    }

    public void clearSuccessfulLogin(String phoneNumber) {
        delete("login:window:phone", phoneNumber);
    }

    /**
     * Consumes one OTP verification attempt before comparing the supplied code.
     */
    public void assertVerificationAttemptAllowed(String phoneNumber) {
        consume("otp:attempt:phone", phoneNumber, properties.getVerificationAttempts(), properties.getVerificationWindow(), "验证码尝试次数过多，请重新获取");
    }

    public void clearVerificationAttempts(String phoneNumber) {
        delete("otp:attempt:phone", phoneNumber);
    }

    private void consume(String namespace, String subject, int limit, Duration window, String message) {
        if (limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalStateException("认证防护限流配置无效");
        }

        Long allowed = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                INCREMENT_SCRIPT,
                RScript.ReturnType.INTEGER,
                List.of(key(namespace, subject)),
                limit,
                window.toMillis()
        );

        if (allowed == null || allowed != 1L) {
            throw new RateLimitException(message);
        }
    }

    private void delete(String namespace, String subject) {
        redissonClient.getBucket(key(namespace, subject), StringCodec.INSTANCE).delete();
    }

    private String key(String namespace, String subject) {
        return "auth:protect:" + namespace + ":" + digest(subject == null ? "" : subject.trim());
    }

    private static String normalizedAddress(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
