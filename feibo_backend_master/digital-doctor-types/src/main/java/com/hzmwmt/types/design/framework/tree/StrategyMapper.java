package com.hzmwmt.types.design.framework.tree;

/**
 * @author huihui
 * @version 1.0
 * @Description TODO 策略映射器，获取下一层节点的业务逻辑
 * @Date 2025/9/3 0:52
 **/

public interface StrategyMapper<T, D, R> {

    /***
     * @Author huihui
     * @Description //TODO 用于获取每一个要执行的节点，相当于一个流程走完进入到下一个流程的过程
     * @Param [T, D]
     * @return com.huihui.types.design.framework.tree.StrategyHandler<T, D, R>
     * @Date
     **/

    StrategyHandler<T, D, R> get(T requestParameter, D dynamicContext);
}
