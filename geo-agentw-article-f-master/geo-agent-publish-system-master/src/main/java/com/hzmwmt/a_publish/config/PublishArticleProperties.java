package com.hzmwmt.a_publish.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.publish")
public class PublishArticleProperties {

    private String baseUrl;

    private String internalToken;
}
