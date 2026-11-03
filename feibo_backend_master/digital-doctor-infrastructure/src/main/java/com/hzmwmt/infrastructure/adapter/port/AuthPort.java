package com.hzmwmt.infrastructure.adapter.port;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hzmwmt.domain.auth.adapter.port.IAuthPort;
import com.hzmwmt.domain.auth.model.entity.JwtRuleCommandEntity;
import com.hzmwmt.domain.auth.model.entity.RevokeTokenEntity;
import com.hzmwmt.domain.auth.model.entity.RotatedTokenPairEntity;
import com.hzmwmt.domain.auth.service.factory.JwtValidationRuleFilterFactory;
import com.hzmwmt.infrastructure.dao.UserMapper;
import com.hzmwmt.infrastructure.dao.po.User;
import com.hzmwmt.infrastructure.gateway.sms.ISMSGateway;
import com.hzmwmt.infrastructure.gateway.security.AuthAbuseProtectionService;
import com.hzmwmt.infrastructure.gateway.security.RefreshTokenRotationStore;
import com.hzmwmt.infrastructure.properties.JWTSecretProperties;
import com.hzmwmt.infrastructure.properties.JWTSecretProperties.SecretType;
import com.hzmwmt.infrastructure.redis.IRedisService;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import io.jsonwebtoken.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static com.hzmwmt.types.common.Constants.RedisConstants.KEY_AUTH_CODE_PREFIX;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AuthPort
 * @Date: 2025/9/4 10:26
 */
@Slf4j
@Service
public class AuthPort implements IAuthPort {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String MFA_VERIFIED_CLAIM = "mfa_verified";
    private static final String REFRESH_FAMILY_CLAIM = "refresh_family";
    private static final String REFRESH_FAMILY_EXPIRY_CLAIM = "refresh_family_exp";
    private static final int FAMILY_INITIALIZATION_ATTEMPTS = 3;

    @Resource
    private IRedisService redissonService;
    @Resource
    private JWTSecretProperties jwtSecretProperties;
    @Resource
    private ISMSGateway smsGateway;
    @Resource
    private UserMapper userMapper;
    @Resource
    private AuthAbuseProtectionService authAbuseProtectionService;
    @Resource
    private RefreshTokenRotationStore refreshTokenRotationStore;

