package com.hzmwmt.infrastructure.adapter.repository;

import com.hzmwmt.infrastructure.redis.IRedisService;
import com.hzmwmt.types.common.CursorPageResponseData;
import com.hzmwmt.types.common.OffsetPageResponseData;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName AbstractRepository
 * @Date: 2025/8/18 9:26
 */
@Slf4j
public class AbstractRepository {
    private final Logger logger = LoggerFactory.getLogger(AbstractRepository.class);
    @Resource
    protected IRedisService redissonService;

    /***
     * @Author huihui
     * @Description //TODO  数据库游标分页模板
     * @param dbFallback   数据库查询逻辑 (返回PO列表)，通常查询 size + 1 条数据
     * @param idExtractor  从PO中提取作为下一页游标的ID (通常是业务ID)
     * @param converter    将单个PO对象转换为领域实体的转换器
     * @param size         期望的每页大小
     * @param <P>          持久化对象类型
     * @param <E>          领域实体类型
     * @Param [java.util.function.Supplier<java.util.List < T>>, java.util.function.Function<T,java.lang.Long>, java.util.function.Function<T,java.lang.Long>, int]
     * @return com.hzmwmt.types.common.CursorPageResponseData<T>
     * @Date 2025/8/18 13:57
     **/
    protected <P, E> CursorPageResponseData<E> getFromDbCursorPage(
            Supplier<List<P>> dbFallback,
            Function<P, Long> idExtractor,
            Function<P, E> converter,
            int size
    ) {
        List<P> poList = dbFallback.get();
        if (poList == null || poList.isEmpty()) {
            return CursorPageResponseData.<E>builder()
                    .data(null)
                    .hasNext(false)
                    .nextCursorId(null)
                    .build();
        }
        boolean hasNext = poList.size() > size;
        List<P> currentPagePoList = hasNext ? poList.subList(0, size) : poList;
        //领域类型转换
        List<E> entityList = currentPagePoList.stream()
                .map(converter)
                .collect(Collectors.toList());


        Long nextCursorId = null;
        if (hasNext) {
            // 从PO列表的最后一个元素获取游标
            P lastItemOnPage = currentPagePoList.get(size - 1);
            nextCursorId = idExtractor.apply(lastItemOnPage);
        }

        // 构建并返回最终响应对象
        return CursorPageResponseData.<E>builder()
                .data(entityList)
                .hasNext(hasNext)
                .nextCursorId(nextCursorId)
                .build();
    }

    /****
     * @Author huihui
     * @Description //TODO 不需要转换
     * @Param [java.util.function.Supplier<java.util.List < P>>, java.util.function.Function<P,java.lang.Long>, int]
     * @return com.hzmwmt.types.common.CursorPageResponseData<E>
     * @Date 2025/8/22 10:41
     **/

    protected <T> CursorPageResponseData<T> getFromDbCursorPage(
            Supplier<List<T>> dbFallback,
            Function<T, Long> idExtractor,
            int size
    ) {
        // Function.identity()，表示不进行任何转换
        return getFromDbCursorPage(dbFallback, idExtractor, Function.identity(), size);
    }
//    /****
//     * @Author huihui
//     * @Description //TODO Map缓存构建+构建模板
//     * @Param [java.lang.String, java.util.function.Supplier<R>, Predicate<R>, java.util.function.Supplier<R>, Consumer<R>]
//     * @return R
//     * @Date  2025/8/19 14:20
//     **/
//    protected <E, K, V> Map<K, V> getMapFromCacheOrDb(
//            String cacheKey,
//            Supplier<List<E>> dbReader,
//            Function<E, K> keyExtractor,
//            Function<E, V> valueExtractor
//    ) {
//        RMap<K, V> cacheMap = redissonService.getMap(cacheKey);
//
//        if (cacheMap != null && !cacheMap.isEmpty()) {
//            logger.debug("缓存命中 key: {}", cacheKey);
//            return cacheMap.readAllMap();
//        }
//
//        logger.debug("缓存未命中 key: {}. 开始查询数据库。", cacheKey);
//        List<E> dataFromDb = dbReader.get();
//
//        if (dataFromDb == null || dataFromDb.isEmpty()) {
//            logger.warn("数据库中未找到 key: {} 的分类信息。", cacheKey);
//            //有空可以加防止缓存穿透的实现
//            return null;
//        }
//
//        Map<K, V> dataToCache = dataFromDb.stream()
//                .collect(Collectors.toMap(keyExtractor, valueExtractor));
//        redissonService.setMap(cacheKey,dataToCache);
//
//        return dataToCache;
//    }

    /***
     * @Author huihui
     * @Description //TODO 去除机器ID
     * @Param [long]
     * @return long
     * @Date 2025/8/21 10:51
     **/
    protected long removeMachineBitsIntSnowflakeId(long snowflakeId) {
        // 雪花ID结构: [41位时间戳][10位机器ID][12位序列号]
        long timestampBits = (snowflakeId >> 22) & 0x1FFFFFFFFFFL;

        long sequenceBits = snowflakeId & 0xFFFL;

        return (timestampBits << 12) | sequenceBits;
    }

