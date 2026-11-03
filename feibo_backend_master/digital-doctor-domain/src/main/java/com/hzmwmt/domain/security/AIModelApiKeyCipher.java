package com.hzmwmt.domain.security;

import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM envelope encryption for model credentials stored in MySQL. */
@Component
public class AIModelApiKeyCipher {
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec encryptionKey;

    public AIModelApiKeyCipher(
            @Value("${ai.model-key-encryption-secret:}") String encryptionSecret) {
        if (encryptionSecret == null || encryptionSecret.isBlank()) {
            this.encryptionKey = null;
            return;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionSecret.trim());
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("Encryption key must contain 32 bytes");
            }
            this.encryptionKey = new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "AI model credential encryption secret must be a Base64-encoded 32-byte key");
        }
    }

    public boolean isConfigured() {
        return encryptionKey != null;
    }

    public String encrypt(Long aiModelConfigId, String apiKey) {
        requireConfigured();
        if (aiModelConfigId == null || apiKey == null || apiKey.isBlank()) {
            throw illegalCredential();
        }
        byte[] iv = new byte[IV_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad(aiModelConfigId));
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return PREFIX + Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException exception) {
            throw credentialConfigurationError();
        }
    }

    public String decrypt(Long aiModelConfigId, String ciphertext) {
        requireConfigured();
        if (aiModelConfigId == null || ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw credentialConfigurationError();
        }
        try {
            byte[] envelope = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (envelope.length <= IV_BYTES) {
                throw credentialConfigurationError();
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[envelope.length - IV_BYTES];
            System.arraycopy(envelope, 0, iv, 0, IV_BYTES);
            System.arraycopy(envelope, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad(aiModelConfigId));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            // Never include ciphertext, plaintext, or provider responses in the exception.
            throw credentialConfigurationError();
        }
    }

    private byte[] aad(Long aiModelConfigId) {
        return ("ai-model-key:v1:" + aiModelConfigId).getBytes(StandardCharsets.UTF_8);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new AppException(
                    ResponseCode.MANAGE_ERROR.getCode(),
                    "AI 模型密钥加密服务未配置");
        }
    }

    private AppException illegalCredential() {
        return new AppException(ResponseCode.ILLEGAL_PARAMETER);
    }

    private AppException credentialConfigurationError() {
        return new AppException(
                ResponseCode.MANAGE_ERROR.getCode(),
                "AI 模型密钥配置不可用");
    }
}
