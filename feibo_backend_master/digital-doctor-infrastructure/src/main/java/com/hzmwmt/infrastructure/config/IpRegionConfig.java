package com.hzmwmt.infrastructure.config;

import com.hzmwmt.infrastructure.util.ip.IpRegionUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;

/**
 * IP地理位置配置类
 */
@Configuration
public class IpRegionConfig {

    /**
     * 应用关闭时释放资源
     */
    @PreDestroy
    public void destroy() {
        IpRegionUtil.close();
    }
}