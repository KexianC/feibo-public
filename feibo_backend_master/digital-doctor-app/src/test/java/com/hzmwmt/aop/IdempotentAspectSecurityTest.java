package com.hzmwmt.aop;

import com.hzmwmt.types.annotations.Idempotent;
import com.hzmwmt.types.exception.AppException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentAspectSecurityTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RBucket<String> bucket;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private IdempotentAspect aspect;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        aspect = new IdempotentAspect();
        ReflectionTestUtils.setField(aspect, "redissonClient", redissonClient);
        request = new MockHttpServletRequest("POST", "/api/v1/test");
        request.setRemoteAddr("203.0.113.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void validTokenUsesBoundHashedRedisKeyAndFiniteTtl() throws Throwable {
        prepareInvocation();
        String token = "request-token_123456";
        request.addHeader("Idempotency-Token", token);
        var keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(redissonClient.<String>getBucket(keyCaptor.capture())).thenReturn(bucket);
        when(bucket.trySet("1", 2, TimeUnit.MINUTES)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.around(joinPoint));

        String redisKey = keyCaptor.getValue();
        assertFalse(redisKey.contains(token));
        assertFalse(redisKey.contains("203.0.113.9"));
        verify(bucket).trySet("1", 2, TimeUnit.MINUTES);
    }

    @Test
    void malformedTokenIsRejectedBeforeRedisOrBusinessLogic() {
        prepareInvocation();
        request.addHeader("Idempotency-Token", "../../unsafe token");

        assertThrows(AppException.class, () -> aspect.around(joinPoint));

        verify(redissonClient, never()).getBucket(anyString());
    }

    @Test
    void annotationDefaultIsBounded() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("defaultTtl");
        Idempotent annotation = method.getAnnotation(Idempotent.class);

        assertEquals(1440, annotation.expireTime());
        assertEquals(TimeUnit.MINUTES, annotation.timeUnit());
    }

    private void prepareInvocation() {
        try {
            Method method = Fixture.class.getDeclaredMethod("mutate");
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(method);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    private static class Fixture {
        @Idempotent(expireTime = 2)
        void mutate() {
        }

        @Idempotent
        void defaultTtl() {
        }
    }
}
