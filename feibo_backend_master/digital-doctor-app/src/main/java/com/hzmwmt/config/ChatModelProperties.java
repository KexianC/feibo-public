package com.hzmwmt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ChatModelProperties
 * @Date: 2025/9/22 13:18
 */
@ConfigurationProperties(prefix = "ai")
public record ChatModelProperties(Map<String, ModelProviderProperties> models) {
}

record ModelProviderProperties(String baseUrl, String apiKey) {
}
