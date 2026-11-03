package com.hzmwmt.security;

import com.hzmwmt.domain.security.AIModelApiKeyCipher;
import com.hzmwmt.infrastructure.dao.AIModelConfigMapper;
import com.hzmwmt.infrastructure.dao.po.AIModelConfig;
import com.hzmwmt.infrastructure.gateway.ai.DatabaseBackedChatModelRegistry;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseBackedChatModelRegistryTest {

    private static final long MODEL_ID = 9005300000000000001L;
    private AIModelConfigMapper mapper;
    private AIModelApiKeyCipher cipher;
    private DatabaseBackedChatModelRegistry registry;

    @BeforeEach
    void setUp() {
        mapper = mock(AIModelConfigMapper.class);
        cipher = new AIModelApiKeyCipher(Base64.getEncoder()
                .encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("ai.models.kimik3.base-url", "https://api.moonshot.cn/v1")
                .withProperty("ai.models.kimik3.api-key", "environment-fallback");
        registry = new DatabaseBackedChatModelRegistry(
                mapper,
                cipher,
                environment,
                RestClient.builder(),
                WebClient.builder());
    }

    @Test
    void databaseOverridesAreNotCachedSoRotationsTakeEffectAcrossInstances() {
        AIModelConfig config = visibleKimi();
        config.setApiKeyCiphertext(cipher.encrypt(MODEL_ID, "database-key"));
        when(mapper.selectAiModelRuntimeConfigByAiModelConfigId(MODEL_ID))
                .thenReturn(config);

        ChatModel first = registry.resolve(MODEL_ID, "kimik3");
        ChatModel second = registry.resolve(MODEL_ID, "kimik3");

        assertThat(first).isNotSameAs(second);
    }

    @Test
    void environmentFallbackIsCachedButHiddenOrMismatchedModelsFailClosed() {
        AIModelConfig config = visibleKimi();
        when(mapper.selectAiModelRuntimeConfigByAiModelConfigId(MODEL_ID))
                .thenReturn(config);

        assertThat(registry.resolve(MODEL_ID, "kimik3"))
                .isSameAs(registry.resolve(MODEL_ID, "kimik3"));
        assertThatThrownBy(() -> registry.resolve(MODEL_ID, "qwen"))
                .isInstanceOf(AppException.class);

        when(mapper.selectAiModelRuntimeConfigByAiModelConfigId(MODEL_ID))
                .thenReturn(null);
        assertThatThrownBy(() -> registry.resolve(MODEL_ID, "kimik3"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void disabledProviderFailsReadinessAndRuntimeResolution() {
        MockEnvironment disabledEnvironment = new MockEnvironment()
                .withProperty("ai.models.kimik3.base-url", "https://api.moonshot.cn/v1")
                .withProperty("ai.models.kimik3.api-key", "environment-fallback")
                .withProperty("ai.models.kimik3.enabled", "false");
        DatabaseBackedChatModelRegistry disabledRegistry = new DatabaseBackedChatModelRegistry(
                mapper,
                cipher,
                disabledEnvironment,
                RestClient.builder(),
                WebClient.builder());
        when(mapper.selectAiModelRuntimeConfigByAiModelConfigId(MODEL_ID))
                .thenReturn(visibleKimi());

        assertThat(disabledRegistry.isProviderReady("kimik3")).isFalse();
        assertThatThrownBy(() -> disabledRegistry.resolve(MODEL_ID, "kimik3"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void qianfanIsSupportedWithTheOfficialV2BaseUrl() {
        MockEnvironment qianfanEnvironment = new MockEnvironment()
                .withProperty("ai.models.qianfan.base-url", "https://qianfan.baidubce.com/v2")
                .withProperty("ai.models.qianfan.enabled", "true");
        DatabaseBackedChatModelRegistry qianfanRegistry = new DatabaseBackedChatModelRegistry(
                mapper,
                cipher,
                qianfanEnvironment,
                RestClient.builder(),
                WebClient.builder());

        assertThat(qianfanRegistry.isSupportedProvider("qianfan")).isTrue();
        assertThat(qianfanRegistry.isProviderReady("qianfan")).isTrue();
    }

    @Test
    void tombstoneFailsClosedEvenIfAStaleMapperResultStillSaysVisible() {
        AIModelConfig tombstone = visibleKimi();
        tombstone.setIsDeleted(true);
        when(mapper.selectAiModelRuntimeConfigByAiModelConfigId(MODEL_ID))
                .thenReturn(tombstone);

        assertThatThrownBy(() -> registry.resolve(MODEL_ID, "kimik3"))
                .isInstanceOf(AppException.class);
    }

    private AIModelConfig visibleKimi() {
        return AIModelConfig.builder()
                .aiModelConfigId(MODEL_ID)
                .modelCode("kimik3")
                .name("kimi-k3")
                .visible(true)
                .isDeleted(false)
                .build();
    }
}