    /***
     * @Author huihui
     * @Description //TODO 链式过滤查询
     * @Param [java.util.function.Supplier<java.util.List < java.lang.Long>>...]
     * @return java.util.Set<java.lang.Long>
     * @Date 2025/8/21 11:20
     **/
    protected List<Long> executeDynamicFiltering(
            List<Long> filterIds,
            BiFunction<Long, List<Long>, List<Long>> queryFunction) {

        if (filterIds == null || filterIds.isEmpty()) {
            return null;
        }

        List<Long> currentIds = null;

        for (Long filterId : filterIds) {
            if (filterId != null) {
                currentIds = queryFunction.apply(filterId, currentIds);

                if (currentIds == null || currentIds.isEmpty()) {
                    return new ArrayList<>();
                }
            }
        }

        return currentIds != null ? currentIds : new ArrayList<>();
    }

    /***
     * @Author huihui
     * @Description //TODO  原子操作模版（无返回值）。
     * @Param lockKey       锁的唯一标识符
     * @Param leaseTime     锁的自动释放时间（租约时间），防止服务宕机导致死锁
     * @Param timeUnit      leaseTime 的时间单位
     * @Param businessLogic 获取锁后需要执行的业务逻辑，以 Runnable 形式传入
     * @Date 2025/8/28 11:20
     **/
    public void execute(String lockKey, long leaseTime, TimeUnit timeUnit, Runnable businessLogic) {
        this.<Void>execute(lockKey, leaseTime, timeUnit, () -> {
            businessLogic.run();
            return null;
        });
    }

    /***
     * @Author huihui
     * @Description //TODO  原子操作模版（有返回值）。
     * @Param lockKey       锁的唯一标识符
     * @Param leaseTime     锁的自动释放时间（租约时间），防止服务宕机导致死锁
     * @Param timeUnit      leaseTime 的时间单位
     * @Param businessLogic 获取锁后需要执行并返回结果的业务逻辑，以 Supplier 形式传入
     * @return T
     * @Date 2025/8/28 11:20
     **/
    public <T> T execute(String lockKey, long leaseTime, TimeUnit timeUnit, Supplier<T> businessLogic) {
        RLock lock = redissonService.getLock(lockKey);
        boolean isLocked = false;
        try {
            // 尝试获取锁，最多等待3秒
            isLocked = lock.tryLock(5, leaseTime, timeUnit);
            if (isLocked) {
                // 成功获取锁，执行业务代码并返回结果
                log.debug("成功获取仓储分布式锁");
                return businessLogic.get();
            } else {
                // 在等待时间内未能获取锁，视为重复操作
                log.warn("获取仓储分布式锁失败，重复操作");
                throw new AppException(ResponseCode.REPEATED_OPERATION_EXCEPTION);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取仓储分布式锁时线程被中断: type={}", e.getClass().getSimpleName());
            throw new AppException(ResponseCode.INTERNET_EXCEPTION);
        } catch (Exception e) {
            log.error("仓储分布式锁内业务逻辑执行异常: type={}", e.getClass().getSimpleName());
            throw e;
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("成功释放仓储分布式锁");
            }
        }
    }

    /**
     * @param idQueryLogic     根据 offset 和 size 查询ID列表的逻辑
     * @param countQueryLogic  查询总记录数的逻辑
     * @param entityQueryLogic 根据ID列表查询持久化对象(PO)列表的逻辑
     * @param idExtractor      从PO中提取ID的函数, 用于构建Map
     * @param converter        将单个PO对象转换为最终实体(Entity/VO)的转换器
     * @param page             当前页码
     * @param size             每页大小
     * @param <P>              持久化对象类型 (如: User)
     * @param <E>              最终实体类型 (如: UserEntity)
     * @return com.your.package.OffsetPageResponseData<E>
     * @Author huihui (Generated by Gemini)
     * @Description //TODO 数据库 Offset 分页模板 (先查ID，再查详情)
     * @Date 2025/9/7 16:20
     **/
    protected <P, E> OffsetPageResponseData<E> getFromDbOffsetPage(
            BiFunction<Long, Integer, List<Long>> idQueryLogic,
            Supplier<Long> countQueryLogic,
            Function<List<Long>, List<P>> entityQueryLogic,
            Function<P, Long> idExtractor,
            Function<P, E> converter,
            int page,
            int size
    ) {
        // 1. 计算分页参数
        long offset = (long) (page - 1) * size;

        // 2. 执行数据库查询，获取ID列表和总数
        List<Long> ids = idQueryLogic.apply(offset, size);

        Long count = countQueryLogic.get();

        // 如果ID列表为空，提前返回，避免无效的后续查询
        if (ids == null || ids.isEmpty()) {
            return OffsetPageResponseData.<E>builder()
                    .data(null)
                    .size(size)
                    .page(page)
                    .total(count)
                    .hasNext(false)
                    .build();
        }

        // 3. 根据ID列表获取持久化对象(PO)
        List<P> poList = entityQueryLogic.apply(ids);

        // 4. 内存中进行数据组装和排序保持
        Map<Long, P> poMap = poList.stream()
                .collect(Collectors.toMap(idExtractor, Function.identity()));

        List<E> entityList = ids.stream()
                .map(poMap::get)
                .filter(java.util.Objects::nonNull)
                .map(converter)
                .collect(Collectors.toList());

        // 构建并返回最终响应对象
        return OffsetPageResponseData.<E>builder()
                .data(entityList)
                .size(size)
                .page(page)
                .total(count)
                .hasNext(count > offset + size)
                .build();
    }
}


