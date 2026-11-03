package com.hzmwmt.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DigitalHumanApiProperties
 * @Date: 2025/9/24 17:55
 */
@Data
@Component
@ConfigurationProperties(prefix = "digital-human.api")
public class DigitalHumanApiProperties {
    private String appKey;
    private String appSecret;
    private String callbackUrl;
    private Duration callbackCredentialTtl = Duration.ofHours(24);
    private List<String> resultAllowedHosts = List.of();
    private long maxResultBytes = 512L * 1024 * 1024;
    private String callbackDurationUnit = "milliseconds";
}
