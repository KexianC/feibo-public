package com.hzmwmt.types.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 用于标记需要记录数据库操作日志的方法
 *
 * @author AI Assistant
 * @version v1.0
 * @Date: 2025-11-05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OperationLog {
    /**
     * 操作类型（如：新增、修改、删除等）
     */
    String operationType() default "";

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录操作参数
     */
    boolean recordParams() default true;

    /**
     * 是否记录返回结果
     */
    boolean recordResult() default true;

    /**
     * Optional zero-based method-argument index for a safe scalar audit target.
     * The audit aspect accepts only positive integral values and never serializes
     * any other argument or request body. The default disables target capture.
     */
    int targetIdParameterIndex() default -1;

    /**
     * Critical audit records are persisted synchronously inside the enclosing
     * business transaction. A missing transaction or failed audit insert aborts
     * the privileged mutation instead of silently losing its audit trail.
     */
    boolean critical() default false;
}
