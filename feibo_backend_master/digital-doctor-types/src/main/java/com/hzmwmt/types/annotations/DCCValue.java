package com.hzmwmt.types.annotations;

import java.lang.annotation.*;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 配置中心
 * @ClassName DCCValue
 * @Date: 2025/8/26 16:31
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
public @interface DCCValue {

    String value() default "";

}