    @Override
    public Long signatureValidation(JwtRuleCommandEntity command) {
        SecretType secretType;
        if (command.getTokenTypeEnumVO() == JwtValidationRuleFilterFactory.TokenTypeEnumVO.REFRESH) {
            secretType = SecretType.REFRESH_PUBLIC;
        } else {
            secretType = SecretType.ACCESS_PUBLIC;
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSecretProperties.getSigningKey(secretType))
                    .build()
                    .parseClaimsJws(command.getToken())
                    .getBody();
            String userId = claims.getSubject();
            String tokenFingerprint = claims.get(jwtSecretProperties.getClientBind(), String.class);
            command.setMfaVerified(Boolean.TRUE.equals(
                    claims.get(MFA_VERIFIED_CLAIM, Boolean.class)));
            if (!Objects.equals(tokenFingerprint, command.getClientFingerprint())) {
                log.warn("JWT校验链：客户端指纹异常。");
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
            }
            log.info("JWT校验链 - 签名和格式校验通过，用户ID: {}", userId);
            if (userId == null) {
                log.warn("JWT校验链：Token中未包含用户ID。");
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
            }
            return Long.valueOf(userId);
        } catch (ExpiredJwtException e) {
            log.warn("JWT校验链 - 令牌已过期。");
            throw new AppException(ResponseCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT校验链 - 无效的Token（签名或格式错误），异常类型: {}",
                    e.getClass().getSimpleName());
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER);
        }
    }

    @Override
    public void blacklistValidation(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtSecretProperties.getSigningKey(SecretType.REFRESH_PUBLIC))
                .build()
                .parseClaimsJws(token)
                .getBody();
        Duration remaining = remaining(claims.getExpiration().toInstant());
        String familyId = claims.get(REFRESH_FAMILY_CLAIM, String.class);
        if (!refreshTokenRotationStore.validate(familyId, claims.getId(), remaining)) {
            log.warn("JWT校验链 (Refresh) - refresh token已消费、撤销或family状态异常。");
            throw new AppException(ResponseCode.REFRESH_TOKEN_EXCEPTION);
        }
        log.info("JWT校验链 (Refresh) - refreshToken状态校验通过");
    }

    @Override
    public String generateAccessToken(Long userId, String clientFingerprint, boolean mfaVerified) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setId(String.valueOf(IdWorker.getId()))
                .setSubject(userId.toString())
                .claim(jwtSecretProperties.getClientBind(), clientFingerprint)
                .claim(MFA_VERIFIED_CLAIM, mfaVerified)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(jwtSecretProperties.getAccessTokenDuration())))
                .signWith(jwtSecretProperties.getSigningKey(SecretType.ACCESS_PRIVATE), SignatureAlgorithm.RS256)
                .compact();
    }

    @Override
    public String generateRefreshToken(Long userId, String clientFingerprint, boolean mfaVerified) {
        Instant now = Instant.now();
        Instant familyExpiresAt = now.plus(jwtSecretProperties.getRefreshTokenDuration());
        Duration ttl = remaining(familyExpiresAt);
        try {
            for (int attempt = 0; attempt < FAMILY_INITIALIZATION_ATTEMPTS; attempt++) {
                String familyId = UUID.randomUUID().toString().replace("-", "");
                String jti = String.valueOf(IdWorker.getId());
                String token = buildRefreshToken(
                        userId,
                        clientFingerprint,
                        familyId,
                        familyExpiresAt,
                        jti,
                        now,
                        mfaVerified);
                if (refreshTokenRotationStore.initializeFamily(familyId, jti, ttl)) {
                    return token;
                }
            }
        } catch (RuntimeException exception) {
            log.error("初始化refresh token family失败，异常类型: {}, 根因类型: {}, 原因分类: {}",
                    exception.getClass().getSimpleName(),
                    rootCauseType(exception),
                    classifyRedisFailure(exception));
            throw new AppException(ResponseCode.INTERNET_EXCEPTION);
        }
        throw new AppException(ResponseCode.SERVICE_ERROR);
    }

    @Override
    public RotatedTokenPairEntity rotateRefreshToken(
            String refreshToken,
            Long userId,
            String clientFingerprint) {
        try {
            Claims claims = parseRefreshClaims(refreshToken);
            if (!Objects.equals(claims.getSubject(), String.valueOf(userId))) {
                throw new AppException(ResponseCode.REFRESH_TOKEN_EXCEPTION);
            }
            String tokenFingerprint = claims.get(jwtSecretProperties.getClientBind(), String.class);
            if (!Objects.equals(tokenFingerprint, clientFingerprint)) {
                throw new AppException(ResponseCode.REFRESH_TOKEN_EXCEPTION);
            }

            String oldJti = claims.getId();
            if (oldJti == null || oldJti.isBlank()) {
                throw new AppException(ResponseCode.REFRESH_TOKEN_EXCEPTION);
            }
            String familyId = claims.get(REFRESH_FAMILY_CLAIM, String.class);
            boolean legacyToken = familyId == null || familyId.isBlank();
            if (legacyToken) {
                // A pre-rotation token gets a deterministic, per-token family
                // and may bootstrap that family exactly once in Redis.
                familyId = legacyFamilyId(oldJti);
            }

            Instant familyExpiresAt = resolveFamilyExpiry(claims, legacyToken);
            boolean mfaVerified = Boolean.TRUE.equals(
                    claims.get(MFA_VERIFIED_CLAIM, Boolean.class));
            Duration ttl = remaining(familyExpiresAt);
            Instant now = Instant.now();
            String newJti = String.valueOf(IdWorker.getId());
            String nextRefreshToken = buildRefreshToken(
                    userId,
                    clientFingerprint,
                    familyId,
                    familyExpiresAt,
                    newJti,
                    now,
                    mfaVerified);
            String nextAccessToken = generateAccessToken(userId, clientFingerprint, mfaVerified);

            RefreshTokenRotationStore.RotationResult result =
                    refreshTokenRotationStore.consumeAndRotate(
                            familyId,
                            oldJti,
                            newJti,
                            ttl,
                            legacyToken);
            if (result != RefreshTokenRotationStore.RotationResult.ROTATED) {
                log.warn("refresh token轮换被拒绝，状态={}", result);
                throw new AppException(ResponseCode.REFRESH_TOKEN_EXCEPTION);
            }
            return RotatedTokenPairEntity.builder()
                    .accessToken(nextAccessToken)
                    .refreshToken(nextRefreshToken)
                    .build();
        } catch (AppException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("refresh token轮换请求无效");
            throw new AppException(ResponseCode.REFRESH_TOKEN_EXCEPTION);
        } catch (RuntimeException exception) {
            // Redis uncertainty must never produce another usable refresh
            // token. The caller receives a transient failure and keeps its
            // local session for a later explicit retry.
            log.error("refresh token轮换状态存储失败，异常类型: {}, 根因类型: {}, 原因分类: {}",
                    exception.getClass().getSimpleName(),
                    rootCauseType(exception),
                    classifyRedisFailure(exception));
            throw new AppException(ResponseCode.INTERNET_EXCEPTION);
        }
    }

    @Override
    public void revokeRefreshToken(RevokeTokenEntity revokeTokenEntity) {
        try {
            Claims claims = parseRefreshClaims(revokeTokenEntity.getToken());
            if (!Objects.equals(claims.getSubject(), String.valueOf(revokeTokenEntity.getUserId()))) {
                throw new AppException(ResponseCode.REFRESH_TOKEN_EXCEPTION);
            }
            Duration remaining = Duration.between(Instant.now(), claims.getExpiration().toInstant());
            if (remaining.isZero() || remaining.isNegative()) {
                log.info("尝试撤销一个即将过期的Refresh Token，无需操作。");
                return;
            }
            refreshTokenRotationStore.revoke(
                    claims.get(REFRESH_FAMILY_CLAIM, String.class),
                    claims.getId(),
                    remaining);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception e) {
            log.warn("撤销Refresh Token失败");
            throw new AppException(ResponseCode.INTERNET_EXCEPTION);
        }
    }

    private String buildRefreshToken(
            Long userId,
            String clientFingerprint,
            String familyId,
            Instant familyExpiresAt,
            String jti,
            Instant issuedAt,
            boolean mfaVerified) {
        return Jwts.builder()
                .setId(jti)
                .setSubject(userId.toString())
                .claim(jwtSecretProperties.getClientBind(), clientFingerprint)
                .claim(MFA_VERIFIED_CLAIM, mfaVerified)
                .claim(REFRESH_FAMILY_CLAIM, familyId)
                .claim(REFRESH_FAMILY_EXPIRY_CLAIM, familyExpiresAt.getEpochSecond())
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(familyExpiresAt))
                .signWith(jwtSecretProperties.getSigningKey(SecretType.REFRESH_PRIVATE), SignatureAlgorithm.RS256)
                .compact();
    }

    private Claims parseRefreshClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(jwtSecretProperties.getSigningKey(SecretType.REFRESH_PUBLIC))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private String rootCauseType(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private String classifyRedisFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("crossslot") || normalized.contains("same slot")) {
                    return "REDIS_CROSS_SLOT";
                }
                if (normalized.contains("noauth") || normalized.contains("wrongpass")
                        || normalized.contains("permission") || normalized.contains("noperm")) {
                    return "REDIS_AUTH_OR_PERMISSION";
                }
                if (normalized.contains("timeout") || normalized.contains("timed out")) {
                    return "REDIS_TIMEOUT";
                }
                if (normalized.contains("connection") || normalized.contains("unreachable")) {
                    return "REDIS_CONNECTION";
                }
            }
            current = current.getCause();
        }
        return "REDIS_OPERATION_FAILED";
    }

    private Instant resolveFamilyExpiry(Claims claims, boolean legacyToken) {
        if (legacyToken) {
            return claims.getExpiration().toInstant();
        }
        Number epochSeconds = claims.get(REFRESH_FAMILY_EXPIRY_CLAIM, Number.class);
        if (epochSeconds == null) {
            throw new AppException(ResponseCode.REFRESH_TOKEN_EXCEPTION);
        }
        Instant familyExpiresAt = Instant.ofEpochSecond(epochSeconds.longValue());
        if (familyExpiresAt.isAfter(claims.getExpiration().toInstant().plusSeconds(1))) {
            throw new AppException(ResponseCode.REFRESH_TOKEN_EXCEPTION);
        }
        return familyExpiresAt;
    }

    private Duration remaining(Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new AppException(ResponseCode.TOKEN_EXPIRED);
        }
        return remaining;
    }

    private String legacyFamilyId(String jti) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("legacy:" + jti).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    @Override
    public String queryTokenHead() {
        return jwtSecretProperties.getTokenHead();
    }

    @Override
    public void sendAuthShortMessage(String phoneNumber) {
        // 检查手机号是否存在于数据库中
        User user = userMapper.selectUserByPhoneNumber(phoneNumber);
        if (user == null) {
            // Keep the public send endpoint response indistinguishable so it
            // cannot be used as an account-enumeration oracle.
            return;
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String content = String.format("{\"code\":\"%s\"}", code);
        smsGateway.sendShortMessage(phoneNumber, content);
        redissonService.setValue(KEY_AUTH_CODE_PREFIX + phoneNumber, code, 5);
        authAbuseProtectionService.clearVerificationAttempts(phoneNumber);
    }

    @Override
    public void authUserByVerificationCode(String phoneNumber, String code) {
        authAbuseProtectionService.assertVerificationAttemptAllowed(phoneNumber);
        String key = KEY_AUTH_CODE_PREFIX + phoneNumber;
        String redisCode = redissonService.getValue(key);
        if (redisCode == null) {
            log.warn("验证码已过期");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "验证码已过期请重新发送");
        }
        if (!MessageDigest.isEqual(
                code.getBytes(StandardCharsets.UTF_8),
                redisCode.getBytes(StandardCharsets.UTF_8))) {
            log.warn("验证码错误");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "验证码错误");
        }
        redissonService.remove(key);
        authAbuseProtectionService.clearVerificationAttempts(phoneNumber);
    }

}
