package com.hzmwmt.config;

import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.service.role.AdminPermissionService;
import com.hzmwmt.security.JwtAuthenticationFilter;
import com.hzmwmt.types.common.Constants;
import jakarta.annotation.Resource;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyAuthoritiesMapper;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName SpringSecurityConfig
 * @Date: 2025/9/4 22:24
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SpringSecurityConfig {
    @Resource
    private IAuthService authService;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GrantedAuthoritiesMapper grantedAuthoritiesMapper,
            AdminPermissionService permissionService,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                // 禁用所有不需要的安全功能
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // 配置授权规则
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(Constants.AuthConstants.PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/admin/ai-models/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/model-failure-notices/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/video-generation/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/geo-agent-api/**").hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                // 添加JWT过滤器 偏移量+锚点
                .addFilterBefore(
                        new JwtAuthenticationFilter(authService, grantedAuthoritiesMapper, permissionService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Platform super administrators inherit every ordinary administrator capability.
     * Authorities are expanded when a JWT is authenticated so the hierarchy is also
     * honored by method security and code that checks ROLE_ADMIN directly.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_SUPER_ADMIN > ROLE_ADMIN");
    }

    @Bean
    public GrantedAuthoritiesMapper grantedAuthoritiesMapper(RoleHierarchy roleHierarchy) {
        return new RoleHierarchyAuthoritiesMapper(roleHierarchy);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(HttpCorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.validatedAllowedOrigins());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of(
                "Accept",
                "Authorization",
                "Content-Type",
                "Client-Fingerprint",
                "Idempotency-Token",
                "Last-Event-ID"));
        configuration.setExposedHeaders(List.of("X-Conversation-Id"));
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // 普通HTTP请求配置
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
