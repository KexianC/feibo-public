package com.hzmwmt.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.eventbus.EventBus;
import com.hzmwmt.trigger.listener.OrderPaySuccessListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GuavaConfig {

    @Autowired
    private OrderPaySuccessListener orderPaySuccessListener;

    @Bean(name = "cache")
    public Cache<String, String> cache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public EventBus eventBusListener() {
        // 创建EventBus实例并注册监听器
        EventBus eventBus = new EventBus();
        // 注册支付成功监听器，确保事件能被正确消费
        eventBus.register(orderPaySuccessListener);
        return eventBus;
    }
}
