package com.hzmwmt.types.design.framework.tree;

/**
 * @author huihui
 * @version 1.0
 * @Description TODO 提供每个节点处理业务逻辑的接口
 * @Date 2025/9/3 0:53
 **/

public interface StrategyHandler<T, D, R> {
    /*
    提供一个默认的处理变量，创建了一个实现了该接口的匿名内部类，
    Java 编译器能根据上下文自动推断泛型类型，参数类型 (T t, D d) 可以省略
    因为DEFAULT是所有node通用的所以不指配TDR
     */
    StrategyHandler DEFAULT = (T, D) -> null;

    /***
     * @Author huihui
     * @Description //TODO 受理执行的业务流程
     * @Param [T, D]
     * @return R
     * @Date
     **/

    R apply(T requestParameter, D dynamicContext) throws Exception;
}
