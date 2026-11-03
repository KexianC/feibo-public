package com.hzmwmt.domain.ai.annotation;

import com.hzmwmt.domain.ai.service.chat.rule.factory.AIChatValidationFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName LogicStrategy
 * @Date: 2025/9/21 15:37
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogicStrategy {

    AIChatValidationFactory.LogicModel logicMode();

}
