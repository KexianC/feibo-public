package com.hzmwmt.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Distributed abuse-protection limits for public authentication endpoints.
 *
 * <p>The limits are deliberately enforced by the server and do not depend on
 * the caller-controlled idempotency token.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security.auth-protection")
public class AuthProtectionProperties {

    private int smsPhonePerHour = 5;
    private int smsAddressPerHour = 30;
    private Duration smsCooldown = Duration.ofSeconds(60);

    private int loginPhonePerWindow = 5;
    private int loginAddressPerWindow = 60;
    private Duration loginWindow = Duration.ofMinutes(15);

    private int verificationAttempts = 5;
    private Duration verificationWindow = Duration.ofMinutes(5);
}
