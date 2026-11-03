package com.hzmwmt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    private String adminPasswordHash;

    /**
     * Development-only fallback. Prefer adminPasswordHash in every shared environment.
     */
    private String adminPassword;

    private int sessionTimeoutMinutes = 30;

    private int rememberMeDays = 7;

    private int loginMaxAttempts = 5;

    private int loginLockMinutes = 15;

    private String csrfHeaderName = "X-CSRF-Token";

    private List<String> allowedCorsOrigins = List.of();

    private long maxUploadBytes = 20L * 1024L * 1024L;
}
