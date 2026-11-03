package com.hzmwmt.types.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName Idempotent
 * @Date: 2025/9/10 22:02
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {
    /**
     * 令牌在Redis中的过期时间。
     * 默认保留 24 小时，避免客户端持续提交随机令牌导致 Redis 无界增长。
     */
    long expireTime() default 1440;

    /**
     * 时间单位，默认为分钟。
     */
    TimeUnit timeUnit() default TimeUnit.MINUTES;

    /**
     * 校验失败时的提示信息。
     */
    String message() default "请勿重复提交";
}
