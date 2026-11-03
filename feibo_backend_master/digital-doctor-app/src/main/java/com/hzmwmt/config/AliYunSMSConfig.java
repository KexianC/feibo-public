package com.hzmwmt.config;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.teaopenapi.models.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AliYunSMSConfig
 * @Date: 2025/9/16 14:16
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AliYunSmsProperties.class)
public class AliYunSMSConfig {

    /**
     * 创建并配置阿里云短信服务的客户端。
     * 该 Bean 仅在 application.properties/yml 文件中的 'aliyun.sms.enable' 配置为 'true' 时才会被创建。
     *
     * @param properties 阿里云短信服务的配置属性对象。
     * @return com.aliyun.dysmsapi20170525.Client 的实例。
     * @throws Exception 如果客户端创建失败则抛出异常。
     */
    @Bean
    public Client aliYunSmsClient(AliYunSmsProperties properties) throws Exception {
        log.info("开始初始化阿里云短信客户端...");

        Config config = new Config()
                .setAccessKeyId(properties.getAccessKeyId())
                .setAccessKeySecret(properties.getAccessKeySecret());

        // 设置服务接入地址
        config.endpoint = properties.getEndpoint();

        Client client = new Client(config);
        log.info("阿里云短信客户端初始化成功，接入地址：{}", config.endpoint);
        return client;
    }
}
