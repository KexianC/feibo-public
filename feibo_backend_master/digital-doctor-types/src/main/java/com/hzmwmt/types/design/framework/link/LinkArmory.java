package com.hzmwmt.types.design.framework.link;


import com.hzmwmt.types.design.framework.link.chain.BusinessLinkedList;
import com.hzmwmt.types.design.framework.link.handler.ILogicHandler;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 责任链注入器
 * @ClassName LinkArmory
 * @Date: 2025/7/21 10:58
 */
public class LinkArmory<T, D, R> {

    private final BusinessLinkedList<T, D, R> logicLink;

    @SafeVarargs
    public LinkArmory(String linkName, ILogicHandler<T, D, R>... logicHandlers) {
        //为每一个服务new一个链，使各个服务之间相互不打扰
        logicLink = new BusinessLinkedList<>(linkName);
        for (ILogicHandler<T, D, R> logicHandler : logicHandlers) {
            logicLink.add(logicHandler);
        }
    }

    public BusinessLinkedList<T, D, R> getLogicLink() {
        return logicLink;
    }

}
