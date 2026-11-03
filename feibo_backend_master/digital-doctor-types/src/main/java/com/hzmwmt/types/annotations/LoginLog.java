package com.hzmwmt.types.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 登录日志注解
 * 用于标记需要记录登录日志的方法
 *
 * @author AI Assistant
 * @version v1.0
 * @Date: 2025-11-05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LoginLog {
    /**
     * 登录方式（如：密码登录、验证码登录等）
     */
    String loginType() default "密码登录";

    /**
     * 登录描述
     */
    String description() default "用户登录";

    /**
     * 是否记录登录IP
     */
    boolean recordIp() default true;

    /**
     * 是否记录登录时间
     */
    boolean recordTime() default true;
}