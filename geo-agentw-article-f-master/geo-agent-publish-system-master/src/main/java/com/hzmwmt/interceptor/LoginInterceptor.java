package com.hzmwmt.interceptor;

import com.hzmwmt.common.context.LoginContextHolder;
import com.hzmwmt.common.exception.AppException;
import com.hzmwmt.config.AppSecurityProperties;
import com.hzmwmt.constant.LoginConstants;
import com.hzmwmt.service.LoginService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.Set;

@Component
public class LoginInterceptor implements AsyncHandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Resource
    private LoginService loginService;

    @Resource
    private AppSecurityProperties securityProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        LoginContextHolder.clear();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        HttpSession session = request.getSession(false);
        boolean loggedIn = loginService.isLoggedIn(session);
        if (!loggedIn || session == null) {
            throw new AppException("UNAUTHORIZED", "Please login first.", HttpStatus.UNAUTHORIZED);
        }

        Long userId = asLong(session.getAttribute(LoginConstants.SESSION_USER_ID));
        if (userId == null || userId <= 0) {
            throw new AppException("UNAUTHORIZED", "Login session is invalid.", HttpStatus.UNAUTHORIZED);
        }

        if (requiresCsrf(request)) {
            assertValidCsrf(request, session);
        }

        LoginContextHolder.setUser(
                userId,
                asString(session.getAttribute(LoginConstants.SESSION_USERNAME)),
                asString(session.getAttribute(LoginConstants.SESSION_DISPLAY_NAME)),
                asString(session.getAttribute(LoginConstants.SESSION_ROLE)),
                true
        );
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LoginContextHolder.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        LoginContextHolder.clear();
    }

    private boolean requiresCsrf(HttpServletRequest request) {
        if (!SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            return true;
        }
        return "/account/login".equals(request.getRequestURI());
    }

    private void assertValidCsrf(HttpServletRequest request, HttpSession session) {
        Object expected = session.getAttribute(LoginConstants.SESSION_CSRF_TOKEN);
        String actual = request.getHeader(securityProperties.getCsrfHeaderName());
        if (expected == null || !StringUtils.hasText(actual) || !expected.toString().equals(actual)) {
            throw new AppException("CSRF_INVALID", "Invalid CSRF token.", HttpStatus.FORBIDDEN);
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
