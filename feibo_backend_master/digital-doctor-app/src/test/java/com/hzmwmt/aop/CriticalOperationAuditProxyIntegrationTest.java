package com.hzmwmt.aop;

import com.hzmwmt.config.MybatisPlusConfig;
import com.hzmwmt.domain.auth.service.IAuthService;
import com.hzmwmt.domain.entity.log.TOperationLogs;
import com.hzmwmt.domain.service.log.OperationLogsService;
import com.hzmwmt.types.annotations.OperationLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        MybatisPlusConfig.class,
        OperationLogAspect.class,
        CriticalOperationAuditProxyIntegrationTest.TestConfig.class
})
class CriticalOperationAuditProxyIntegrationTest {

    @Autowired
    private PrivilegedMutation privilegedMutation;
    @Autowired
    private OperationLogsService operationLogsService;
    @Autowired
    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void resetFixture() {
        reset(operationLogsService);
        privilegedMutation.reset();
        transactionManager.reset();
    }

    @Test
    void productionAdvisorOrderStartsTransactionBeforeCriticalAuditAdvice() {
        AtomicBoolean auditObservedTransaction = new AtomicBoolean();
        when(operationLogsService.save(any(TOperationLogs.class))).thenAnswer(invocation -> {
            auditObservedTransaction.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });

        assertThat(AopUtils.isAopProxy(privilegedMutation)).isTrue();
        assertThat(privilegedMutation.apply()).isEqualTo("ok");

        assertThat(privilegedMutation.businessObservedTransaction()).isTrue();
        assertThat(auditObservedTransaction).isTrue();
        assertThat(transactionManager.commits.get()).isEqualTo(1);
        assertThat(transactionManager.rollbacks.get()).isZero();
        verify(operationLogsService).save(any(TOperationLogs.class));
    }

    @Test
    void failedCriticalAuditRollsBackTheRealProxiedTransaction() {
        when(operationLogsService.save(any(TOperationLogs.class))).thenReturn(false);

        assertThatThrownBy(privilegedMutation::apply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Critical operation audit");

        assertThat(privilegedMutation.businessObservedTransaction()).isTrue();
        assertThat(transactionManager.commits.get()).isZero();
        assertThat(transactionManager.rollbacks.get()).isEqualTo(1);
        verify(operationLogsService).save(any(TOperationLogs.class));
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        PrivilegedMutation privilegedMutation() {
            return new PrivilegedMutation();
        }

        @Bean
        OperationLogsService operationLogsService() {
            return mock(OperationLogsService.class);
        }

        @Bean
        IAuthService authService() {
            return mock(IAuthService.class);
        }

        @Bean
        ThreadPoolExecutor threadPoolExecutor() {
            return mock(ThreadPoolExecutor.class);
        }

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }
    }

    static class PrivilegedMutation {
        private final AtomicBoolean businessObservedTransaction = new AtomicBoolean();

        @Transactional
        @OperationLog(
                operationType = "修改",
                description = "真实代理事务顺序测试",
                recordParams = false,
                recordResult = false,
                critical = true)
        public String apply() {
            businessObservedTransaction.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
            return "ok";
        }

        boolean businessObservedTransaction() {
            return businessObservedTransaction.get();
        }

        void reset() {
            businessObservedTransaction.set(false);
        }
    }

    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger rollbacks = new AtomicInteger();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // AbstractPlatformTransactionManager publishes the active state to
            // TransactionSynchronizationManager after this callback returns.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits.incrementAndGet();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks.incrementAndGet();
        }

        void reset() {
            commits.set(0);
            rollbacks.set(0);
        }
    }
}
