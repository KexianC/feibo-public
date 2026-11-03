package com.hzmwmt.aop;

import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.entity.log.TOperationLogs;
import com.hzmwmt.domain.service.log.OperationLogsService;
import com.hzmwmt.types.annotations.OperationLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriticalOperationAuditTest {

    @Mock
    private OperationLogsService operationLogsService;
    @Mock
    private IAuthService authService;
    @Mock
    private ThreadPoolExecutor executor;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private OperationLogAspect aspect;
    private Object[] auditArguments;

    @BeforeEach
    void setUp() throws Exception {
        aspect = new OperationLogAspect();
        ReflectionTestUtils.setField(aspect, "operationLogsService", operationLogsService);
        ReflectionTestUtils.setField(aspect, "authService", authService);
        ReflectionTestUtils.setField(aspect, "threadPoolExecutor", executor);

        Method method = PrivilegedMutation.class.getDeclaredMethod(
                "apply", Long.class, String.class);
        auditArguments = new Object[]{9005300000000000001L, "sk-never-audit-this"};
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new PrivilegedMutation());
        lenient().when(joinPoint.getArgs()).thenAnswer(ignored -> auditArguments);
        when(signature.getMethod()).thenReturn(method);

        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/admin/test");
        request.setRemoteAddr("203.0.113.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void criticalMutationAndAuditSucceedInTheSameTransaction() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(joinPoint.proceed()).thenReturn("ok");
        when(operationLogsService.save(any(TOperationLogs.class))).thenReturn(true);

        assertEquals("ok", aspect.around(joinPoint));

        ArgumentCaptor<TOperationLogs> savedLog =
                ArgumentCaptor.forClass(TOperationLogs.class);
        verify(operationLogsService).save(savedLog.capture());
        assertThat(savedLog.getValue().getOperationDesc())
                .contains("targetId=9005300000000000001")
                .doesNotContain("sk-never-audit-this");
        verify(executor, never()).execute(any(Runnable.class));
    }

    @Test
    void criticalMutationFailsClosedWhenAuditCannotBePersisted() throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(joinPoint.proceed()).thenReturn("changed");
        when(operationLogsService.save(any(TOperationLogs.class))).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> aspect.around(joinPoint));
    }

    @Test
    void criticalMutationCannotRunOutsideBusinessTransaction() throws Throwable {
        assertThrows(IllegalStateException.class, () -> aspect.around(joinPoint));
        verify(joinPoint, never()).proceed();
    }

    @Test
    void invalidTargetIdFailsBeforeBusinessMutationOrAuditPersistence()
            throws Throwable {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        auditArguments = new Object[]{0L, "sk-never-audit-this"};

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("positive integer scalar");

        verify(joinPoint, never()).proceed();
        verify(operationLogsService, never()).save(any(TOperationLogs.class));
    }

    private static final class PrivilegedMutation {
        @OperationLog(
                operationType = "修改",
                description = "测试关键权限写入",
                recordParams = false,
                recordResult = false,
                targetIdParameterIndex = 0,
                critical = true)
        public String apply(Long targetId, String apiKey) {
            return "ok";
        }
    }
}
