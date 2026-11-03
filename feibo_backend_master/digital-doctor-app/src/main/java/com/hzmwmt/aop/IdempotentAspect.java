package com.hzmwmt.aop;

import com.hzmwmt.types.annotations.Idempotent;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.HexFormat;
import java.util.regex.Pattern;

import static com.hzmwmt.types.common.Constants.RedisConstants.KEY_IDEMPOTENT_TOKEN_PREFIX;

/**
 * @author huihui
 * @version v1.0
 * @Description: TODO
 * @ClassName IdempotentAspect
 * @Date: 2025/9/10 22:14
 */
@Slf4j
@Aspect
@Component
public class IdempotentAspect {
    private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");

    @Resource
    private RedissonClient redissonClient;

    @Around("@annotation(com.hzmwmt.types.annotations.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取注解和请求信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        // 获取并校验请求头中的令牌
        String token = request.getHeader("Idempotency-Token");
        if (!StringUtils.hasText(token)) {
            // 强制要求请求头必须包含令牌
            log.warn("AOP -> 幂等性校验失败，请求头为空。");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "缺少幂等令牌");
        }
        token = token.trim();
        if (!SAFE_TOKEN.matcher(token).matches()) {
            log.warn("AOP -> 幂等性校验失败，请求头格式无效。");
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "幂等令牌格式无效");
        }

        Principal principal = request.getUserPrincipal();
        String callerScope = principal == null
                ? "anonymous:" + request.getRemoteAddr()
                : "principal:" + principal.getName();
        String tokenScope = callerScope + "|" + request.getMethod() + "|" + request.getRequestURI() + "|" + token;
        String redisKey = KEY_IDEMPOTENT_TOKEN_PREFIX + sha256(tokenScope);
        RBucket<String> bucket = redissonClient.getBucket(redisKey);

        // trySet 方法是原子的，它会尝试设置值并返回一个boolean结果
        // 如果key不存在，它会设置值和过期时间，并返回true
        // 如果key已存在，它不会做任何操作，并返回false
        boolean isFirstTime;
        if (idempotent.expireTime() != -1) {
            isFirstTime = bucket.trySet("1", idempotent.expireTime(), idempotent.timeUnit());
        } else {
            isFirstTime = bucket.trySet("1");
        }

        //判断结果
        if (isFirstTime) {
            // 成功写入，说明是第一次请求，执行业务逻辑
            log.debug("AOP -> 幂等性令牌校验通过。");
            try {
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                // 如果业务方法执行异常，删除Redis中的令牌，允许客户端使用相同令牌重试
                log.error("AOP -> 业务方法执行异常，即将删除幂等状态以允许重试。异常类型: {}",
                        throwable.getClass().getSimpleName());
                // 确保删除操作的成功
                boolean deleteSuccess = bucket.delete();
                if (!deleteSuccess) {
                    // 如果删除失败，可能意味着key已过期或网络问题
                    log.error("AOP -> 严重警告：删除幂等状态失败，可能导致客户端暂时无法重试。");
                }
                throw throwable;
            }

        } else {
            // 写入失败，说明令牌已存在，是重复请求，直接拦截
            log.warn("AOP -> 幂等性校验失败，检测到重复请求。");
            throw new AppException(ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode(), idempotent.message());
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
