package com.hzmwmt.security;

import com.hzmwmt.domain.security.AIModelApiKeyCipher;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIModelApiKeyCipherTest {

    private static final String ENCRYPTION_SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void encryptsWithRandomNonceAndBindsCiphertextToModelId() {
        AIModelApiKeyCipher cipher = new AIModelApiKeyCipher(ENCRYPTION_SECRET);

        String first = cipher.encrypt(9005300000000000001L, "provider-secret");
        String second = cipher.encrypt(9005300000000000001L, "provider-secret");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(first).doesNotContain("provider-secret");
        assertThat(cipher.decrypt(9005300000000000001L, first))
                .isEqualTo("provider-secret");
        assertThatThrownBy(() -> cipher.decrypt(9005300000000000002L, first))
                .isInstanceOf(AppException.class);
    }

    @Test
    void refusesWritesWhenEncryptionKeyIsMissing() {
        AIModelApiKeyCipher cipher = new AIModelApiKeyCipher("");

        assertThat(cipher.isConfigured()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt(1L, "provider-secret"))
                .isInstanceOf(AppException.class);
    }
}
