package com.hzmwmt.security;

import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.config.AppSecurityProperties;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class LoginAttemptLimiter {

    @Resource
    private AppSecurityProperties securityProperties;

    private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public void assertNotLocked(String key) {
        AttemptState state = attempts.get(key);
        if (state == null || state.lockedUntil == null) {
            return;
        }
        if (Instant.now().isBefore(state.lockedUntil)) {
            throw new AppException("LOGIN_LOCKED", "Too many login attempts. Try again later.", HttpStatus.TOO_MANY_REQUESTS);
        }
        attempts.remove(key);
    }

    public void onSuccess(String key) {
        attempts.remove(key);
    }

    public void onFailure(String key) {
        int maxAttempts = Math.max(1, securityProperties.getLoginMaxAttempts());
        int lockMinutes = Math.max(1, securityProperties.getLoginLockMinutes());
        AttemptState state = attempts.compute(key, (ignored, current) -> {
            AttemptState next = current == null ? new AttemptState() : current;
            next.failureCount++;
            next.lastFailure = Instant.now();
            if (next.failureCount >= maxAttempts) {
                next.lockedUntil = Instant.now().plus(Duration.ofMinutes(lockMinutes));
            }
            return next;
        });
        if (state.lockedUntil != null) {
            log.warn("login locked: failures={}", state.failureCount);
        }
    }

    public String clientKey(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (StringUtils.hasText(remoteAddr)) {
            return remoteAddr;
        }
        return "unknown";
    }

    private static class AttemptState {
        private int failureCount;
        private Instant lastFailure;
        private Instant lockedUntil;
    }
}
