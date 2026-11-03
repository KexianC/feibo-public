package com.hzmwmt.api.valid;

import jakarta.validation.Constraint;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName Phone
 * @Date: 2025/9/4 16:05
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "")
@Retention(RetentionPolicy.RUNTIME)
public @interface Phone {
    @OverridesAttribute(constraint = Pattern.class, name = "message")
    String message() default "手机号格式不正确";

    @OverridesAttribute(constraint = Pattern.class, name = "regexp")
    String regexp() default "^1[3-9]\\d{9}$";

    // 必须声明groups/payload
    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
