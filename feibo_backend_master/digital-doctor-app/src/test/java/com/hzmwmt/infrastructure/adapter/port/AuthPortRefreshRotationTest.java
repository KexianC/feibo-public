package com.hzmwmt.infrastructure.adapter.port;

import com.hzmwmt.domain.auth.model.entity.RotatedTokenPairEntity;
import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.service.factory.JwtValidationRuleFilterFactory;
import com.hzmwmt.infrastructure.gateway.security.RefreshTokenRotationStore;
import com.hzmwmt.infrastructure.properties.JWTSecretProperties;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.hzmwmt.infrastructure.properties.JWTSecretProperties.SecretType.ACCESS_PRIVATE;
import static com.hzmwmt.infrastructure.properties.JWTSecretProperties.SecretType.ACCESS_PUBLIC;
import static com.hzmwmt.infrastructure.properties.JWTSecretProperties.SecretType.REFRESH_PRIVATE;
import static com.hzmwmt.infrastructure.properties.JWTSecretProperties.SecretType.REFRESH_PUBLIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthPortRefreshRotationTest {

    @Mock
    private RefreshTokenRotationStore rotationStore;

    private AuthPort authPort;
    private JWTSecretProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        properties = validProperties();
        properties.afterPropertiesSet();
        authPort = new AuthPort();
        ReflectionTestUtils.setField(authPort, "jwtSecretProperties", properties);
        ReflectionTestUtils.setField(authPort, "refreshTokenRotationStore", rotationStore);
    }

    @Test
    void legitimateRotationPreservesStableFamilyAbsoluteExpiryAndMfaAssurance() {
        when(rotationStore.initializeFamily(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        String initial = authPort.generateRefreshToken(1001L, "device-a", true);
        Claims oldClaims = parse(initial, REFRESH_PUBLIC);
        when(rotationStore.consumeAndRotate(
                eq(oldClaims.get("refresh_family", String.class)),
                eq(oldClaims.getId()),
                anyString(),
                any(Duration.class),
                eq(false)))
                .thenReturn(RefreshTokenRotationStore.RotationResult.ROTATED);

        RotatedTokenPairEntity rotated = authPort.rotateRefreshToken(initial, 1001L, "device-a");
        Claims newClaims = parse(rotated.getRefreshToken(), REFRESH_PUBLIC);

        assertNotNull(rotated.getAccessToken());
        assertEquals(oldClaims.get("refresh_family"), newClaims.get("refresh_family"));
        assertEquals(oldClaims.getExpiration(), newClaims.getExpiration());
        assertNotEquals(oldClaims.getId(), newClaims.getId());
        Claims newAccessClaims = parse(rotated.getAccessToken(), ACCESS_PUBLIC);
        assertEquals("1001", newAccessClaims.getSubject());
        assertTrue(oldClaims.get("mfa_verified", Boolean.class));
        assertTrue(newClaims.get("mfa_verified", Boolean.class));
        assertTrue(newAccessClaims.get("mfa_verified", Boolean.class));
    }

    @Test
    void ordinaryRefreshRotationDoesNotInventMfaAssurance() {
        when(rotationStore.initializeFamily(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        String initial = authPort.generateRefreshToken(1001L, "device-a", false);
        Claims oldClaims = parse(initial, REFRESH_PUBLIC);
        when(rotationStore.consumeAndRotate(
                eq(oldClaims.get("refresh_family", String.class)),
                eq(oldClaims.getId()),
                anyString(),
                any(Duration.class),
                eq(false)))
                .thenReturn(RefreshTokenRotationStore.RotationResult.ROTATED);

        RotatedTokenPairEntity rotated = authPort.rotateRefreshToken(initial, 1001L, "device-a");

        assertFalse(parse(rotated.getRefreshToken(), REFRESH_PUBLIC)
                .get("mfa_verified", Boolean.class));
        assertFalse(parse(rotated.getAccessToken(), ACCESS_PUBLIC)
                .get("mfa_verified", Boolean.class));
    }

    @Test
    void replayDecisionFailsClosedWithoutReturningAnotherPair() {
        when(rotationStore.initializeFamily(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        String initial = authPort.generateRefreshToken(1001L, "device-a", false);
        Claims claims = parse(initial, REFRESH_PUBLIC);
        when(rotationStore.consumeAndRotate(
                eq(claims.get("refresh_family", String.class)),
                eq(claims.getId()),
                anyString(),
                any(Duration.class),
                eq(false)))
                .thenReturn(RefreshTokenRotationStore.RotationResult.REPLAY_REVOKED);

        AppException exception = assertThrows(
                AppException.class,
                () -> authPort.rotateRefreshToken(initial, 1001L, "device-a"));

        assertEquals(ResponseCode.REFRESH_TOKEN_EXCEPTION.getCode(), exception.getCode());
    }

    @Test
    void tokenCannotBeRotatedForAnotherPrincipalOrFingerprint() {
        when(rotationStore.initializeFamily(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        String initial = authPort.generateRefreshToken(1001L, "device-a", false);

        assertThrows(AppException.class, () -> authPort.rotateRefreshToken(initial, 2002L, "device-a"));
        assertThrows(AppException.class, () -> authPort.rotateRefreshToken(initial, 1001L, "device-b"));
        verify(rotationStore, never()).consumeAndRotate(
                anyString(), anyString(), anyString(), any(Duration.class), anyBoolean());
    }

    @Test
    void signatureValidationExtractsSignedMfaAssurance() {
        String token = authPort.generateAccessToken(1001L, "device-a", true);
        JwtRuleCommandEntity command = JwtRuleCommandEntity.builder()
                .token(token)
                .clientFingerprint("device-a")
                .tokenTypeEnumVO(JwtValidationRuleFilterFactory.TokenTypeEnumVO.ACCESS)
                .build();

        assertEquals(1001L, authPort.signatureValidation(command));
        assertTrue(command.isMfaVerified());
    }

    @Test
    void legacyAccessTokenWithoutMfaClaimIsTreatedAsUnassured() {
        Instant now = Instant.now();
        String legacyToken = Jwts.builder()
                .setId("legacy-access")
                .setSubject("1001")
                .claim(properties.getClientBind(), "device-a")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .signWith(properties.getSigningKey(ACCESS_PRIVATE), io.jsonwebtoken.SignatureAlgorithm.RS256)
                .compact();
        JwtRuleCommandEntity command = JwtRuleCommandEntity.builder()
                .token(legacyToken)
                .clientFingerprint("device-a")
                .tokenTypeEnumVO(JwtValidationRuleFilterFactory.TokenTypeEnumVO.ACCESS)
                .build();

        assertEquals(1001L, authPort.signatureValidation(command));
        assertFalse(command.isMfaVerified());
    }

    private Claims parse(String token, JWTSecretProperties.SecretType keyType) {
        return Jwts.parserBuilder()
                .setSigningKey(properties.getSigningKey(keyType))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private JWTSecretProperties validProperties() throws Exception {
        KeyPair access = generateKeyPair();
        KeyPair refresh = generateKeyPair();
        Map<String, String> secrets = new HashMap<>();
        secrets.put(ACCESS_PRIVATE.name(), encode(access.getPrivate().getEncoded()));
        secrets.put(ACCESS_PUBLIC.name(), encode(access.getPublic().getEncoded()));
        secrets.put(REFRESH_PRIVATE.name(), encode(refresh.getPrivate().getEncoded()));
        secrets.put(REFRESH_PUBLIC.name(), encode(refresh.getPublic().getEncoded()));

        JWTSecretProperties result = new JWTSecretProperties();
        result.setSecrets(secrets);
        result.setAccessTokenDuration(Duration.ofMinutes(15));
        result.setRefreshTokenDuration(Duration.ofDays(7));
        result.setClientBind("clientBind");
        result.setTokenHead("Bearer");
        return result;
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
