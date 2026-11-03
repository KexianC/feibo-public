package com.hzmwmt.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AllInPayProperties
 * @Date: 2025/8/28 13:55
 */

@Data
@Component
@ConfigurationProperties(prefix = "payment.all-in-pay")
public class AllInPayProperties {
    // 身份与基础配置
    private String cusId;
    private String appId;
    private String version;
    private String signType;
    private String cusIp;

    // URL配置
    private String notifyUrl;

    // 其他默认配置
    private Integer defaultValidTime;
    /**
     * 商户 RSA 私钥 (PKCS#8 格式)
     */
    private String rsaPrivateKeyPkcs8;
    /**
     * 通联支付平台的 RSA 公钥
     */
    private String validRsaPublicKey;

}
