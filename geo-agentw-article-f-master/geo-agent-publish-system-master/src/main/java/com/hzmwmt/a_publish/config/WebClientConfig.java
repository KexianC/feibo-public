package com.hzmwmt.a_publish.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration("geoPublishWebClientConfiguration")
public class WebClientConfig {

    @Resource
    private PublishArticleProperties publishArticleProperties;

    @Bean
    public WebClient publishClient(){
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(publishArticleProperties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(15))
                ));
        if (StringUtils.hasText(publishArticleProperties.getInternalToken())) {
            builder.defaultHeader("X-Internal-Token", publishArticleProperties.getInternalToken());
        }
        return builder.build();
    }
}
