package com.hzmwmt.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;

/**
 * 状态机配置类，用于排除默认的 StateMachineAnnotationPostProcessor
 * 解决 Spring 容器初始化时的 BeanCreationException 问题
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnMissingBean(name = "org.springframework.statemachine.processor.stateMachineAnnotationPostProcessor")
public class StateMachineConfig {
    // 不导入 StateMachineAnnotationPostProcessorConfiguration
    // 这样可以避免 StateMachineAnnotationPostProcessor 的自动创建
}