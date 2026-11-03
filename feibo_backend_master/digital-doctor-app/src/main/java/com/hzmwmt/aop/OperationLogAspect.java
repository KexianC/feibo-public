package com.hzmwmt.aop;

import com.hzmwmt.domain.auth.model.entity.AuthUserEntity;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.entity.log.TOperationLogs;
import com.hzmwmt.domain.service.log.OperationLogsService;
import com.hzmwmt.types.annotations.OperationLog;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigInteger;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 操作日志AOP切面
 * 用于拦截标记了@OperationLog注解的方法并记录操作日志
 *
 * @author AI Assistant
 * @version v1.0
 * @Date: 2025-11-05
 */
@Slf4j
@Aspect
@Component
@Order(10) // 设置切面优先级
public class OperationLogAspect {

    @Resource
    private OperationLogsService operationLogsService;
    @Resource
    private IAuthService authService;
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    // 使用更明确的切入点表达式，确保能够匹配所有使用@OperationLog注解的方法
    @Around("@annotation(com.hzmwmt.types.annotations.OperationLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法上的注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(
                signature.getMethod(),
                joinPoint.getTarget().getClass());
        OperationLog operationLog = AnnotatedElementUtils.findMergedAnnotation(method, OperationLog.class);

        // 如果注解为null，直接执行方法
        if (operationLog == null) {
            log.warn("OperationLog注解为null，可能是切入点表达式匹配问题");
            return joinPoint.proceed();
        }
        if (operationLog.critical()
                && !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Critical operation auditing requires an active business transaction");
        }
        // 添加详细的进入切面日志
        log.info("========== 进入OperationLogAspect切面 ==========");
        log.info("拦截到带@OperationLog注解的方法，operationType: {}, description: {}",
                operationLog.operationType(), operationLog.description());

        // 方法信息已在上边获取
        String methodInfo = method.getDeclaringClass().getName() + "." + method.getName();
        log.info("拦截的方法: {}", methodInfo);

        // 1. 准备日志信息
        TOperationLogs logEntity = new TOperationLogs();
        logEntity.setOperationTime(new Date());
        log.info("创建日志实体，初始操作时间: {}", logEntity.getOperationTime());

        // Only an explicitly configured positive integral scalar may identify
        // the audit target. Other arguments (especially request bodies and
        // credentials) are never serialized or inspected.
        String targetIdSuffix = safeTargetIdSuffix(
                operationLog, joinPoint.getArgs());
        logEntity.setOperationDesc(operationLog.description()
                + "(" + operationLog.operationType() + ")"
                + targetIdSuffix);
        log.info("设置操作描述: {}", logEntity.getOperationDesc());

        // 获取当前操作用户信息
        String operator = getCurrentUser();
        logEntity.setOperator(operator);
        log.info("获取当前用户: {}", operator);

