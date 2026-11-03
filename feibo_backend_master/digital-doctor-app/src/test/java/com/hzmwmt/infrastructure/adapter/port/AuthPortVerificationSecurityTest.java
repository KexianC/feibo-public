package com.hzmwmt.infrastructure.adapter.port;

import com.hzmwmt.infrastructure.dao.UserMapper;
import com.hzmwmt.infrastructure.dao.po.User;
import com.hzmwmt.infrastructure.gateway.security.AuthAbuseProtectionService;
import com.hzmwmt.infrastructure.gateway.sms.ISMSGateway;
import com.hzmwmt.infrastructure.redis.IRedisService;
import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static com.hzmwmt.types.common.Constants.RedisConstants.KEY_AUTH_CODE_PREFIX;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthPortVerificationSecurityTest {

    @Mock
    private IRedisService redisService;
    @Mock
    private ISMSGateway smsGateway;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuthAbuseProtectionService protectionService;

    private AuthPort authPort;

    @BeforeEach
    void setUp() {
        authPort = new AuthPort();
        ReflectionTestUtils.setField(authPort, "redissonService", redisService);
        ReflectionTestUtils.setField(authPort, "smsGateway", smsGateway);
        ReflectionTestUtils.setField(authPort, "userMapper", userMapper);
        ReflectionTestUtils.setField(authPort, "authAbuseProtectionService", protectionService);
    }

    @Test
    void exhaustedAttemptBudgetStopsBeforeReadingTheOtp() {
        doThrow(new AppException(ResponseCode.REPEATED_OPERATION_EXCEPTION))
                .when(protectionService).assertVerificationAttemptAllowed("13175144211");

        assertThrows(AppException.class,
                () -> authPort.authUserByVerificationCode("13175144211", "000000"));

        verify(redisService, never()).getValue(anyString());
    }

    @Test
    void validOtpIsConsumedOnceAndClearsAttemptCounter() {
        when(redisService.getValue(KEY_AUTH_CODE_PREFIX + "13175144211")).thenReturn("123456");

        assertDoesNotThrow(() -> authPort.authUserByVerificationCode("13175144211", "123456"));

        verify(protectionService).assertVerificationAttemptAllowed("13175144211");
        verify(redisService).remove(KEY_AUTH_CODE_PREFIX + "13175144211");
        verify(protectionService).clearVerificationAttempts("13175144211");
    }

    @Test
    void unknownPhoneReturnsWithoutSendingSmsOrCreatingAnOracleSideEffect() {
        when(userMapper.selectUserByPhoneNumber("13175144211")).thenReturn(null);

        assertDoesNotThrow(() -> authPort.sendAuthShortMessage("13175144211"));

        verify(smsGateway, never()).sendShortMessage(anyString(), anyString());
        verify(redisService, never()).setValue(anyString(), anyString(), eq(5));
    }

    @Test
    void existingPhoneReceivesSixDigitOtpWithFiveMinuteLifetime() {
        when(userMapper.selectUserByPhoneNumber("13175144211")).thenReturn(User.builder().build());

        authPort.sendAuthShortMessage("13175144211");

        verify(smsGateway).sendShortMessage(eq("13175144211"),
                org.mockito.ArgumentMatchers.matches("\\{\\\"code\\\":\\\"\\d{6}\\\"}"));
        verify(redisService).setValue(eq(KEY_AUTH_CODE_PREFIX + "13175144211"),
                org.mockito.ArgumentMatchers.matches("\\d{6}"), eq(5));
        verify(protectionService).clearVerificationAttempts("13175144211");
    }
}
