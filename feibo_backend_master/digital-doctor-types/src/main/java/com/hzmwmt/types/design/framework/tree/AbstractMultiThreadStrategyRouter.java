package com.hzmwmt.types.design.framework.tree;

import lombok.Getter;
import lombok.Setter;

/**
 * @author huihui
 * @version 1.0
 * @Description TODO
 * @Date 2025/9/3 23:47
 **/

public abstract class AbstractMultiThreadStrategyRouter<T, D, R> implements StrategyHandler<T, D, R>, StrategyMapper<T, D, R> {


    @Getter
    @Setter
    protected StrategyHandler<T, D, R> defaultStrategyHandler = StrategyHandler.DEFAULT;

    /***
     * @Author huihui
     * @Description //TODO 通过调用策略映射器get方法，控制节点流程的走向，为空说明没有节点了最后一个节点则调用默认处理器去执行
     * @Param [T, D]
     * @return R
     * @Date
     **/
    public R router(T requestParameter, D dynamicContext) throws Exception {
        StrategyHandler<T, D, R> handler = get(requestParameter, dynamicContext);
        if (null != handler) {
            return handler.apply(requestParameter, dynamicContext);
        }
        return defaultStrategyHandler.apply(requestParameter, dynamicContext);
    }

    /***
     * @Author huihui
     * @Description //TODO 多线程执行策略，然后执行doApply方法 因为要执行多线程策略所以不能直接调用接口的apply要多加一层再执行doApply
     * @Param [T, D]
     * @return R
     * @Date
     **/
    @Override
    public R apply(T requestParameter, D dynamicContext) throws Exception {
        multiThread(requestParameter, dynamicContext);
        return doApply(requestParameter, dynamicContext);
    }

    protected abstract void multiThread(T requestParameter, D dynamicContext) throws Exception;

    protected abstract R doApply(T requestParameter, D dynamicContext) throws Exception;


}
