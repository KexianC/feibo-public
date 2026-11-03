package com.hzmwmt.modelnotification;

import com.hzmwmt.domain.ai.model.entity.ModelFailureEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelFailureNotificationListenerTest {
    private final ModelFailureEvent event = new ModelFailureEvent("request", 1L, 2L, 3L,
            "primary", "model", "HTTP_503", "deepseek", "deepseek-chat", "FALLBACK_SUCCEEDED");

    @Test void eventPersistenceRunsOnTheConfiguredWorker() {
        var pending = new ArrayList<Runnable>();
        var store = mock(ModelFailureNoticeStore.class);
        var listener = new ModelFailureNotificationListener(store, pending::add);
        listener.onModelFailure(event);
        verifyNoInteractions(store);
        assertThat(pending).hasSize(1);
        pending.get(0).run();
        verify(store).save(event);
    }

    @Test void databaseFailureCannotEscapeIntoChatCompletion() {
        var store = mock(ModelFailureNoticeStore.class);
        doThrow(new IllegalStateException("sensitive database detail")).when(store).save(event);
        var listener = new ModelFailureNotificationListener(store, Runnable::run);
        assertThatCode(() -> listener.onModelFailure(event)).doesNotThrowAnyException();
    }

    @Test void workerRejectionCannotEscapeIntoChatCompletion() {
        var store = mock(ModelFailureNoticeStore.class);
        TaskExecutor rejected = runnable -> { throw new RejectedExecutionException("shutting down"); };
        var listener = new ModelFailureNotificationListener(store, rejected);
        assertThatCode(() -> listener.onModelFailure(event)).doesNotThrowAnyException();
        verifyNoInteractions(store);
    }
}
