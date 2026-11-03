package com.hzmwmt.infrastructure.redis;


import org.redisson.api.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 服务
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 */
public interface IRedisService {


    /**
     * 设置指定 key 的值
     *
     * @param key   键
     * @param value 值
     */
    <T> void setValue(String key, T value);

    /**
     * 设置指定 key 的值
     *
     * @param key     键
     * @param value   值
     * @param expired 过期时间
     */
    <T> void setValue(String key, T value, long expired);

    <T> void setValue(String key, T value, int expired);

    <T> void setValue(String key, T value, int expired, TimeUnit timeUnit);

    /**
     * 获取指定 key 的值
     *
     * @param key 键
     * @return 值
     */
    <T> T getValue(String key);

    /**
     * 获取队列
     *
     * @param key 键
     * @param <T> 泛型
     * @return 队列
     */
    <T> RQueue<T> getQueue(String key);

    /**
     * 加锁队列
     *
     * @param key 键
     * @param <T> 泛型
     * @return 队列
     */
    <T> RBlockingQueue<T> getBlockingQueue(String key);

    /**
     * 延迟队列
     *
     * @param rBlockingQueue 加锁队列
     * @param <T>            泛型
     * @return 队列
     */
    <T> RDelayedQueue<T> getDelayedQueue(RBlockingQueue<T> rBlockingQueue);

    /**
     * 设置值
     *
     * @param key   key 键
     * @param value 值
     */
    void setAtomicLong(String key, long value);

    /**
     * 获取值
     *
     * @param key key 键
     */
    Long getAtomicLong(String key);

    /**
     * 自增 Key 的值；1、2、3、4
     *
     * @param key 键
     * @return 自增后的值
     */
    long incr(String key);

    /**
     * 指定值，自增 Key 的值；1、2、3、4
     *
     * @param key 键
     * @return 自增后的值
     */
    long incrBy(String key, long delta);

    /**
     * 自减 Key 的值；1、2、3、4
     *
     * @param key 键
     * @return 自增后的值
     */
    long decr(String key);

    /**
     * 指定值，自增 Key 的值；1、2、3、4
     *
     * @param key 键
     * @return 自增后的值
     */
    long decrBy(String key, long delta);


    /**
     * 移除指定 key 的值
     *
     * @param key 键
     */
    void remove(String key);

    /**
     * 判断指定 key 的值是否存在
     *
     * @param key 键
     * @return true/false
     */
    boolean isExists(String key);


    Set<String> getToSet(String key, int num);

    /**
     * 将指定的值添加到集合中
     *
     * @param key   键
     * @param value 值
     */
    void addToSet(String key, String value);

    <T> void addListToSet(String key, List<T> list);

    /**
     * 判断指定的值是否是集合的成员
     *
     * @param key   键
     * @param value 值
     * @return 如果是集合的成员返回 true，否则返回 false
     */
    boolean isSetMember(String key, String value);


    /**
     * 将指定的值添加到列表中
     *
     * @param key   键
     * @param value 值
     */
    void addToList(String key, String value);

    /**
     * 获取列表中指定索引的值
     *
     * @param key   键
     * @param index 索引
     * @return 值
     */
    String getFromList(String key, int index);


    <K, V> void setMap(String key, Map<K, V> data);

    /**
     * 获取Map
     *
     * @param key 键
     * @return 值
     */
    <K, V> RMap<K, V> getMap(String key);

    /**
     * 将指定的键值对添加到哈希表中
     *
     * @param key   键
     * @param field 字段
     * @param value 值
     */
    void addToMap(String key, String field, String value);

    Long incrFieldFromMap(String key, String field, long value);

    /***
     * @Author huihui
     * @Description //TODO 批量获取 Hash 结构中多个字段的值
     * @Param [java.lang.String, java.util.Set<K>]
     * @return java.util.Map<K, V>
     * @Date 2025/8/7 11:24
     **/
    <K, V> Map<K, V> getListFieldFromMap(String key, Set<K> fields);


