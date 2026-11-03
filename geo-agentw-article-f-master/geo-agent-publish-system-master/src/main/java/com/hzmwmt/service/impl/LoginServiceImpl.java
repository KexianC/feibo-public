package com.hzmwmt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.config.AppSecurityProperties;
import com.hzmwmt.constant.LoginConstants;
import com.hzmwmt.mapper.SystemUserMapper;
import com.hzmwmt.model.dto.LoginRequest;
import com.hzmwmt.model.dto.LoginResponse;
import com.hzmwmt.model.entity.SystemUser;
import com.hzmwmt.security.LoginAttemptLimiter;
import com.hzmwmt.service.LoginService;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class LoginServiceImpl implements LoginService {

    @Resource
    private AppSecurityProperties securityProperties;

    @Resource
    private LoginAttemptLimiter loginAttemptLimiter;

    @Resource
    private SystemUserMapper systemUserMapper;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginResponse login(LoginRequest loginRequest, HttpServletRequest request, HttpSession session) {
        if (loginRequest == null || !StringUtils.hasText(loginRequest.getPassword())) {
            throw new AppException("PARAM_INVALID", "Password is required.");
        }
        ensureDefaultAdminUser();

        String clientKey = loginAttemptLimiter.clientKey(request);
        loginAttemptLimiter.assertNotLocked(clientKey);

        String username = StringUtils.hasText(loginRequest.getUsername()) ? loginRequest.getUsername().trim() : "admin";
        SystemUser user = systemUserMapper.selectOne(new LambdaQueryWrapper<SystemUser>()
                .eq(SystemUser::getUsername, username)
                .last("LIMIT 1"));
        if (user == null || !Integer.valueOf(1).equals(user.getEnableStatus())
                || !passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            loginAttemptLimiter.onFailure(clientKey);
            log.warn("login failed: status={}", HttpStatus.UNAUTHORIZED.value());
            throw new AppException("LOGIN_FAILED", "Invalid username or password.", HttpStatus.UNAUTHORIZED);
        }

        request.changeSessionId();
        int maxInactiveSeconds = resolveSessionSeconds(Boolean.TRUE.equals(loginRequest.getRememberMe()));
        session.setMaxInactiveInterval(maxInactiveSeconds);

        String csrfToken = UUID.randomUUID().toString();
        session.setAttribute(LoginConstants.SESSION_LOGIN_FLAG, true);
        session.setAttribute(LoginConstants.SESSION_CSRF_TOKEN, csrfToken);
        session.setAttribute(LoginConstants.SESSION_LOGIN_TIME, Instant.now().toString());
        session.setAttribute(LoginConstants.SESSION_CLIENT_IP, clientKey);
        session.setAttribute(LoginConstants.SESSION_USER_ID, user.getId());
        session.setAttribute(LoginConstants.SESSION_USERNAME, user.getUsername());
        session.setAttribute(LoginConstants.SESSION_DISPLAY_NAME, user.getDisplayName());
        session.setAttribute(LoginConstants.SESSION_ROLE, user.getRole());

        SystemUser update = new SystemUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        systemUserMapper.updateById(update);

        loginAttemptLimiter.onSuccess(clientKey);
        log.info("login succeeded: userId={}", user.getId());
        return buildResponse(session, csrfToken);
    }

    @Override
    public void logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
    }

    @Override
    public boolean isLoggedIn(HttpSession session) {
        if (session == null) {
            return false;
        }
        return Boolean.TRUE.equals(session.getAttribute(LoginConstants.SESSION_LOGIN_FLAG));
    }

    @Override
    public LoginResponse status(HttpSession session) {
        if (!isLoggedIn(session)) {
            return new LoginResponse(false, null, null, null, null, null);
        }
        return buildResponse(session, value(session, LoginConstants.SESSION_CSRF_TOKEN));
    }

    private LoginResponse buildResponse(HttpSession session, String csrfToken) {
        Long userId = null;
        Object rawUserId = session.getAttribute(LoginConstants.SESSION_USER_ID);
        if (rawUserId instanceof Number number) {
            userId = number.longValue();
        } else if (rawUserId != null) {
            try {
                userId = Long.parseLong(rawUserId.toString());
            } catch (NumberFormatException ignored) {
                userId = null;
            }
        }
        return new LoginResponse(
                true,
                csrfToken,
                userId,
                value(session, LoginConstants.SESSION_USERNAME),
                value(session, LoginConstants.SESSION_DISPLAY_NAME),
                value(session, LoginConstants.SESSION_ROLE)
        );
    }

    private String value(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        return value == null ? null : value.toString();
    }

    private int resolveSessionSeconds(boolean rememberMe) {
        if (rememberMe) {
            return Math.max(1, securityProperties.getRememberMeDays()) * 24 * 60 * 60;
        }
        return Math.max(1, securityProperties.getSessionTimeoutMinutes()) * 60;
    }

    private void ensureDefaultAdminUser() {
        Long count = systemUserMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        if (!StringUtils.hasText(securityProperties.getAdminPassword())
                && !StringUtils.hasText(securityProperties.getAdminPasswordHash())) {
            throw new AppException("CONFIG_ERROR", "Admin password is not configured.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String hash = StringUtils.hasText(securityProperties.getAdminPasswordHash())
                ? securityProperties.getAdminPasswordHash()
                : passwordEncoder.encode(securityProperties.getAdminPassword());
        SystemUser admin = SystemUser.builder()
                .id(snowflakeIdGenerator.nextId())
                .username("admin")
                .displayName("系统管理员")
                .role(LoginConstants.ROLE_ADMIN)
                .passwordHash(hash)
                .enableStatus(1)
                .build();
        systemUserMapper.insert(admin);
        log.info("initialized default admin user");
    }
}
