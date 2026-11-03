package com.hzmwmt.security;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.auth.model.aggregate.AuthUserAggregate;
import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.auth.service.factory.JwtValidationRuleFilterFactory;
import com.hzmwmt.domain.service.role.AdminPermissionService;
import com.hzmwmt.types.common.Constants;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.enums.RoleCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName JwtAuthenticationFilter
 * @Date: 2025/8/6 0:56
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String ROTATING_REFRESH_PATH = "/api/v1/auth/refresh/rotate";

    private final IAuthService authService;
    private final GrantedAuthoritiesMapper grantedAuthoritiesMapper;
    private final AdminPermissionService permissionService;

    // 将常量数组转换为 AntPathRequestMatcher 列表，以支持通配符 **
    private final List<AntPathRequestMatcher> publicMatchers;

    public JwtAuthenticationFilter(
            IAuthService authService,
            GrantedAuthoritiesMapper grantedAuthoritiesMapper,
            AdminPermissionService permissionService) {
        this.authService = authService;
        this.grantedAuthoritiesMapper = grantedAuthoritiesMapper;
        this.permissionService = permissionService;
        this.publicMatchers = Arrays.stream(Constants.AuthConstants.PUBLIC_ENDPOINTS)
                .map(AntPathRequestMatcher::new)
                .collect(Collectors.toList());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // 先判断是否需要放行 /api/admin/**
        if (validNotFilter(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        // 从请求头中获取JWT
        String token = extractTokenFromRequest(request);
        String fingerprint = request.getHeader("client-fingerprint");
        try {
            JwtRuleCommandEntity build = JwtRuleCommandEntity.builder()
                    .token(token)
                    .clientFingerprint(fingerprint)
                    .build();
            if (isRefreshTokenRequest(request)) {
                build.setTokenTypeEnumVO(JwtValidationRuleFilterFactory.TokenTypeEnumVO.REFRESH);
            } else {
                build.setTokenTypeEnumVO(JwtValidationRuleFilterFactory.TokenTypeEnumVO.ACCESS);
            }
            AuthUserAggregate authUserAggregate = authService.validateToken(build);
            List<GrantedAuthority> directAuthorities = new ArrayList<>();
            directAuthorities.addAll(authUserAggregate.getRoleEntityList().stream()
                    .map(roleEntity -> RoleCode.canonicalize(roleEntity.getName()))
                    .flatMap(java.util.Optional::stream)
                    .map(roleCode -> new SimpleGrantedAuthority("ROLE_" + roleCode))
                    .toList());
            directAuthorities.addAll(permissionService.getPermissionCodesByUserId(
                            authUserAggregate.getAuthUserEntity().getUserId()).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList());
            Collection<? extends GrantedAuthority> effectiveAuthorities =
                    grantedAuthoritiesMapper.mapAuthorities(directAuthorities);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            authUserAggregate.getAuthUserEntity(),
                            fingerprint,
                            effectiveAuthorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("用户 {} 通过JWT认证成功。", authUserAggregate.getAuthUserEntity().getUserId());

        } catch (Exception e) {
            // 清理上下文
            SecurityContextHolder.clearContext();
            String failureCode = e instanceof AppException appException
                    ? appException.getCode()
                    : ResponseCode.SERVICE_ERROR.getCode();
            log.warn("JWT认证失败: method={}, path={}, code={}, errorType={}",
                    request.getMethod(), request.getRequestURI(), failureCode,
                    e.getClass().getSimpleName());
            // 直接向客户端返回错误响应，不再继续执行过滤器链
            handleException(response, e);
            //中断链条
            return;
        }
        // 认证成功的情况下，继续执行过滤器链
        filterChain.doFilter(request, response);
    }

    boolean isRefreshTokenRequest(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        return "POST".equalsIgnoreCase(request.getMethod())
                && ROTATING_REFRESH_PATH.equals(requestPath);
    }

//        private boolean validNotFilter(HttpServletRequest request) {
//            // 1. 获取排除上下文路径的纯接口路径（如 /api/admin/list，不含项目部署路径）
//            String servletPath = request.getServletPath();
//
//            // 2. 用 Spring 提供的 AntPathRequestMatcher 匹配 /api/admin/ 及其所有子路径
//            // 支持：/api/admin、/api/admin/、/api/admin/xxx、/api/admin/xxx/yyy 等所有情况
//            AntPathRequestMatcher adminMatcher = new AntPathRequestMatcher("/api/admin/**");
//
//
//            // 3. 若匹配 /api/admin/**，直接放行
//            if (adminMatcher.matches(request)) {
//                return true;
//            }
//
//            // 保留原有公开接口的放行逻辑（不变）
//            return publicMatchers.stream().anyMatch(matcher -> matcher.matches(request));
//        }

    private boolean validNotFilter(HttpServletRequest request) {
        // 1. 直接获取请求的完整路径（包含上下文，避免 servletPath 截断问题）
        String requestURI = request.getRequestURI();
        // 2. 定义 Swagger 所有需要放行的路径（用通配符 * 匹配）
        List<String> swaggerPaths = Arrays.asList(
                "/swagger-ui.html",
                "/swagger-ui/*",
                "/v3/api-docs",
                "/v3/api-docs/*",
                "/favicon.ico"
        );

        // 3. 优先判断：如果是 Swagger 路径，直接放行（不进入 JWT 认证）
        for (String path : swaggerPaths) {
            // 用 AntPathMatcher 匹配路径（支持通配符，避免硬编码错误）
            AntPathMatcher matcher = new AntPathMatcher();
            if (matcher.match(path, requestURI)) {
                log.info("放行 Swagger 请求：{}", requestURI); // 加日志，验证是否命中
                return true;
            }
        }

        // 4. 原有逻辑：放行 PUBLIC_ENDPOINTS 中的路径
        return publicMatchers.stream()
                .anyMatch(matcher -> matcher.matches(request));
    }


    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void handleException(HttpServletResponse response, Exception e) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> errorDetails = new HashMap<>();
        boolean authenticationRejected = e instanceof AppException appException
                && !isTransientInfrastructureFailure(appException);
        if (authenticationRejected) {
            AppException appException = (AppException) e;
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            errorDetails.put("code", appException.getCode());
            errorDetails.put("info", appException.getInfo());
        } else {
            // Redis/database uncertainty does not prove that the credential is
            // invalid. Return a retryable 503 so clients keep the local session.
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            errorDetails.put("code", ResponseCode.SERVICE_ERROR.getCode());
            errorDetails.put("info", "认证服务暂时不可用，请稍后重试");
        }
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(errorDetails));
    }

    private boolean isTransientInfrastructureFailure(AppException exception) {
        return ResponseCode.INTERNET_EXCEPTION.getCode().equals(exception.getCode())
                || ResponseCode.SERVICE_ERROR.getCode().equals(exception.getCode())
                || ResponseCode.HTTP_EXCEPTION.getCode().equals(exception.getCode())
                || ResponseCode.REMOTE_REQUEST_EXCEPTION.getCode().equals(exception.getCode());
    }
}
