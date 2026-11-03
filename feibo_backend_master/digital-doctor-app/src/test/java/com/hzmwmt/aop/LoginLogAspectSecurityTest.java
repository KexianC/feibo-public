package com.hzmwmt.aop;

import com.hzmwmt.api.dto.AuthUserByPasswordRequestDTO;
import com.hzmwmt.api.response.Response;
import com.hzmwmt.domain.entity.log.TLoginLog;
import com.hzmwmt.domain.service.log.LoginLogService;
import com.hzmwmt.infrastructure.dao.UserMapper;
import com.hzmwmt.types.annotations.LoginLog;
import com.hzmwmt.types.enums.ResponseCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginLogAspectSecurityTest {

    @Mock
    private LoginLogService loginLogService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ThreadPoolExecutor executor;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private LoginLog loginLog;

    private LoginLogAspect aspect;
    private AuthUserByPasswordRequestDTO loginRequest;

    @BeforeEach
    void setUp() {
        aspect = new LoginLogAspect();
        ReflectionTestUtils.setField(aspect, "loginLogService", loginLogService);
        ReflectionTestUtils.setField(aspect, "userMapper", userMapper);
        ReflectionTestUtils.setField(aspect, "threadPoolExecutor", executor);

        loginRequest = new AuthUserByPasswordRequestDTO();
        ReflectionTestUtils.setField(loginRequest, "phoneNumber", "13175144211");
        when(joinPoint.getArgs()).thenReturn(new Object[]{loginRequest});
        when(loginLog.recordIp()).thenReturn(true);
        when(loginLog.recordTime()).thenReturn(true);
        when(loginLogService.save(any(TLoginLog.class))).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).submit(any(Runnable.class));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("User-Agent", "test-agent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void nonThrowingBusinessFailureIsRecordedAsFailureAndPhoneIsMasked() throws Throwable {
        when(joinPoint.proceed()).thenReturn(Response.builder()
                .code(ResponseCode.E0003.getCode())
                .info(ResponseCode.E0003.getInfo())
                .build());

        aspect.around(joinPoint, loginLog);

        var captor = org.mockito.ArgumentCaptor.forClass(TLoginLog.class);
        verify(loginLogService).save(captor.capture());
        assertEquals("登录失败", captor.getValue().getLoginStatus());
        assertEquals("131****4211", captor.getValue().getLoginUser());
    }

    @Test
    void successfulBusinessResponseRemainsAValidAuditEvent() throws Throwable {
        when(joinPoint.proceed()).thenReturn(Response.builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .build());

        aspect.around(joinPoint, loginLog);

        var captor = org.mockito.ArgumentCaptor.forClass(TLoginLog.class);
        verify(loginLogService).save(captor.capture());
        assertEquals("登录成功", captor.getValue().getLoginStatus());
        assertEquals("203.0.113.9", captor.getValue().getLoginIp());
    }

}
