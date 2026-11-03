package com.hzmwmt.api.valid;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName JsonString
 * @Date: 2025/9/24 22:15
 */
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = JsonStringValidator.class)
public @interface JsonString {
    String message() default "必须是合法 JSON 字符串";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
