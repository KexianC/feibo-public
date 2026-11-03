package com.hzmwmt.domain.ai.service.chat.model;

import com.hzmwmt.domain.ai.model.aggregate.ModelProcessAggregate;
import reactor.core.Disposable;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IModelStrategyService
 * @Date: 2025/9/12 11:39
 */
public interface IModelStrategyService {
    Disposable doMessageResponse(ModelProcessAggregate modelProcessAggregate);
}
