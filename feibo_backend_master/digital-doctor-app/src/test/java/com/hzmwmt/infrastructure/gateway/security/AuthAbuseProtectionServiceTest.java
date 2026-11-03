package com.hzmwmt.infrastructure.gateway.security;

import com.hzmwmt.infrastructure.properties.AuthProtectionProperties;
import com.hzmwmt.types.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthAbuseProtectionServiceTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RScript script;
    @Mock
    private RBucket<Object> bucket;

    private AuthAbuseProtectionService service;

    @BeforeEach
    void setUp() {
        AuthProtectionProperties properties = new AuthProtectionProperties();
        service = new AuthAbuseProtectionService(redissonClient, properties);
    }

    @Test
    void allowsSmsOnlyWhenEveryServerSideLimitAcceptsAndHashesPiiKeys() {
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doReturn(1L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                any());

        assertDoesNotThrow(() -> service.assertSmsSendAllowed("13175144211", "203.0.113.9"));

        @SuppressWarnings("unchecked")
        var keysCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(script, times(3)).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.INTEGER),
                keysCaptor.capture(),
                any(),
                any());
        keysCaptor.getAllValues().forEach(keys -> {
            String redisKey = String.valueOf(keys.get(0));
            assertFalse(redisKey.contains("13175144211"));
            assertFalse(redisKey.contains("203.0.113.9"));
        });
    }

    @Test
    void rejectsWhenAnyDistributedLimitIsExhausted() {
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doReturn(1L, 0L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                any());

        assertThrows(RateLimitException.class,
                () -> service.assertSmsSendAllowed("13175144211", "203.0.113.9"));
        verify(script, times(2)).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.INTEGER),
                anyList(),
                any(),
                any());
    }

    @Test
    void successfulLoginClearsOnlyTheAccountCounter() {
        when(redissonClient.getBucket(anyString(), eq(StringCodec.INSTANCE))).thenReturn(bucket);

        service.clearSuccessfulLogin("13175144211");

        verify(redissonClient).getBucket(anyString(), eq(StringCodec.INSTANCE));
        verify(bucket).delete();
    }
}
