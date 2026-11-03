package com.hzmwmt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName OSSClientConfigProperties
 * @Date: 2025/9/17 18:34
 */
@Data// 确保该类能被 Spring 扫描到
@ConfigurationProperties(prefix = "aliyun.oss")
public class OSSClientConfigProperties {
    /**
     * Endpoint 表示您希望访问的阿里云 OSS 的区域域名
     */
    private String endpoint;

    /**
     * Region 地域
     */
    private String region;

    /**
     * Bucket 名称
     */
    private String bucketName;

    /**
     * 访问密钥 ID
     */
    private String accessKeyId;

    /**
     * 访问密钥 Secret
     */
    private String accessKeySecret;

    // === 以下为可选的客户端高级配置 ===

    /**
     * 设置允许打开的最大HTTP连接数，默认为1024
     */
    private int maxConnections;

    /**
     * 设置Socket层传输数据的超时时间（单位：毫秒），默认为50000
     */
    private int socketTimeout;

    /**
     * 设置建立连接的超时时间（单位：毫秒），默认为50000
     */
    private int connectionTimeout;

    /**
     * 设置从连接池中获取连接的超时时间（单位：毫秒），默认为0，表示不超时
     */
    private int connectionRequestTimeout;

    /**
     * 设置连接空闲超时时间，超时则关闭连接（单位：毫秒），默认为60000
     */
    private int idleConnectionTime;

    /**
     * 设置请求失败后最大的重试次数，默认为3
     */
    private int maxErrorRetry;
}
