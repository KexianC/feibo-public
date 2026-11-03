package com.hzmwmt.config;

import com.hzmwmt.interceptor.LoginInterceptor;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "app.geo-integration", name = "enabled", havingValue = "false")
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;

    @Resource
    private AppSecurityProperties securityProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/index.html",
                        "/favicon.ico",
                        "/error",
                        "/login",
                        "/login/status",
                        "/ai-bot/callback/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/assets/**",
                        "/webjars/**"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (CollectionUtils.isEmpty(securityProperties.getAllowedCorsOrigins())) {
            return;
        }
        List<String> allowedOrigins = securityProperties.getAllowedCorsOrigins().stream()
                .filter(StringUtils::hasText)
                .toList();
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", securityProperties.getCsrfHeaderName())
                .allowCredentials(true)
                .maxAge(3600);
    }
}
