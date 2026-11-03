package com.hzmwmt.aop;

import com.hzmwmt.infrastructure.redis.IRedisService;
import com.hzmwmt.types.annotations.DistributedLock;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName DistributedLockAspect
 * @Date: 2025/9/4 12:36
 */
@Slf4j
@Aspect
@Component
public class DistributedLockAspect {

    private final ExpressionParser parser = new SpelExpressionParser();
    @Resource
    private IRedisService redissonService;

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = parseKey(distributedLock.key(), joinPoint);
        RLock lock = redissonService.getLock(lockKey);
        boolean isLocked = false;
        try {
            // 尝试获取锁，最多等待3秒
            isLocked = lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.unit());
            if (isLocked) {
                // 成功获取锁，执行业务代码并返回结果
                log.debug("成功获取分布式锁");
                return joinPoint.proceed();
            } else {
                // 在等待时间内未能获取锁，视为重复操作
                log.warn("AOP -> 获取分布式锁失败 (超时或被占用)");
                throw new AppException(ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode(), "获取分布式锁失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("AOP -> 获取分布式锁时线程被中断: type={}", e.getClass().getSimpleName());
            throw new RuntimeException("服务器繁忙，获取锁时被中断", e);
        } catch (Exception e) {
            log.error("分布式锁内业务逻辑执行异常: type={}", e.getClass().getSimpleName());
            throw e;
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("成功释放分布式锁");
            }
        }
    }

    private String parseKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] params = signature.getParameterNames();

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < params.length; i++) {
            context.setVariable(params[i], args[i]);
        }
        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
