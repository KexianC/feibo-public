package com.hzmwmt.modelnotification;

import com.hzmwmt.domain.ai.model.entity.ModelFailureEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ModelFailureNotificationConfigurationTest {
    @Test
    void saturatedRealExecutorRejectsSafelyWithoutRunningNotificationJdbcOnChatThread() throws Exception {
        var executor = new ModelFailureNotificationConfiguration().modelFailureNotificationExecutor();
        var release = new CountDownLatch(1);
        var running = new CountDownLatch(2);
        Runnable block = () -> {
            running.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        };
        executor.initialize();
        try {
            executor.execute(block);
            for (int i = 0; i < 1000; i++) executor.execute(() -> {});
            executor.execute(block);
            assertThat(running.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.getThreadPoolExecutor().getQueue().size()).isEqualTo(1000);
            var store = mock(ModelFailureNoticeStore.class);
            var listener = new ModelFailureNotificationListener(store, executor);
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> listener.onModelFailure(new ModelFailureEvent(
                    "synthetic-overload", 1L, 2L, 3L, "primary", "model", "TimeoutException",
                    "deepseek", "fallback", "FALLBACK_SUCCEEDED")));
            verifyNoInteractions(store);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
