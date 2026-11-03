package com.hzmwmt.types.design.framework.tree;

import lombok.Getter;
import lombok.Setter;

/**
 * @author huihui
 * @version 1.0
 * @Description TODO 抽象路由接口，用于寻找规则树的下一个节点
 * @Date 2025/9/3 0:53
 **/
public abstract class AbstractStrategyRouter<T, D, R> implements StrategyHandler<T, D, R>, StrategyMapper<T, D, R> {
    @Getter
    @Setter
    protected StrategyHandler<T, D, R> defaultStrategyHandler = StrategyHandler.DEFAULT;

    public R router(T requestParameter, D dynamicContext) throws Exception {
        StrategyHandler<T, D, R> handler = get(requestParameter, dynamicContext);
        if (null != handler) {
            return handler.apply(requestParameter, dynamicContext);
        }
        return defaultStrategyHandler.apply(requestParameter, dynamicContext);
    }
}
