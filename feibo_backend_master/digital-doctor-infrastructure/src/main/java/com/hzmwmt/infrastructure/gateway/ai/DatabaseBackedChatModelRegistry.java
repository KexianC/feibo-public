package com.hzmwmt.infrastructure.gateway.ai;

import com.hzmwmt.domain.ai.adapter.port.IAIModelRuntimeManager;
import com.hzmwmt.domain.security.AIModelApiKeyCipher;
import com.hzmwmt.infrastructure.dao.AIModelConfigMapper;
import com.hzmwmt.infrastructure.dao.po.AIModelConfig;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.env.Environment;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves a ChatModel by model business ID. A database credential overrides
 * the legacy environment credential; absence of an override preserves the
 * current environment-based behaviour.
 */
@Service
public class DatabaseBackedChatModelRegistry implements IAIModelRuntimeManager {
    private static final Map<String, ProviderSpec> SUPPORTED_PROVIDERS = Map.ofEntries(
            Map.entry("deepseek", new ProviderSpec("/v1/chat/completions", "/v1/embeddings")),
            Map.entry("ant", new ProviderSpec("/v1/chat/completions", "/v1/embeddings")),
            Map.entry("wisediag", new ProviderSpec("/v1/chat/completions", "/v1/embeddings")),
            Map.entry("doubao", new ProviderSpec("/api/v3/chat/completions", "/api/v3/embeddings")),
            Map.entry("qwen", new ProviderSpec("/compatible-mode/v1/chat/completions", "/compatible-mode/v1/embeddings")),
            Map.entry("hy3", new ProviderSpec("/chat/completions", "/embeddings")),
            Map.entry("glm52", new ProviderSpec("/chat/completions", "/embeddings")),
            Map.entry("kimik3", new ProviderSpec("/chat/completions", "/embeddings")),
            Map.entry("qianfan", new ProviderSpec("/chat/completions", "/embeddings")),
            Map.entry("szdoctor", new ProviderSpec("/v1/chat/completions", "/v1/embeddings")),
            Map.entry("baichuan", new ProviderSpec("/v1/chat/completions", "/v1/embeddings")));

    private final AIModelConfigMapper modelConfigMapper;
    private final AIModelApiKeyCipher apiKeyCipher;
    private final Environment environment;
    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ConcurrentMap<Long, ChatModel> modelCache = new ConcurrentHashMap<>();

