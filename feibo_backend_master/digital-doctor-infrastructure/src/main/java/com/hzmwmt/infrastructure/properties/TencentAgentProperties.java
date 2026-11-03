package com.hzmwmt.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName TencentAgentProperties
 * @Date: 2025/9/11 16:08
 */
@Data
@Component
@ConfigurationProperties(prefix = "tencent.agent")
public class TencentAgentProperties {
    private String botAppKey;
}
