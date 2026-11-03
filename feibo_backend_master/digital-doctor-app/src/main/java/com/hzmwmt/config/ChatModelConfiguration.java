package com.hzmwmt.config;


import com.hzmwmt.types.common.Constants;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName ChatModelConfiguration
 * @Date: 2025/9/22 13:19
 */
@Configuration
@EnableConfigurationProperties(ChatModelProperties.class)
public class ChatModelConfiguration {

    @Bean("deepseek" + Constants.AIChatConstants.MODEL_SUFFIX)
    public ChatModel deepseekChatModel(
            ChatModelProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {

        var props = properties.models().get("deepseek");

        var openAiApi = new OpenAiApi(
                props.baseUrl(),
                new SimpleApiKey(props.apiKey()),
                new LinkedMultiValueMap<>(),
                "/v1/chat/completions",
                "/v1/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );

        var defaultOptions = OpenAiChatOptions.builder().build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        var retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();

        return new OpenAiChatModel(
                openAiApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                ObservationRegistry.NOOP
        );
    }

    @Bean("ant" + Constants.AIChatConstants.MODEL_SUFFIX)
    public ChatModel antChatModel(
            ChatModelProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {

        var props = properties.models().get("ant");

        var openAiApi = new OpenAiApi(
                props.baseUrl(),
                new SimpleApiKey(props.apiKey()),
                new LinkedMultiValueMap<>(),
                "/v1/chat/completions",
                "/v1/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );

        var defaultOptions = OpenAiChatOptions.builder().build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        var retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();

        return new OpenAiChatModel(
                openAiApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                ObservationRegistry.NOOP
        );
    }

    @Bean("sharding" + Constants.AIChatConstants.MODEL_SUFFIX)
    public ChatModel shardingChatModel(
            ChatModelProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {

        var props = properties.models().get("deepseek");

        var openAiApi = new OpenAiApi(
                props.baseUrl(),
                new SimpleApiKey(props.apiKey()),
                new LinkedMultiValueMap<>(),
                "/v1/chat/completions",
                "/v1/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );


        var defaultOptions = OpenAiChatOptions.builder()
                .model("deepseek-v4-flash")
                .build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        var retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();


        return new OpenAiChatModel(
                openAiApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                ObservationRegistry.NOOP
        );
    }

    @Bean("wisediag" + Constants.AIChatConstants.MODEL_SUFFIX)
    public ChatModel wisediagChatModel(
            ChatModelProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {

        var props = properties.models().get("wisediag");
        var openAiApi = new OpenAiApi(
                props.baseUrl(),
                new SimpleApiKey(props.apiKey()),
                new LinkedMultiValueMap<>(),
                "/v1/chat/completions",
                "/v1/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );

        var defaultOptions = OpenAiChatOptions.builder().build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        var retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();

        return new OpenAiChatModel(
                openAiApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                ObservationRegistry.NOOP
        );
    }

    @Bean("doubao" + Constants.AIChatConstants.MODEL_SUFFIX)
    public ChatModel doubaoChatModel(
            ChatModelProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {


        var props = properties.models().get("doubao");

        var openAiApi = new OpenAiApi(
                props.baseUrl(),
                new SimpleApiKey(props.apiKey()),
                new LinkedMultiValueMap<>(),
                "/api/v3/chat/completions",
                "/api/v3/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );


        var defaultOptions = OpenAiChatOptions.builder().build();
        var retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        return new OpenAiChatModel(
                openAiApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                ObservationRegistry.NOOP
        );
    }

    @Bean("qwen" + Constants.AIChatConstants.MODEL_SUFFIX)
    public ChatModel qWenChatModel(
            ChatModelProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {

        var props = properties.models().get("qwen");

        var openAiApi = new OpenAiApi(
                props.baseUrl(),
                new SimpleApiKey(props.apiKey()),
                new LinkedMultiValueMap<>(),
                "/compatible-mode/v1/chat/completions",
                "/compatible-mode/v1/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );


        var defaultOptions = OpenAiChatOptions.builder().build();
        var retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        return new OpenAiChatModel(
                openAiApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                ObservationRegistry.NOOP
        );
    }

    /**
     * HY3 ChatModel Bean. This uses an OpenAI-compatible endpoint whose base URL normally already includes /v1.
     */
    @Bean("hy3" + Constants.AIChatConstants.MODEL_SUFFIX)
    @ConditionalOnProperty(prefix = "ai.models.hy3", name = {"base-url", "api-key"})
    public ChatModel hy3ChatModel(
            ChatModelProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {

        var props = properties.models().get("hy3");

        var openAiApi = new OpenAiApi(
                props.baseUrl(),
                new SimpleApiKey(props.apiKey()),
                new LinkedMultiValueMap<>(),
                "/chat/completions",
                "/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );

        var defaultOptions = OpenAiChatOptions.builder().build();
        var retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        return new OpenAiChatModel(
                openAiApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                ObservationRegistry.NOOP
        );
    }

    /**
     * Zhipu GLM ChatModel bean backed by the BigModel OpenAI-compatible API.
     * The stable bean/configuration key remains {@code glm52}; the database
     * model name selects the concrete GLM version at request time.
     */
    @Bean("glm52" + Constants.AIChatConstants.MODEL_SUFFIX)
    @ConditionalOnProperty(prefix = "ai.models.glm52", name = "enabled", havingValue = "true")
    public ChatModel glm52ChatModel(
            ChatModelProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {

        var props = properties.models().get("glm52");

        var openAiApi = new OpenAiApi(
                props.baseUrl(),
                new SimpleApiKey(props.apiKey()),
                new LinkedMultiValueMap<>(),
                "/chat/completions",
                "/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );

        var defaultOptions = OpenAiChatOptions.builder().build();
        var retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        return new OpenAiChatModel(
                openAiApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                ObservationRegistry.NOOP
        );
    }

    /**
     * Kimi K3 ChatModel Bean, backed by Moonshot AI's OpenAI-compatible API.
     */
    @Bean("kimik3" + Constants.AIChatConstants.MODEL_SUFFIX)
    @ConditionalOnProperty(prefix = "ai.models.kimik3", name = "enabled", havingValue = "true")
    public ChatModel kimiK3ChatModel(
            ChatModelProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {

        var props = properties.models().get("kimik3");

        var openAiApi = new OpenAiApi(
                props.baseUrl(),
                new SimpleApiKey(props.apiKey()),
                new LinkedMultiValueMap<>(),
                "/chat/completions",
                "/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );

        var defaultOptions = OpenAiChatOptions.builder()
                .model("kimi-k3")
                .temperature(1.0)
                .build();
        var retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        return new OpenAiChatModel(
                openAiApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                ObservationRegistry.NOOP
        );
    }

    /**
     * SZDoctor V8.3 OpenAI-compatible model gateway.
     */
    @Bean("szdoctor" + Constants.AIChatConstants.MODEL_SUFFIX)
    @ConditionalOnProperty(prefix = "ai.models.szdoctor", name = "enabled", havingValue = "true")
    public ChatModel szDoctorChatModel(
            ChatModelProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {

        var props = properties.models().get("szdoctor");

        var openAiApi = new OpenAiApi(
                props.baseUrl(),
                new SimpleApiKey(props.apiKey()),
                new LinkedMultiValueMap<>(),
                "/v1/chat/completions",
                "/v1/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );

        var defaultOptions = OpenAiChatOptions.builder().build();
        var retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        return new OpenAiChatModel(
                openAiApi,
                defaultOptions,
                toolCallingManager,
                retryTemplate,
                ObservationRegistry.NOOP
        );
    }

    /**
     * 百川 ChatModel Bean（名称格式：baichuan + 模型后缀，和 qwen 保持一致）
     */
    @Bean("baichuan" + Constants.AIChatConstants.MODEL_SUFFIX)
    public ChatModel baichuanChatModel(
            ChatModelProperties properties,  // 复用你的配置属性类（存储百川的baseUrl、apiKey）
            RestClient.Builder restClientBuilder,  // 复用 HTTP 客户端构建器
            WebClient.Builder webClientBuilder) {  // 复用响应式 HTTP 客户端构建器

        // 1. 从配置中读取百川的模型配置（对应 yml 中 models.baichuan 节点）
        // 注意：需在你的配置文件中添加百川的 baseUrl、apiKey 配置（和 qwen 同级）
        var baichuanProps = properties.models().get("baichuan");

        // 2. 构建百川 OpenAI 兼容 API 客户端（核心：调整百川的接口路径和认证方式）
        var openAiApi = new OpenAiApi(
                baichuanProps.baseUrl(),  // 百川接口 baseUrl：https://api.baichuan-ai.com/v1/
                new SimpleApiKey(baichuanProps.apiKey()),  // 百川 API Key（从配置读取，避免硬编码）
                new LinkedMultiValueMap<>(),  // 额外请求头（百川无需额外头，保持空）
                "/v1/chat/completions",  // 百川 chat 接口路径（原生兼容 OpenAI，无需加 compatible-mode）
                "/v1/embeddings",  // 百川 embedding 接口路径（按需配置，若用不到可保持默认）
                restClientBuilder,  // 复用 RestClient
                webClientBuilder,  // 复用 WebClient（响应式流式调用用）
                new DefaultResponseErrorHandler()  // 复用默认错误处理器（和 qwen 一致）
        );

        // 3. 构建默认模型选项（可按需添加百川专属参数，如 temperature、top_p 等）
        // 百川支持 OpenAI 标准参数，直接复用 OpenAiChatOptions
        var defaultOptions = OpenAiChatOptions.builder()
                .temperature(0.3)  // 默认温度（对齐你 Python 示例的 0.3）
                .maxTokens(1024)    // 默认最大 tokens（可从配置读取，更灵活）
                .build();

        // 4. 复用重试模板（和 qwen 配置完全一致：3次重试、1秒固定退避）
        var retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1000)
                .build();

        // 5. 复用工具调用管理器（若百川需要 tools 参数，直接复用现有逻辑）
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

        // 6. 构建并返回百川 ChatModel（复用 OpenAiChatModel，无需新增类）
        return new OpenAiChatModel(
                openAiApi,          // 百川 API 客户端
                defaultOptions,     // 模型默认选项
                toolCallingManager, // 工具调用管理器
                retryTemplate,      // 重试模板
                ObservationRegistry.NOOP  // 观测器（和 qwen 一致，无需修改）
        );
    }


}
