package com.hzmwmt.integration.geo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;
import java.util.function.Predicate;

@Configuration
@ConditionalOnProperty(prefix = "app.geo-integration", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GeoIntegrationProperties.class)
@MapperScan(basePackages = {
        "com.hzmwmt.mapper",
        "com.hzmwmt.geo.mapper",
        "com.hzmwmt.a_publish.mapper",
        "com.hzmwmt.ai.rag.mapper"
})
public class GeoIntegrationWebMvcConfig implements WebMvcConfigurer {

    private static final Set<String> GEO_CONTROLLER_PACKAGES = Set.of(
            "com.hzmwmt.controller",
            "com.hzmwmt.geo.controller",
            "com.hzmwmt.a_publish.controller",
            "com.hzmwmt.ai.rag.controller"
    );

    private final GeoIntegrationProperties properties;
    private final GeoHostAuthenticationInterceptor hostAuthenticationInterceptor;

    public GeoIntegrationWebMvcConfig(GeoIntegrationProperties properties) {
        this.properties = properties;
        this.hostAuthenticationInterceptor = new GeoHostAuthenticationInterceptor(properties);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        Predicate<Class<?>> geoController = handlerType -> GEO_CONTROLLER_PACKAGES.stream()
                .anyMatch(packageName -> handlerType.getPackageName().equals(packageName));
        configurer.addPathPrefix(properties.getApiPrefix(), geoController);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(hostAuthenticationInterceptor)
                .addPathPatterns(properties.getApiPrefix() + "/**");
    }
}
