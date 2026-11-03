package com.hzmwmt.security;

import com.hzmwmt.constant.LoginConstants;
import com.hzmwmt.mapper.SystemOperationLogMapper;
import com.hzmwmt.model.entity.SystemOperationLog;
import com.hzmwmt.util.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditLogFilter extends OncePerRequestFilter {

    private static final Set<String> CORE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Value("${app.geo-integration.enabled:false}")
    private boolean integratedWithFeibo;

    @Value("${app.geo-integration.api-prefix:/geo-agent-api}")
    private String apiPrefix;

    @Resource
    private SystemOperationLogMapper operationLogMapper;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return integratedWithFeibo && !request.getRequestURI().startsWith(normalizedApiPrefix());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            HttpSession session = request.getSession(false);
            log.info("AUDIT requestId={} status={} durationMs={}",
                    requestId,
                    response.getStatus(),
                    durationMs);
            persistOperationLog(request, response, session, durationMs);
            MDC.remove("requestId");
        }
    }

    private void persistOperationLog(HttpServletRequest request, HttpServletResponse response, HttpSession session, long durationMs) {
        if (!CORE_METHODS.contains(request.getMethod().toUpperCase())) {
            return;
        }
        String path = request.getRequestURI();
        try {
            OperatorIdentity operator = resolveOperator(session);
            SystemOperationLog operationLog = SystemOperationLog.builder()
                    .id(snowflakeIdGenerator.nextId())
                    .operatorId(operator.userId())
                    .operatorName(operator.displayName())
                    .role(operator.role())
                    .moduleName(resolveModule(path))
                    .actionName(resolveAction(request.getMethod(), path))
                    .method(request.getMethod())
                    .path(path)
                    .status(response.getStatus())
                    .durationMs(durationMs)
                    .remoteAddr(request.getRemoteAddr())
                    .details(queryParameterNames(request.getQueryString()))
                    .build();
            operationLogMapper.insert(operationLog);
        } catch (Exception e) {
            log.warn("persist operation log failed: exceptionType={}", e.getClass().getSimpleName());
        }
    }

    private String resolveModule(String path) {
        if (integratedWithFeibo && path.startsWith(normalizedApiPrefix())) {
            path = path.substring(normalizedApiPrefix().length());
        }
        if (path.startsWith("/system")) return "系统管理";
        if (path.startsWith("/geo")) return "GEO运营";
        if (path.startsWith("/article")) return "内容发布";
        if (path.startsWith("/knowledge") || path.startsWith("/rag")) return "知识库";
        if (path.startsWith("/doctor")) return "医生管理";
        if (path.startsWith("/department")) return "科室管理";
        if (path.startsWith("/hospital")) return "医院管理";
        if (path.startsWith("/title")) return "职称字典";
        if (path.startsWith("/account")) return "账号管理";
        if (path.startsWith("/login")) return "登录";
        return "其他";
    }

    private String resolveAction(String method, String path) {
        if (path.contains("delete") || "DELETE".equalsIgnoreCase(method)) return "删除";
        if (path.contains("toggle")) return "状态变更";
        if (path.contains("edit") || path.contains("update") || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) return "更新";
        if (path.contains("generate")) return "生成";
        if (path.contains("audit")) return "审核";
        if (path.contains("run")) return "运行";
        if (path.contains("login")) return "登录";
        if (path.contains("logout")) return "退出";
        return "提交";
    }

    private String queryParameterNames(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return null;
        }
        return java.util.Arrays.stream(queryString.split("&"))
                .map(parameter -> {
                    int separator = parameter.indexOf('=');
                    return separator < 0 ? parameter : parameter.substring(0, separator);
                })
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted()
                .limit(50)
                .collect(java.util.stream.Collectors.joining(","));
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

    private String normalizedApiPrefix() {
        if (apiPrefix == null || apiPrefix.isBlank()) {
            return "/geo-agent-api";
        }
        String normalized = apiPrefix.startsWith("/") ? apiPrefix : "/" + apiPrefix;
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private OperatorIdentity resolveOperator(HttpSession session) {
        if (session != null && session.getAttribute(LoginConstants.SESSION_USER_ID) != null) {
            return new OperatorIdentity(
                    asLong(session.getAttribute(LoginConstants.SESSION_USER_ID)),
                    asString(session.getAttribute(LoginConstants.SESSION_USERNAME)),
                    asString(session.getAttribute(LoginConstants.SESSION_ROLE))
            );
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new OperatorIdentity(null, null, null);
        }
        Object principal = authentication.getPrincipal();
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(value -> value == null ? "" : value.toUpperCase(Locale.ROOT))
                .anyMatch(value -> value.equals("ADMIN") || value.equals("ROLE_ADMIN")) ? "ADMIN" : "USER";
        String displayName = asString(readProperty(principal, "getNickName"));
        if (displayName == null) {
            displayName = authentication.getName();
        }
        return new OperatorIdentity(asLong(readProperty(principal, "getUserId")), displayName, role);
    }

    private Object readProperty(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private record OperatorIdentity(Long userId, String displayName, String role) {
    }
}
