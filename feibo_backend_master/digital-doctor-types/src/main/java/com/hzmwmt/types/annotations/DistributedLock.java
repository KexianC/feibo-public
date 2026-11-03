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
 * @ClassName DistributedLock
 * @Date: 2025/9/4 12:34
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DistributedLock {
    String key();

    /**
     * 获取锁的最大等待时间。
     * 如果在此时间内未获取到锁，将抛出异常。
     */
    long waitTime() default 5;

    /**
     * 获取锁的最大等待时间。
     * 如果在此时间内未获取到锁，将抛出异常。
     */
    long leaseTime() default -1;

    /**
     * 时间单位，默认为秒。
     */
    TimeUnit unit() default TimeUnit.SECONDS;

}