        // 获取请求URL和方法
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            log.info("获取到ServletRequestAttributes");
            if (attributes.getRequest() != null) {
                log.info("获取到HttpServletRequest");
                logEntity.setOperationMethod(attributes.getRequest().getMethod());
                // 获取真实IP地址（考虑代理情况）
                String ip = getRealIp(attributes.getRequest());
                logEntity.setOperatorIp(ip);
                // 获取IP对应的地理位置
                String region = com.hzmwmt.infrastructure.util.ip.IpRegionUtil.getRegion(ip);
                logEntity.setLoginRegion(region);
                log.info("IP地理位置解析完成，resolved: {}", region != null && !region.isEmpty());
                // 记录浏览器信息
                logEntity.setLoginDevice(attributes.getRequest().getHeader("User-Agent"));
                log.info("设置请求信息 - 方法: {}", logEntity.getOperationMethod());
            } else {
                log.warn("ServletRequestAttributes存在，但HttpServletRequest为null");
            }
        } else {
            log.warn("ServletRequestAttributes为null");
        }

        // 将方法信息添加到操作描述中
        logEntity.setOperationDesc(logEntity.getOperationDesc() + " - " + method.getDeclaringClass().getSimpleName() + "." + method.getName());
        log.info("更新后的操作描述: {}", logEntity.getOperationDesc());

        // 2. 执行业务方法
        long startTime = System.currentTimeMillis();
        Object result;
        Throwable businessFailure = null;
        try {
            log.info("准备执行业务方法");
            result = joinPoint.proceed();
            log.info("业务方法执行完成");
            // 记录成功状态
            logEntity.setOperationDesc(logEntity.getOperationDesc() + " - 成功");
            log.info("业务执行成功，更新操作描述");
        } catch (Throwable throwable) {
            businessFailure = throwable;
            // 记录失败状态和错误信息
            logEntity.setOperationDesc(logEntity.getOperationDesc() + " - 失败");
            log.error("业务方法执行失败: operationType={}, errorType={}",
                    operationLog.operationType(), throwable.getClass().getSimpleName());
            throw throwable;
        } finally {
            // 记录执行时间
            long executeTime = System.currentTimeMillis() - startTime;
            logEntity.setOperationDesc(logEntity.getOperationDesc() + " (" + executeTime + "ms)");
            log.info("业务方法执行时间: {}ms，最终操作描述: {}", executeTime, logEntity.getOperationDesc());

            // Critical privileged writes are saved in the active business
            // transaction. Ordinary operational telemetry remains asynchronous.
            try {
                if (operationLog.critical()) {
                    saveCriticalLog(logEntity);
                } else {
                    saveLogAsync(logEntity);
                }
            } catch (RuntimeException auditFailure) {
                if (businessFailure != null) {
                    businessFailure.addSuppressed(auditFailure);
                } else {
                    throw auditFailure;
                }
            }
            log.info("========== 退出OperationLogAspect切面 ==========");
        }

        return result;
    }

    /**
     * 获取当前用户信息
     */
    private String getCurrentUser() {
        try {
            // 首先尝试从SecurityContextHolder获取当前用户
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof AuthUserEntity) {
                AuthUserEntity authUser = (AuthUserEntity) authentication.getPrincipal();
                if (authUser.getUserId() != null) {
                    return String.valueOf(authUser.getUserId());
                }
            }

            // 如果SecurityContextHolder中没有获取到用户，尝试使用AuthService
            try {
                AuthUserEntity validatedUser = authService.queryValidatedUser();
                if (validatedUser != null) {
                    if (validatedUser.getUserId() != null) {
                        return String.valueOf(validatedUser.getUserId());
                    }
                }
            } catch (Exception e) {
                // AuthService可能抛出异常，记录并继续尝试其他方式
                log.warn("通过AuthService获取用户信息失败，异常类型: {}",
                        e.getClass().getSimpleName());
            }

        } catch (Exception e) {
            log.error("获取当前用户信息时发生异常，异常类型: {}",
                    e.getClass().getSimpleName());
        }
        return "anonymous";
    }

    private String safeTargetIdSuffix(
            OperationLog operationLog,
            Object[] arguments) {
        int parameterIndex = operationLog.targetIdParameterIndex();
        if (parameterIndex == -1) {
            return "";
        }
        if (parameterIndex < 0
                || arguments == null
                || parameterIndex >= arguments.length) {
            throw new IllegalStateException(
                    "Operation audit target index is invalid");
        }

        Object candidate = arguments[parameterIndex];
        BigInteger targetId;
        if (candidate instanceof Byte
                || candidate instanceof Short
                || candidate instanceof Integer
                || candidate instanceof Long) {
            targetId = BigInteger.valueOf(((Number) candidate).longValue());
        } else if (candidate instanceof BigInteger integer) {
            targetId = integer;
        } else {
            throw new IllegalStateException(
                    "Operation audit target must be a positive integer scalar");
        }
        if (targetId.signum() <= 0 || targetId.bitLength() > 63) {
            throw new IllegalStateException(
                    "Operation audit target must be a positive integer scalar");
        }
        return " [targetId=" + targetId + "]";
    }

    /**
     * 使用线程池异步保存日志
     *
     * @param logEntity 日志实体
     */
    private void saveLogAsync(final TOperationLogs logEntity) {
        log.info("========== 准备异步保存操作日志 ==========");

        // 确保日志实体不为空
        if (logEntity == null) {
            log.error("日志实体为null，无法保存");
            return;
        }

        // 记录当前线程信息
        log.info("当前调用线程: {}", Thread.currentThread().getName());

        try {
            // 使用线程池异步执行保存操作
            if (threadPoolExecutor != null) {
                log.info("使用线程池异步执行保存日志，线程池状态: 活跃线程数={}, 队列任务数={}",
                        threadPoolExecutor.getActiveCount(), threadPoolExecutor.getQueue().size());

                threadPoolExecutor.execute(() -> {
                    doSaveLog(logEntity);
                });
                log.info("异步保存任务已提交到线程池");
            } else {
                // 线程池未注入，同步执行保存
                log.warn("线程池未注入，将同步执行保存日志操作");
                doSaveLog(logEntity);
            }

            log.info("========== 异步保存操作日志提交完成 ==========");
        } catch (Exception e) {
            log.error("提交异步保存任务异常，异常类型: {}",
                    e.getClass().getSimpleName());
        }
    }

    private void saveCriticalLog(TOperationLogs logEntity) {
        if (logEntity == null || operationLogsService == null) {
            throw new IllegalStateException("Critical operation audit service is unavailable");
        }
        if (!operationLogsService.save(logEntity)) {
            throw new IllegalStateException("Critical operation audit could not be persisted");
        }
    }

    /**
     * 实际执行日志保存的方法
     */
    /**
     * 获取真实的IP地址，考虑代理情况
     */
    private String getRealIp(jakarta.servlet.http.HttpServletRequest request) {
        // Spring/Tomcat normalizes trusted proxy headers because the server is
        // configured with forward-headers-strategy=native. Never trust raw
        // client-supplied forwarding headers in application code.
        return request.getRemoteAddr();
    }

    private void doSaveLog(TOperationLogs logEntity) {
        log.info("========== doSaveLog - 开始执行日志保存 ==========");
        log.info("执行线程: {}", Thread.currentThread().getName());

        try {
            // 确保实体数据完整性
            if (logEntity.getOperationTime() == null) {
                logEntity.setOperationTime(new Date());
                log.info("操作时间为空，已设置为当前时间: {}", logEntity.getOperationTime());
            }

            // 为必填字段设置默认值
            if (logEntity.getLoginRegion() == null) {
                logEntity.setLoginRegion("未知地区");
                log.info("登录地区为空，已设置默认值: 未知地区");
            }

            // 验证关键字段
            log.info("日志关键字段检查 - operator: {}, operationDesc: {}, operationMethod: {}",
                    logEntity.getOperator(), logEntity.getOperationDesc(), logEntity.getOperationMethod());

            // 检查并记录service是否正常注入
            if (operationLogsService == null) {
                log.error("operationLogsService未注入，无法保存操作日志");
                return;
            }

            log.info("operationLogsService已注入，准备调用save方法");

            // 先尝试直接调用save方法（非事务方式）
            boolean saved = false;
            try {
                log.info("尝试直接保存日志到数据库");
                saved = operationLogsService.save(logEntity);
                log.info("直接保存返回结果: {}", saved);
            } catch (Exception e) {
                log.error("直接保存失败，尝试使用事务方式，异常类型: {}",
                        e.getClass().getSimpleName());
                // 失败后尝试使用事务方式
                saved = operationLogsService.saveWithTransaction(logEntity);
                log.info("事务方式保存返回结果: {}", saved);
            }

            // 保存后再次检查日志实体状态
            log.debug("保存后日志实体状态 - 是否有ID: {}", logEntity.getLogId() != null);

            if (!saved) {
                log.error("操作日志保存失败");
            } else {
                log.info("操作日志保存成功: {}，日志ID: {}", logEntity.getOperationDesc(), logEntity.getLogId());
            }

            log.info("========== doSaveLog - 日志保存完成 ==========");
        } catch (Exception e) {
            log.error("保存操作日志异常，异常类型: {}",
                    e.getClass().getSimpleName());
        } catch (Throwable t) {
            log.error("保存操作日志发生严重错误，异常类型: {}",
                    t.getClass().getSimpleName());
        }
    }
}
