package com.hzmwmt.integration.geo;

import com.hzmwmt.common.context.LoginContextHolder;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class GeoHostAuthenticationInterceptor implements AsyncHandlerInterceptor {

    private static final String REQUEST_IDENTITY_ATTRIBUTE =
            GeoHostAuthenticationInterceptor.class.getName() + ".identity";

    private final GeoIntegrationProperties properties;

    public GeoHostAuthenticationInterceptor(GeoIntegrationProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        LoginContextHolder.clear();
        if (request != null && request.getDispatcherType() == DispatcherType.ASYNC) {
            return restoreAsyncIdentity(request);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Feibo authentication is required.");
        }

        Object principal = authentication.getPrincipal();
        Long userId = asLong(readProperty(principal, "getUserId"));
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Feibo user identity is invalid.");
        }
        String username = asString(readProperty(principal, "getPhoneNumber"));
        String displayName = asString(readProperty(principal, "getNickName"));
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(value -> value == null ? "" : value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        boolean isSuperAdmin = authorities.contains("SUPER_ADMIN")
                || authorities.contains("ROLE_SUPER_ADMIN");
        boolean isAdmin = isSuperAdmin
                || authorities.contains("ADMIN")
                || authorities.contains("ROLE_ADMIN");
        String role = isAdmin ? "ADMIN" : "USER";

        String requiredRole = properties.getRequiredRole();
        if (!hasRequiredRole(authorities, requiredRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "GEO super administrator access is required.");
        }

        RequestIdentity identity = new RequestIdentity(userId, username, displayName, role);
        if (request != null) {
            request.setAttribute(REQUEST_IDENTITY_ATTRIBUTE, identity);
        }
        setCurrentUser(identity);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LoginContextHolder.clear();
        if (request != null) {
            request.removeAttribute(REQUEST_IDENTITY_ATTRIBUTE);
        }
    }

    @Override
    public void afterConcurrentHandlingStarted(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        LoginContextHolder.clear();
    }

    private boolean restoreAsyncIdentity(HttpServletRequest request) {
        Object attribute = request.getAttribute(REQUEST_IDENTITY_ATTRIBUTE);
        if (!(attribute instanceof RequestIdentity identity)
                || identity.userId() == null
                || identity.userId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Feibo async identity is unavailable.");
        }
        setCurrentUser(identity);
        return true;
    }

    private void setCurrentUser(RequestIdentity identity) {
        LoginContextHolder.setUser(
                identity.userId(),
                identity.username(),
                identity.displayName(),
                identity.role(),
                true
        );
    }

    private boolean hasRequiredRole(Set<String> authorities, String requiredRole) {
        if (requiredRole == null || requiredRole.isBlank()) {
            return true;
        }
        String normalizedRole = requiredRole.trim().toUpperCase(Locale.ROOT);
        String roleAuthority = normalizedRole.startsWith("ROLE_")
                ? normalizedRole
                : "ROLE_" + normalizedRole;
        return authorities.contains(normalizedRole) || authorities.contains(roleAuthority);
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

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private record RequestIdentity(Long userId, String username, String displayName, String role) {
    }
}
