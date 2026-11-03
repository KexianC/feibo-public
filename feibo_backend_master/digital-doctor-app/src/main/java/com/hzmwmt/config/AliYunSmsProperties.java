package com.hzmwmt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AliYunSmsProperties
 * @Date: 2025/9/16 14:24
 */
@Data
@ConfigurationProperties(prefix = "aliyun.sms")
public class AliYunSmsProperties {

    /**
     * 从阿里云控制台获取的 AccessKey ID。
     */
    private String accessKeyId;

    /**
     * 从阿里云控制台获取的 AccessKey Secret。
     */
    private String accessKeySecret;

    /**
     * 短信服务的API接入地址。
     * 例如：dysmsapi.aliyuncs.com
     */
    private String endpoint;
}
