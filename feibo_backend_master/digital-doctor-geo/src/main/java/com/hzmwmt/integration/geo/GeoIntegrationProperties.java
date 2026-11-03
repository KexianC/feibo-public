package com.hzmwmt.integration.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.geo-integration")
public class GeoIntegrationProperties {

    private boolean enabled;
    private String apiPrefix = "/geo-agent-api";
    private String requiredRole = "SUPER_ADMIN";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiPrefix() {
        return normalizePrefix(apiPrefix);
    }

    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(String requiredRole) {
        this.requiredRole = requiredRole;
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank() || "/".equals(value.trim())) {
            return "/geo-agent-api";
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
