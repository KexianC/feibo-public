package com.hzmwmt.infrastructure.properties;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static com.hzmwmt.infrastructure.properties.JWTSecretProperties.SecretType.ACCESS_PRIVATE;
import static com.hzmwmt.infrastructure.properties.JWTSecretProperties.SecretType.ACCESS_PUBLIC;
import static com.hzmwmt.infrastructure.properties.JWTSecretProperties.SecretType.REFRESH_PRIVATE;
import static com.hzmwmt.infrastructure.properties.JWTSecretProperties.SecretType.REFRESH_PUBLIC;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JWTSecretPropertiesTest {

    @Test
    void validConfigurationPassesFailFastValidation() throws Exception {
        JWTSecretProperties properties = validProperties();

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    @Test
    void missingKeyFailsAtStartup() throws Exception {
        JWTSecretProperties properties = validProperties();
        properties.getSecrets().remove(REFRESH_PUBLIC.name());

        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }

    @Test
    void mismatchedKeyPairFailsAtStartup() throws Exception {
        JWTSecretProperties properties = validProperties();
        KeyPair unrelated = generateKeyPair();
        properties.getSecrets().put(ACCESS_PUBLIC.name(), encode(unrelated.getPublic().getEncoded()));

        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }

    @Test
    void refreshLifetimeMustBeLongerThanAccessLifetime() throws Exception {
        JWTSecretProperties properties = validProperties();
        properties.setAccessTokenDuration(Duration.ofHours(1));
        properties.setRefreshTokenDuration(Duration.ofMinutes(30));

        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }

    private JWTSecretProperties validProperties() throws Exception {
        KeyPair access = generateKeyPair();
        KeyPair refresh = generateKeyPair();
        Map<String, String> secrets = new java.util.HashMap<>();
        secrets.put(ACCESS_PRIVATE.name(), encode(access.getPrivate().getEncoded()));
        secrets.put(ACCESS_PUBLIC.name(), encode(access.getPublic().getEncoded()));
        secrets.put(REFRESH_PRIVATE.name(), encode(refresh.getPrivate().getEncoded()));
        secrets.put(REFRESH_PUBLIC.name(), encode(refresh.getPublic().getEncoded()));

        JWTSecretProperties properties = new JWTSecretProperties();
        properties.setSecrets(secrets);
        properties.setAccessTokenDuration(Duration.ofMinutes(15));
        properties.setRefreshTokenDuration(Duration.ofDays(7));
        properties.setClientBind("clientBind");
        properties.setTokenHead("Bearer");
        return properties;
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
