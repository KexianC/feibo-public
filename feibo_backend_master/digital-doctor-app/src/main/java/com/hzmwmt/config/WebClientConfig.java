package com.hzmwmt.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hzmwmt.domain.auth.service.IAuthService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName WebClientConfig
 * @Date: 2025/9/23 19:49
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(WebClientConfigProperties.class)
public class WebClientConfig {

    @Bean("tencentAgentWebClient")
    public WebClient tencentAgentWebClient(WebClientConfigProperties properties) {
        String baseUrl = properties.getHosts().get("tencent-agent-prod");
        // 配置 HttpClient
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout())
                .responseTimeout(Duration.ofMinutes(properties.getResponseTimeoutMinutes()))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(properties.getReadTimeoutMinutes(), TimeUnit.MINUTES))
                                .addHandlerLast(new WriteTimeoutHandler(properties.getWriteTimeoutSeconds(), TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> {
                    // 设置内存缓冲区大小
                    configurer.defaultCodecs().maxInMemorySize(properties.getMaxInMemorySize() * 1024 * 1024);
                    configurer.customCodecs().register(new Jackson2JsonDecoder(new ObjectMapper(),
                            MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
                })
                .build();
    }

    @Bean("digitalWebClient")
    public WebClient digitaWebClient(WebClientConfigProperties properties) {
        String baseUrl = properties.getHosts().get("digital-human-prod");
        // 配置 HttpClient
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout())
                .responseTimeout(Duration.ofMinutes(properties.getResponseTimeoutMinutes()))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(properties.getReadTimeoutMinutes(), TimeUnit.MINUTES))
                                .addHandlerLast(new WriteTimeoutHandler(properties.getWriteTimeoutSeconds(), TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> {
                    // 设置内存缓冲区大小
                    configurer.defaultCodecs().maxInMemorySize(properties.getMaxInMemorySize() * 1024 * 1024);
                    configurer.customCodecs().register(new Jackson2JsonDecoder(new ObjectMapper(),
                            MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
                })
                .build();
    }

    @Bean("allInPayWebClient")
    public WebClient allInPayWebClient(WebClientConfigProperties properties) {
        String baseUrl = properties.getHosts().get("all-in-pay-prod");

        // 配置 HttpClient
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout())
                .responseTimeout(Duration.ofMinutes(properties.getResponseTimeoutMinutes()))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(properties.getReadTimeoutMinutes(), TimeUnit.MINUTES))
                                .addHandlerLast(new WriteTimeoutHandler(properties.getWriteTimeoutSeconds(), TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> {
                    // 设置内存缓冲区大小
                    configurer.defaultCodecs().maxInMemorySize(properties.getMaxInMemorySize() * 1024 * 1024);
                    configurer.customCodecs().register(new Jackson2JsonDecoder(new ObjectMapper(),
                            MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
                })
                .build();
    }

    @Autowired
    private IAuthService authService;
    //新增的用于视频发布的
    @Bean("vedioWebClient")
    public WebClient videoWebClient(
            WebClientConfigProperties properties,
            @Value("${app.publish.internal-token:}") String publishInternalToken
    ) {
        if (!StringUtils.hasText(publishInternalToken) || publishInternalToken.trim().length() < 32) {
            throw new IllegalStateException("PUBLISH_INTERNAL_TOKEN必须配置且长度不少于32个字符");
        }
        String requiredPublishToken = publishInternalToken.trim();
        String baseUrl = properties.getHosts().get("vedio-prod");

        // 配置 HttpClient
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout())
                .responseTimeout(Duration.ofMinutes(properties.getResponseTimeoutMinutes()))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(properties.getReadTimeoutMinutes(), TimeUnit.MINUTES))
                                .addHandlerLast(new WriteTimeoutHandler(properties.getWriteTimeoutSeconds(), TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("X-Internal-Token", requiredPublishToken)
                .filter((request, next) -> {
                    // 从安全上下文中获取当前用户ID
                    String userId = authService.queryValidatedUser().getUserId().toString();

                    // 如果获取到用户ID，添加到请求头
                    ClientRequest filteredRequest = ClientRequest.from(request)
                            .header("userid", userId)
                            .build();

                    return next.exchange(filteredRequest);
                })
                .codecs(configurer -> {
                    // 设置内存缓冲区大小
                    configurer.defaultCodecs().maxInMemorySize(properties.getMaxInMemorySize() * 1024 * 1024);
                    configurer.customCodecs().register(new Jackson2JsonDecoder(new ObjectMapper(),
                            MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
                })
                .build();
    }
}
