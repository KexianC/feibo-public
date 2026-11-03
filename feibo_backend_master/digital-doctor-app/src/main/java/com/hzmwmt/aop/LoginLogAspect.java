package com.hzmwmt.aop;

import com.hzmwmt.domain.entity.log.TLoginLog;
import com.hzmwmt.domain.service.log.LoginLogService;
import com.hzmwmt.infrastructure.dao.UserMapper;
import com.hzmwmt.infrastructure.dao.po.User;
import com.hzmwmt.types.annotations.LoginLog;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 登录日志AOP切面
 * 用于拦截标记了@LoginLog注解的方法并记录登录日志
 *
 * @author AI Assistant
 * @version v1.0
 * @Date: 2025-11-05
 */
@Slf4j
@Aspect
@Component
@Order(9) // 登录日志切面优先级高于操作日志
public class LoginLogAspect {

    @Resource
    private LoginLogService loginLogService;

    @Resource
    private UserMapper userMapper;
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Around("@annotation(loginLog)")
    public Object around(ProceedingJoinPoint joinPoint, LoginLog loginLog) throws Throwable {
        // 1. 准备登录日志信息
        TLoginLog logEntity = new TLoginLog();

        // 设置默认登录地区（必填字段）
        logEntity.setLoginRegion("未知地区");

        // 尝试从方法参数中获取用户名
        Object[] args = joinPoint.getArgs();
        String loginUser = extractLoginUser(args);
        logEntity.setLoginUser(loginUser);

        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null && attributes.getRequest() != null) {
            // 记录登录IP
            if (loginLog.recordIp()) {
                logEntity.setLoginIp(attributes.getRequest().getRemoteAddr());
            }
            // 记录浏览器信息
            logEntity.setLoginUserAgent(truncate(attributes.getRequest().getHeader("User-Agent"), 512));
        }

        // 2. 执行业务方法
        boolean loginSuccess = false;
        Object result;
        try {
            // 执行业务方法并记录登录状态
            result = joinPoint.proceed();
            // 假设返回true或包含success=true表示登录成功
            loginSuccess = isLoginSuccess(result);
            logEntity.setLoginStatus(loginSuccess ? "登录成功" : "登录失败");
        } catch (Throwable throwable) {
            logEntity.setLoginStatus("登录失败");
            log.warn("登录失败，审计状态已记录");
            throw throwable;
        } finally {
            // 记录登录时间
            if (loginLog.recordTime()) {
                logEntity.setLoginTime(new Date());
            }

            // 保存登录日志
            saveLoginLog(logEntity);
        }

        return result;
    }

    /**
     * 从方法参数中提取登录用户名
     */
    private String extractLoginUser(Object[] args) {
        if (args == null || args.length == 0) {
            return "unknown";
        }

        // 尝试从参数中提取用户名
        for (Object arg : args) {
            if (arg == null) continue;

            // 优先尝试获取手机号，然后根据手机号查询用户名
            try {
                // 尝试获取phoneNumber字段
                Method getPhoneNumber = arg.getClass().getMethod("getPhoneNumber");
                Object phoneNumber = getPhoneNumber.invoke(arg);
                if (phoneNumber != null && phoneNumber instanceof String) {
                    String phone = (String) phoneNumber;
                    // 根据手机号查询用户信息
                    User user = userMapper.selectUserByPhoneNumber(phone);
                    if (user != null && user.getUserId() != null) {
                        // Persist a stable identifier instead of nickname/phone PII.
                        return String.valueOf(user.getUserId());
                    }
                    // Unknown accounts must not persist a complete phone number.
                    return maskPhone(phone);
                }
            } catch (Exception e) {
                // 忽略反射异常，继续尝试其他方式
            }

            // 如果参数是String，假设是用户名
            if (arg instanceof String) {
                return (String) arg;
            }

            // 尝试通过反射获取username/account字段
            try {
                // 尝试获取username字段
                Method getUsername = arg.getClass().getMethod("getUsername");
                Object username = getUsername.invoke(arg);
                if (username != null) {
                    return username.toString();
                }

                // 尝试获取account字段
                Method getAccount = arg.getClass().getMethod("getAccount");
                Object account = getAccount.invoke(arg);
                if (account != null) {
                    return account.toString();
                }

                // 尝试获取loginUser字段
                Method getLoginUser = arg.getClass().getMethod("getLoginUser");
                Object loginUserObj = getLoginUser.invoke(arg);
                if (loginUserObj != null) {
                    return loginUserObj.toString();
                }
            } catch (Exception e) {
                // 忽略反射异常
            }
        }

        return "unknown";
    }

    /**
     * 判断登录是否成功
     */
    private boolean isLoginSuccess(Object result) {
        if (result == null) {
            return false;
        }

        // Standard API responses carry success in their business code. A
        // non-throwing failure response must never be recorded as success.
        try {
            Method getCode = result.getClass().getMethod("getCode");
            Object code = getCode.invoke(result);
            if (code != null) {
                return "0000".equals(String.valueOf(code));
            }
        } catch (Exception ignored) {
            // Continue with compatibility fallbacks below.
        }

        // 布尔类型直接返回
        if (result instanceof Boolean) {
            return (Boolean) result;
        }

        // 对象类型尝试获取success字段
        try {
            Method getSuccess = result.getClass().getMethod("getSuccess");
            Object success = getSuccess.invoke(result);
            if (success instanceof Boolean) {
                return (Boolean) success;
            }
        } catch (Exception e) {
            // 忽略反射异常
        }

        // 默认认为登录成功（如果没有抛出异常）
        return true;
    }

    /**
     * 保存登录日志
     */
    private void saveLoginLog(TLoginLog logEntity) {
        threadPoolExecutor.submit(() -> {
            try {
                // 使用线程池异步保存登录日志到数据库
                boolean saved = loginLogService.save(logEntity);
                if (!saved) {
                    log.error("登录日志保存失败");
                } else {
                    log.debug("登录日志保存成功，状态={}", logEntity.getLoginStatus());
                }
            } catch (Exception e) {
                log.error("保存登录日志异常，异常类型: {}",
                        e.getClass().getSimpleName());
            }
        });
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "unknown";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
