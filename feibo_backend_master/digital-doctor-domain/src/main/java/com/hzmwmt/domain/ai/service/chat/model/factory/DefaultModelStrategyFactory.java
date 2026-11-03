package com.hzmwmt.domain.ai.service.chat.model.factory;

import com.hzmwmt.domain.ai.service.chat.model.IModelStrategyService;
import com.hzmwmt.domain.ai.service.chat.model.strategy.AbstractModelStrategyService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DefaultModelStrategyFactory
 * @Date: 2025/9/12 14:54
 */
@Service
public class DefaultModelStrategyFactory {
    @Resource
    private final Map<String, IModelStrategyService> modelServiceMap = new HashMap<>();


    public IModelStrategyService getStrategyService(String modelCode) {
        return modelServiceMap.get(modelCode);
    }

    public AbstractModelStrategyService getAbstractStrategyService(String modelCode) {
        return (AbstractModelStrategyService) modelServiceMap.get(modelCode);
    }
}
