package com.hzmwmt.security;

import com.hzmwmt.domain.security.AIModelApiKeyCipher;
import com.hzmwmt.infrastructure.dao.AIModelConfigMapper;
import com.hzmwmt.infrastructure.dao.po.AIModelConfig;
import com.hzmwmt.infrastructure.gateway.ai.DatabaseBackedChatModelRegistry;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepSeekFallbackRegistryTest {
    private static final long PRIMARY_ID = 9005300000000000001L;
    private static final long NORMAL_ID = 9005300000000000002L;
    private static final long PREMIUM_ID = 9005300000000000003L;

    private AIModelConfigMapper mapper;
    private MockEnvironment environment;
    private DatabaseBackedChatModelRegistry registry;

    @BeforeEach
    void setUp() {
        mapper = mock(AIModelConfigMapper.class);
        environment = new MockEnvironment()
                .withProperty("ai.models.deepseek.base-url", "https://api.deepseek.com")
                .withProperty("ai.models.deepseek.api-key", "test-only-placeholder");
        AIModelApiKeyCipher cipher = new AIModelApiKeyCipher(Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        registry = new DatabaseBackedChatModelRegistry(
                mapper, cipher, environment, RestClient.builder(), WebClient.builder());
    }

    @Test
    void normalDeepseekIsPreferredEvenWhenPremiumHasEarlierDisplayOrder() {
        AIModelConfig primary = primary(true);
        AIModelConfig normal = deepseek(NORMAL_ID, false, 99);
        AIModelConfig premium = deepseek(PREMIUM_ID, true, 1);
        configure(primary, premium, normal);

        var fallback = registry.resolveDeepSeekFallback(PRIMARY_ID);

        assertThat(fallback.configId()).isEqualTo(NORMAL_ID);
        assertThat(fallback.modelName()).isEqualTo("deepseek-v4-flash");
        assertThat(fallback.chatModel()).isNotNull();
    }

    @Test
    void ordinaryOriginalModelCannotSilentlyUpgradeToPremiumDeepseek() {
        configure(primary(false), deepseek(PREMIUM_ID, true, 1));

        assertThatThrownBy(() -> registry.resolveDeepSeekFallback(PRIMARY_ID))
                .isInstanceOf(AppException.class);
    }

    @Test
    void premiumOriginalModelCanUsePremiumDeepseekWhenNoOrdinaryModelExists() {
        configure(primary(true), deepseek(PREMIUM_ID, true, 1));

        assertThat(registry.resolveDeepSeekFallback(PRIMARY_ID).configId()).isEqualTo(PREMIUM_ID);
    }

    @Test
    void hiddenDeletedGroupingAndBlankNameModelsCannotBeFallbacks() {
        AIModelConfig hidden = deepseek(51L, false, 1);
        hidden.setVisible(false);
        AIModelConfig deleted = deepseek(52L, false, 2);
        deleted.setIsDeleted(true);
        AIModelConfig grouping = deepseek(53L, false, 3);
        grouping.setIsLeaf(false);
        AIModelConfig blankName = deepseek(54L, false, 4);
        blankName.setName(" ");
        AIModelConfig wrongProvider = deepseek(55L, false, 5);
        wrongProvider.setModelCode("qwen");
        AIModelConfig normal = deepseek(NORMAL_ID, false, 99);
        configure(primary(false), hidden, deleted, grouping, blankName, wrongProvider, normal);

        assertThat(registry.resolveDeepSeekFallback(PRIMARY_ID).configId()).isEqualTo(NORMAL_ID);
    }

    @Test
    void theFailedConfigurationIsExcludedFromCandidates() {
        AIModelConfig failed = deepseek(PRIMARY_ID, false, 0);
        configure(failed, deepseek(NORMAL_ID, false, 99));

        assertThat(registry.resolveDeepSeekFallback(PRIMARY_ID).configId()).isEqualTo(NORMAL_ID);
    }

    @Test
    void runtimeVisibilityIsRecheckedAfterReadingCandidateList() {
        AIModelConfig stale = deepseek(51L, false, 1);
        AIModelConfig normal = deepseek(NORMAL_ID, false, 2);
        configure(primary(false), stale, normal);
        when(mapper.selectAiModelRuntimeConfigByAiModelConfigId(51L)).thenReturn(null);

        assertThat(registry.resolveDeepSeekFallback(PRIMARY_ID).configId()).isEqualTo(NORMAL_ID);
    }

    @Test
    void unusableFirstCredentialDoesNotHideAnotherWorkingDeepseekConfiguration() {
        AIModelConfig invalidCredential = deepseek(51L, false, 1);
        invalidCredential.setApiKeyCiphertext("invalid-encrypted-value");
        configure(primary(false), invalidCredential, deepseek(NORMAL_ID, false, 2));

        assertThat(registry.resolveDeepSeekFallback(PRIMARY_ID).configId()).isEqualTo(NORMAL_ID);
    }

    @Test
    void disablingDeepseekMakesFallbackUnavailable() {
        environment.setProperty("ai.models.deepseek.enabled", "false");
        configure(primary(false), deepseek(NORMAL_ID, false, 1));

        assertThatThrownBy(() -> registry.resolveDeepSeekFallback(PRIMARY_ID))
                .isInstanceOf(AppException.class);
    }

    private void configure(AIModelConfig... configs) {
        when(mapper.selectAllAiModelConfig()).thenReturn(List.of(configs));
        for (AIModelConfig config : configs) {
            when(mapper.selectAiModelRuntimeConfigByAiModelConfigId(config.getAiModelConfigId()))
                    .thenReturn(config);
        }
    }

    private AIModelConfig primary(boolean premium) {
        return AIModelConfig.builder().aiModelConfigId(PRIMARY_ID).modelCode("qwen")
                .name("qwen3.7-flash").visible(true).isDeleted(false).isLeaf(true)
                .type(premium).sort(1).build();
    }

    private AIModelConfig deepseek(long id, boolean premium, int sort) {
        return AIModelConfig.builder().aiModelConfigId(id).modelCode("deepseek")
                .name("deepseek-v4-flash").visible(true).isDeleted(false).isLeaf(true)
                .type(premium).sort(sort).build();
    }
}