    public DatabaseBackedChatModelRegistry(
            AIModelConfigMapper modelConfigMapper,
            AIModelApiKeyCipher apiKeyCipher,
            Environment environment,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {
        this.modelConfigMapper = modelConfigMapper;
        this.apiKeyCipher = apiKeyCipher;
        this.environment = environment;
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
    }

    public ChatModel resolve(Long aiModelConfigId, String expectedModelCode) {
        if (aiModelConfigId == null) {
            throw configurationError();
        }
        // Always re-check visibility and identity before consulting the cache.
        AIModelConfig config = modelConfigMapper
                .selectAiModelRuntimeConfigByAiModelConfigId(aiModelConfigId);
        if (config == null
                || !Boolean.TRUE.equals(config.getVisible())
                || Boolean.TRUE.equals(config.getIsDeleted())
                || config.getModelCode() == null
                || !config.getModelCode().equals(expectedModelCode)) {
            invalidate(aiModelConfigId);
            throw configurationError();
        }
        ProviderSpec provider = SUPPORTED_PROVIDERS.get(config.getModelCode());
        String baseUrl = providerBaseUrl(config.getModelCode());
        if (provider == null || !isProviderReady(config.getModelCode())) {
            throw configurationError();
        }
        if (!isBlank(config.getApiKeyCiphertext())) {
            // Deliberately do not cache database credential overrides. This
            // makes rotations immediately consistent across JVM instances and
            // prevents an in-flight pre-commit request from repopulating a
            // local cache with a stale Key after invalidation.
            String apiKey = apiKeyCipher.decrypt(
                    config.getAiModelConfigId(), config.getApiKeyCiphertext());
            return buildChatModel(config.getModelCode(), provider, baseUrl, apiKey);
        }
        return modelCache.computeIfAbsent(aiModelConfigId, ignored -> {
            String fallbackKey = environment.getProperty(
                    "ai.models." + config.getModelCode() + ".api-key");
            if (isBlank(fallbackKey)) {
                throw configurationError();
            }
            return buildChatModel(
                    config.getModelCode(), provider, baseUrl, fallbackKey);
        });
    }

    /** Uses enabled database models and the same credential resolution as normal requests. */
    public FallbackModel resolveDeepSeekFallback(Long failedConfigId) {
        List<AIModelConfig> configs = modelConfigMapper.selectAllAiModelConfig();
        boolean premiumAllowed = configs.stream().anyMatch(config ->
                java.util.Objects.equals(config.getAiModelConfigId(), failedConfigId)
                        && Boolean.TRUE.equals(config.getType()));
        List<AIModelConfig> candidates = configs.stream()
                .filter(config -> "deepseek".equals(config.getModelCode()))
                .filter(config -> Boolean.TRUE.equals(config.getIsLeaf())
                        && Boolean.TRUE.equals(config.getVisible())
                        && !Boolean.TRUE.equals(config.getIsDeleted()))
                .filter(config -> config.getName() != null && !config.getName().isBlank())
                .filter(config -> !java.util.Objects.equals(config.getAiModelConfigId(), failedConfigId))
                .filter(config -> premiumAllowed || !Boolean.TRUE.equals(config.getType()))
                .sorted(Comparator.comparing((AIModelConfig config) -> Boolean.TRUE.equals(config.getType()))
                        .thenComparing(AIModelConfig::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AIModelConfig::getAiModelConfigId))
                .toList();
        for (AIModelConfig candidate : candidates) {
            try {
                return new FallbackModel(candidate.getAiModelConfigId(), candidate.getName(),
                        resolve(candidate.getAiModelConfigId(), "deepseek"));
            } catch (AppException unavailableCredential) {
                // A model with an unusable credential must not hide another configured DeepSeek model.
            }
        }
        throw configurationError();
    }

    public record FallbackModel(Long configId, String modelName, ChatModel chatModel) {}

    @Override
    public boolean isSupportedProvider(String modelCode) {
        return modelCode != null
                && SUPPORTED_PROVIDERS.containsKey(modelCode)
                && !isBlank(providerBaseUrl(modelCode));
    }

    @Override
    public boolean isProviderReady(String modelCode) {
        return isSupportedProvider(modelCode) && isProviderEnabled(modelCode);
    }

    @Override
    public boolean hasFallbackApiKey(String modelCode) {
        return modelCode != null
                && !isBlank(environment.getProperty("ai.models." + modelCode + ".api-key"));
    }

    @Override
    public void invalidate(Long aiModelConfigId) {
        if (aiModelConfigId != null) {
            modelCache.remove(aiModelConfigId);
        }
    }

    private ChatModel buildChatModel(
            String modelCode,
            ProviderSpec provider,
            String baseUrl,
            String apiKey) {
        OpenAiApi openAiApi = new OpenAiApi(
                baseUrl,
                new SimpleApiKey(apiKey),
                new LinkedMultiValueMap<>(),
                provider.completionsPath(),
                provider.embeddingsPath(),
                restClientBuilder.clone(),
                webClientBuilder.clone(),
                new DefaultResponseErrorHandler());

        OpenAiChatOptions options;
        if ("kimik3".equals(modelCode)) {
            options = OpenAiChatOptions.builder().temperature(1.0).build();
        } else if ("baichuan".equals(modelCode)) {
            options = OpenAiChatOptions.builder()
                    .temperature(0.3)
                    .maxTokens(1024)
                    .build();
        } else {
            options = OpenAiChatOptions.builder().build();
        }
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();
        return new OpenAiChatModel(
                openAiApi,
                options,
                ToolCallingManager.builder().build(),
                retryTemplate,
                ObservationRegistry.NOOP);
    }

    private String providerBaseUrl(String modelCode) {
        return modelCode == null
                ? null
                : environment.getProperty("ai.models." + modelCode + ".base-url");
    }

    public String endpointHost(String modelCode) {
        try {
            return java.net.URI.create(providerBaseUrl(modelCode)).getHost();
        } catch (RuntimeException invalidEndpoint) {
            return null;
        }
    }

    private boolean isProviderEnabled(String modelCode) {
        return modelCode != null && environment.getProperty(
                "ai.models." + modelCode + ".enabled", Boolean.class, true);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private AppException configurationError() {
        return new AppException(
                ResponseCode.MANAGE_ERROR.getCode(),
                "AI 模型运行配置不可用");
    }

    private record ProviderSpec(String completionsPath, String embeddingsPath) {
    }
}
