package com.hzmwmt.types.design.framework.link.chain;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO 抽象链路
 * @ClassName ILink
 * @Date: 2025/7/21 11:00
 */
public interface ILink<E> {
    boolean add(E e);

    boolean addFirst(E e);

    boolean addLast(E e);

    boolean remove(Object o);

    E get(int index);

    void printLinkList();
}
