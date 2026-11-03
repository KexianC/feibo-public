package com.hzmwmt.domain.ai.service.chat.rule.factory;

import com.hzmwmt.domain.ai.annotation.LogicStrategy;
import com.hzmwmt.domain.ai.service.chat.rule.IAIChatRulerLogicFilter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AIChatValidationFactory
 * @Date: 2025/9/20 14:43
 */
@Service
public class AIChatValidationFactory {

    private final Map<String, IAIChatRulerLogicFilter> logicFilterMap = new ConcurrentHashMap<>();

    public AIChatValidationFactory(List<IAIChatRulerLogicFilter> logicFilters) {
        logicFilters.forEach(logic -> {
            LogicStrategy strategy = AnnotationUtils.findAnnotation(logic.getClass(), LogicStrategy.class);
            if (null != strategy) {
                logicFilterMap.put(strategy.logicMode().getCode(), logic);
            }
        });
    }

    public IAIChatRulerLogicFilter getLogicFilter(String code) {
        return logicFilterMap.get(code);
    }

    public Map<String, IAIChatRulerLogicFilter> openLogicFilter() {
        return logicFilterMap;
    }

    @Getter
    @AllArgsConstructor
    public enum LogicModel {
        NULL("NULL", "放行不用过滤"),
        PARAMETER_VALIDATION("PARAMETER_VALIDATION", "基础参数校验"),
        CONTENT_VALIDATION("CONTENT_VALIDATION", "内容合规校验(含敏感词)"),
        MODEL_ACCESS_CHECK("MODEL_ACCESS_CHECK", "模型访问校验(区分普通与高级)"),
        USER_QUOTA_DEDUCTION("USER_QUOTA_DEDUCTION", "用户额度扣减");

        private final String code;
        private final String info;

    }
}
