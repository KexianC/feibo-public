package com.hzmwmt.types.design.framework.link.handler;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 业务逻辑执行单元（逻辑层面），
 * @ClassName ILogicHandler
 * @Date: 2025/7/21 10:59
 */
public interface ILogicHandler<T, D, R> {

    default R next(T requestParameter, D dynamicContext) {
        return null;
    }

    R apply(T requestParameter, D dynamicContext) throws Exception;
}
