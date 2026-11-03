package com.hzmwmt.infrastructure.redis.impl;


import com.hzmwmt.infrastructure.redis.IRedisService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * Redis 服务 - Redisson
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 */
@Slf4j
@Service("redissonService")
public class RedissonService implements IRedisService {

    @Resource
    private RedissonClient redissonClient;

    @Override
    public <T> void setValue(String key, T value) {
        redissonClient.<T>getBucket(key).set(value);
    }

    @Override
    public <T> void setValue(String key, T value, long expired) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value, expired, TimeUnit.MILLISECONDS);
    }

    @Override
    public <T> void setValue(String key, T value, int expired) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value, expired, TimeUnit.MINUTES);
    }

    @Override
    public <T> void setValue(String key, T value, int expired, TimeUnit timeUnit) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value, expired, timeUnit);
    }


    @Override
    public <T> T getValue(String key) {
        return redissonClient.<T>getBucket(key).get();
    }

    @Override
    public <T> RQueue<T> getQueue(String key) {
        return redissonClient.getQueue(key);
    }

    @Override
    public <T> RBlockingQueue<T> getBlockingQueue(String key) {
        return redissonClient.getBlockingQueue(key);
    }

    @Override
    public <T> RDelayedQueue<T> getDelayedQueue(RBlockingQueue<T> rBlockingQueue) {
        return redissonClient.getDelayedQueue(rBlockingQueue);
    }

    @Override
    public void setAtomicLong(String key, long value) {
        redissonClient.getAtomicLong(key).set(value);
    }

    @Override
    public Long getAtomicLong(String key) {
        return redissonClient.getAtomicLong(key).get();
    }

    @Override
    public long incr(String key) {
        return redissonClient.getAtomicLong(key).incrementAndGet();
    }

    @Override
    public long incrBy(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(delta);
    }

    @Override
    public long decr(String key) {
        return redissonClient.getAtomicLong(key).decrementAndGet();
    }

    @Override
    public long decrBy(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(-delta);
    }

    @Override
    public void remove(String key) {
        redissonClient.getBucket(key).delete();
    }

    @Override
    public boolean isExists(String key) {
        return redissonClient.getBucket(key).isExists();
    }

    @Override
    public Set<String> getToSet(String key, int num) {
        RSet<String> set = redissonClient.getSet(key);
        return set.random(num);
    }

    @Override
    public void addToSet(String key, String value) {
        RSet<String> set = redissonClient.getSet(key);
        set.add(value);
    }

    @Override
    public <T> void addListToSet(String key, List<T> list) {
        RSet<T> set = redissonClient.getSet(key);
        set.addAll(list);
    }

    @Override
    public boolean isSetMember(String key, String value) {
        RSet<String> set = redissonClient.getSet(key);
        return set.contains(value);
    }

    @Override
    public <T> boolean tryAddToScoredSet(String key, T value, double expireTimestamp) {
        RScoredSortedSet<T> sortedSet = redissonClient.getScoredSortedSet(key);

        return sortedSet.tryAdd(expireTimestamp, value);
    }

    @Override
    public <T> Double getScoreFromScoredSet(String key, T value) {
        RScoredSortedSet<T> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.getScore(value);
    }

    @Override
    public void addToList(String key, String value) {
        RList<String> list = redissonClient.getList(key);
        list.add(value);
    }

    @Override
    public String getFromList(String key, int index) {
        RList<String> list = redissonClient.getList(key);
        return list.get(index);
    }

    @Override
    public <K, V> void setMap(String key, Map<K, V> data) {
        redissonClient.getMap(key).putAll(data);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String key) {
        return redissonClient.getMap(key);
    }

    @Override
    public void addToMap(String key, String field, String value) {
        RMap<String, String> map = redissonClient.getMap(key);
        map.put(field, value);
    }

    @Override
    public Long incrFieldFromMap(String key, String field, long value) {
        RMap<String, Long> map = redissonClient.getMap(key);
        return map.addAndGet(field, value);
    }

    @Override
    public <K, V> Map<K, V> getListFieldFromMap(String key, Set<K> fields) {
        RMap<K, V> map = redissonClient.getMap(key);
        return map.getAll(fields);
    }

    @Override
    public String getFromMap(String key, String field) {
        RMap<String, String> map = redissonClient.getMap(key);
        return map.get(field);
    }

    @Override
    public <K, V> V getFromMap(String key, K field) {
        return redissonClient.<K, V>getMap(key).get(field);
    }

    @Override
    public void addToSortedSet(String key, String value) {
        RSortedSet<String> sortedSet = redissonClient.getSortedSet(key);
        sortedSet.add(value);
    }

    @Override
    public <T> void addToScoredSet(String key, T value, double score) {
        RScoredSortedSet<T> sortedSet = redissonClient.getScoredSortedSet(key);
        sortedSet.add(score, value);
    }


    @Override
    public <T> void addBatchToScoredSet(String key, Map<T, Double> data) {
        RScoredSortedSet<T> sortedSet = redissonClient.getScoredSortedSet(key);
        sortedSet.addAll(data);
    }

    @Override
    public <T> void deleteToScoredSet(String key, T value) {
        RScoredSortedSet<T> sortedSet = redissonClient.getScoredSortedSet(key);
        sortedSet.remove(value);
    }

    @Override
    public <T> Map<T, Double> getMapFromScoredSet(String key, Double cursorScore, int size) {
        RScoredSortedSet<T> sortedSet = redissonClient.getScoredSortedSet(key);
        if (sortedSet.isEmpty()) {
            return null;
        }
        double start = cursorScore == null ? Double.MAX_VALUE : cursorScore;
        Collection<ScoredEntry<T>> entries = sortedSet.entryRangeReversed(start, false, 0, true, 0, size);

        if (entries == null || entries.isEmpty()) {
            return null;
        }

        return entries.stream()
                .collect(Collectors.toMap(
                        ScoredEntry::getValue,
                        ScoredEntry::getScore));
    }


    @Override
    public <T> Map<T, Boolean> isMemberListInScoredSet(String key, List<T> list) {
        Map<T, Boolean> statusMap = new HashMap<>(list.size());
        RBatch batch = redissonClient.createBatch();
        Map<T, RFuture<Double>> futures = new HashMap<>();
        for (T item : list) {
            // getScoreAsync会立即返回一个RFuture对象，并将命令放入队列
            RFuture<Double> future = batch.getScoredSortedSet(key).getScoreAsync(item);
            futures.put(item, future);
        }
        batch.execute();
        for (Map.Entry<T, RFuture<Double>> entry : futures.entrySet()) {
            try {
                // 如果get()返回的不是null，说明该成员在ZSET中存在
                statusMap.put(entry.getKey(), entry.getValue().get() != null);
            } catch (InterruptedException | ExecutionException e) {
                // 异常处理，记录日志并默认为不存在
                statusMap.put(entry.getKey(), false);
//                //中断线程防止线程不回收
//                Thread.currentThread().interrupt();
                log.error("isMemberListInScoredSet:Rbatch处理失败");
            }
        }
        return statusMap;
    }

    @Override
    public <K> boolean isMemberInScoredSet(String key, K value) {
        RScoredSortedSet<K> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.contains(value);
    }

    @Override
    public RLock getLock(String key) {
        return redissonClient.getLock(key);
    }

    @Override
    public RLock getFairLock(String key) {
        return redissonClient.getFairLock(key);
    }

    @Override
    public RReadWriteLock getReadWriteLock(String key) {
        return redissonClient.getReadWriteLock(key);
    }

    @Override
    public RSemaphore getSemaphore(String key) {
        return redissonClient.getSemaphore(key);
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(String key) {
        return redissonClient.getPermitExpirableSemaphore(key);
    }

    @Override
    public RCountDownLatch getCountDownLatch(String key) {
        return redissonClient.getCountDownLatch(key);
    }

    @Override
    public <T> RBloomFilter<T> getBloomFilter(String key) {
        return redissonClient.getBloomFilter(key);
    }


    @Override
    public Boolean setNx(String key) {
        return redissonClient.getBucket(key).trySet("lock");
    }

    @Override
    public Boolean setNx(String key, long expired, TimeUnit timeUnit) {
        return redissonClient.getBucket(key).trySet("lock", expired, timeUnit);
    }

    @Override
    public RBitSet getBitSet(String key) {
        return redissonClient.getBitSet(key);
    }

    @Override
    public RScript getScriptLongCodec() {
        return redissonClient.getScript(LongCodec.INSTANCE);
    }

}
