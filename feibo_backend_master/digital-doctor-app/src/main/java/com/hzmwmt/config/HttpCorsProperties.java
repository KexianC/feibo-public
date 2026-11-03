package com.hzmwmt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Exact browser origins that may call the HTTP API cross-origin.
 *
 * <p>Patterns and wildcards are intentionally unsupported. WebSocket origins
 * are validated independently by the Netty handshake and do not use this
 * configuration.</p>
 */
@Component
@ConfigurationProperties(prefix = "app.security.cors")
public class HttpCorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();
    private boolean allowCredentials = true;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    /**
     * Validates that every entry is a complete, exact HTTP(S) origin. Invalid
     * configuration fails startup instead of silently widening browser access.
     */
    public List<String> validatedAllowedOrigins() {
        Set<String> validated = new LinkedHashSet<>();
        for (String configuredOrigin : allowedOrigins) {
            String origin = configuredOrigin == null ? "" : configuredOrigin.trim();
            if (origin.isEmpty()) {
                continue;
            }
            validateExactOrigin(origin);
            validated.add(origin);
        }
        if (validated.isEmpty()) {
            throw new IllegalStateException("app.security.cors.allowed-origins must contain at least one exact origin");
        }
        return List.copyOf(validated);
    }

    private static void validateExactOrigin(String origin) {
        if (origin.contains("*") || "null".equalsIgnoreCase(origin)) {
            throw invalidOrigin(origin);
        }

        try {
            URI uri = new URI(origin);
            String scheme = uri.getScheme();
            String path = uri.getRawPath();
            int port = uri.getPort();
            if (scheme == null
                    || !("http".equals(scheme.toLowerCase(Locale.ROOT))
                    || "https".equals(scheme.toLowerCase(Locale.ROOT)))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || (path != null && !path.isEmpty())
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || port > 65535) {
                throw invalidOrigin(origin);
            }
        } catch (URISyntaxException exception) {
            throw invalidOrigin(origin);
        }
    }

    private static IllegalStateException invalidOrigin(String origin) {
        return new IllegalStateException(
                "app.security.cors.allowed-origins only accepts exact HTTP(S) origins: " + origin);
    }
}