    /**
     * 获取哈希表中指定字段的值
     *
     * @param key   键
     * @param field 字段
     * @return 值
     */
    String getFromMap(String key, String field);

    /**
     * 获取哈希表中指定字段的值
     *
     * @param key   键
     * @param field 字段
     * @return 值
     */
    <K, V> V getFromMap(String key, K field);


    /**
     * 将指定的值添加到有序集合中
     *
     * @param key   键
     * @param value 值
     */
    void addToSortedSet(String key, String value);

    <T> boolean tryAddToScoredSet(String key, T value, double expireTimestamp);

    <T> Double getScoreFromScoredSet(String key, T value);

    <T> void addToScoredSet(String key, T value, double score);

    <K> void addBatchToScoredSet(String key, Map<K, Double> data);

    <T> void deleteToScoredSet(String key, T value);


    <T> Map<T, Double> getMapFromScoredSet(String key, Double cursor, int size);


    /***
     * @Author huihui
     * @Description //TODO 判断元素是否在zest中
     * @Param [java.lang.String, K]
     * @return void
     * @Date 2025/8/16 0:02
     **/
    <T> boolean isMemberInScoredSet(String key, T value);

    /***
     * @Author huihui
     * @Description //TODO 判断元素list是否在zset中用Rbatch减少io
     * @Param [java.lang.String, java.util.List<K>]
     * @return java.util.Map<K, java.lang.Boolean>
     * @Date 2025/8/16 0:03
     **/
    public <T> Map<T, Boolean> isMemberListInScoredSet(String key, List<T> list);

    /**
     * 获取 Redis 锁（可重入锁）
     *
     * @param key 键
     * @return Lock
     */
    RLock getLock(String key);

    /**
     * 获取 Redis 锁（公平锁）
     *
     * @param key 键
     * @return Lock
     */
    RLock getFairLock(String key);

    /**
     * 获取 Redis 锁（读写锁）
     *
     * @param key 键
     * @return RReadWriteLock
     */
    RReadWriteLock getReadWriteLock(String key);

    /**
     * 获取 Redis 信号量
     *
     * @param key 键
     * @return RSemaphore
     */
    RSemaphore getSemaphore(String key);

    /**
     * 获取 Redis 过期信号量
     * <p>
     * 基于Redis的Redisson的分布式信号量（Semaphore）Java对象RSemaphore采用了与java.util.concurrent.Semaphore相似的接口和用法。
     * 同时还提供了异步（Async）、反射式（Reactive）和RxJava2标准的接口。
     *
     * @param key 键
     * @return RPermitExpirableSemaphore
     */
    RPermitExpirableSemaphore getPermitExpirableSemaphore(String key);

    /**
     * 闭锁
     *
     * @param key 键
     * @return RCountDownLatch
     */
    RCountDownLatch getCountDownLatch(String key);

    /**
     * 布隆过滤器
     *
     * @param key 键
     * @param <T> 存放对象
     * @return 返回结果
     */
    <T> RBloomFilter<T> getBloomFilter(String key);

    //    /***
//     * @Author huihui
//     * @Description //TODO 判断否可能存在于布隆过滤器中
//     * @Param [java.lang.String, T]
//     * @return boolean
//     * @Date  2025/8/7 14:04
//     **/
//    <T> boolean bloomFilterContains(String key, T element);
//    /***
//     * @Author huihui
//     * @Description //TODO 判断一个集合中的元素，并返回所有可能存在于布隆过滤器中的元素列表
//     * @Param [java.lang.String, java.util.Collection<T>]
//     * @return java.util.List<T>
//     * @Date  2025/8/7 14:05
//     **/
//    <T> List<T> bloomFilterMultiContains(String key, Collection<T> elements);
    Boolean setNx(String key);

    Boolean setNx(String key, long expired, TimeUnit timeUnit);

    RBitSet getBitSet(String key);

    RScript getScriptLongCodec();

}
