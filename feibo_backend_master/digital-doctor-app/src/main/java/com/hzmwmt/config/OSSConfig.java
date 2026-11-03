package com.hzmwmt.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName OSSConfig
 * @Date: 2025/7/23 13:12
 */
@Configuration
@EnableConfigurationProperties(OSSClientConfigProperties.class)
public class OSSConfig {

    @Bean
    public OSS ossClient(OSSClientConfigProperties properties) {
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        clientBuilderConfiguration.setMaxConnections(properties.getMaxConnections());
        clientBuilderConfiguration.setSocketTimeout(properties.getSocketTimeout());
        clientBuilderConfiguration.setConnectionTimeout(properties.getConnectionTimeout());
        clientBuilderConfiguration.setConnectionRequestTimeout(properties.getConnectionRequestTimeout());
        clientBuilderConfiguration.setIdleConnectionTime(properties.getIdleConnectionTime());
        clientBuilderConfiguration.setMaxErrorRetry(properties.getMaxErrorRetry());

        CredentialsProvider credentialsProvider = new DefaultCredentialProvider(
                properties.getAccessKeyId(),
                properties.getAccessKeySecret()
        );


        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                credentialsProvider,
                clientBuilderConfiguration
        );
    }


}
