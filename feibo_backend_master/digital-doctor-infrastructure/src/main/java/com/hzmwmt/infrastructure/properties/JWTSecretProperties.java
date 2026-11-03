package com.hzmwmt.infrastructure.properties;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.security.KeyFactory;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName JWTSecretProperties
 * @Date: 2025/9/4 11:41
 */
@ConfigurationProperties(prefix = "jwt")
@Component
@Data
public class JWTSecretProperties implements InitializingBean {
    private final Map<SecretType, Key> keyCache = new ConcurrentHashMap<>();
    private Map<String, String> secrets;
    @DurationUnit(ChronoUnit.MINUTES)
    private Duration accessTokenDuration;
    @DurationUnit(ChronoUnit.DAYS)
    private Duration refreshTokenDuration;
    private String clientBind;
    private String tokenHead;

    @Override
    public void afterPropertiesSet() {
        if (secrets == null) {
            throw new IllegalStateException("JWT密钥配置不能为空");
        }
        for (SecretType type : SecretType.values()) {
            String encodedKey = secrets.get(type.name());
            if (!StringUtils.hasText(encodedKey)) {
                throw new IllegalStateException("缺少JWT密钥配置: " + type.name());
            }
            try {
                generateKey(type);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("JWT密钥格式无效: " + type.name(), exception);
            }
        }
        validateKeyPair(SecretType.ACCESS_PRIVATE, SecretType.ACCESS_PUBLIC);
        validateKeyPair(SecretType.REFRESH_PRIVATE, SecretType.REFRESH_PUBLIC);
        if (accessTokenDuration == null || accessTokenDuration.isZero() || accessTokenDuration.isNegative()) {
            throw new IllegalStateException("JWT accessTokenDuration必须为正数");
        }
        if (refreshTokenDuration == null || refreshTokenDuration.isZero() || refreshTokenDuration.isNegative()) {
            throw new IllegalStateException("JWT refreshTokenDuration必须为正数");
        }
        if (refreshTokenDuration.compareTo(accessTokenDuration) <= 0) {
            throw new IllegalStateException("JWT refreshTokenDuration必须长于accessTokenDuration");
        }
        if (!StringUtils.hasText(clientBind)) {
            throw new IllegalStateException("JWT clientBind配置不能为空");
        }
        if (!"Bearer".equals(tokenHead)) {
            throw new IllegalStateException("JWT tokenHead必须为Bearer");
        }
    }

    private void validateKeyPair(SecretType privateType, SecretType publicType) {
        try {
            byte[] probe = "jwt-key-pair-validation".getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign((java.security.PrivateKey) generateKey(privateType));
            signer.update(probe);

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify((java.security.PublicKey) generateKey(publicType));
            verifier.update(probe);
            if (!verifier.verify(signer.sign())) {
                throw new IllegalStateException("JWT密钥对不匹配: " + privateType.name());
            }
        } catch (GeneralSecurityException | ClassCastException exception) {
            throw new IllegalStateException("JWT密钥对校验失败: " + privateType.name(), exception);
        }
    }

    public Key getSigningKey(SecretType type) {
        return keyCache.computeIfAbsent(type, this::generateKey);
    }

    public Key generateKey(SecretType type) {
        String name = type.name();
        String base64Key = secrets.get(name);
        if (base64Key == null) {
            throw new IllegalArgumentException("在配置中未找到密钥: " + name);
        }

        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            if (name.contains("PRIVATE")) {
                return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            } else {
                return keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("从Base64字符串重建RSA密钥失败", e);
        }
    }

    public enum SecretType {
        ACCESS_PRIVATE,
        ACCESS_PUBLIC,
        REFRESH_PRIVATE,
        REFRESH_PUBLIC
    }

}
