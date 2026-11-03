package com.hzmwmt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName WebClientConfigProperties
 * @Date: 2025/9/23 19:53
 */
@Data
@ConfigurationProperties(prefix = "webclient.config", ignoreInvalidFields = true)
public class WebClientConfigProperties {
    /**
     * 服务地址配置
     */
    private Map<String, String> hosts;
    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 30000;
    /**
     * 响应超时时间（分钟）
     */
    private int responseTimeoutMinutes = 10;
    /**
     * 读取超时时间（分钟）
     */
    private int readTimeoutMinutes = 10;
    /**
     * 写入超时时间（秒）
     */
    private int writeTimeoutSeconds = 30;
    /**
     * 最大内存缓冲区大小（MB）
     */
    private int maxInMemorySize = 10;
}
