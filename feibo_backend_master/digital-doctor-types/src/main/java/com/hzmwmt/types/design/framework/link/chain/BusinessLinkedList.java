package com.hzmwmt.types.design.framework.link.chain;


import com.hzmwmt.types.design.framework.link.handler.ILogicHandler;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 组装链路和执行单元（实际责任链）
 * @ClassName BusinessLinkedList
 * @Date: 2025/7/21 11:02
 */
public class BusinessLinkedList<T, D, R> extends LinkedList<ILogicHandler<T, D, R>> implements ILogicHandler<T, D, R> {

    //给这个链命名
    public BusinessLinkedList(String name) {
        super(name);
    }

    //遍历执行链路（2种遍历形式一张是通过逻辑执行单元的next一个一个遍历，还有就是通过物理链路直接遍历指针）
    @Override
    public R apply(T requestParameter, D dynamicContext) throws Exception {
        Node<ILogicHandler<T, D, R>> current = this.first;
        while (current != null) {
            R result = current.item.apply(requestParameter, dynamicContext);
            if (null != result) {
                return result;
            }
            current = current.next;
        }
        return null;
    }
}