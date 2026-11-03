package com.hzmwmt.modelnotification;

import com.hzmwmt.domain.ai.model.entity.ModelFailureEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class ModelFailureNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(ModelFailureNotificationListener.class);
    private final ModelFailureNoticeStore store;
    private final TaskExecutor executor;

    public ModelFailureNotificationListener(ModelFailureNoticeStore store,
            @Qualifier("modelFailureNotificationExecutor") TaskExecutor executor) {
        this.store = store;
        this.executor = executor;
    }

    @EventListener
    public void onModelFailure(ModelFailureEvent event) {
        try {
            executor.execute(() -> {
                try {
                    store.save(event);
                } catch (RuntimeException failure) {
                    log.error("Model failure notification persistence failed: type={}; verify database and model_failure_notifications_20260909.sql migration",
                            failure.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException failure) {
            // Notification infrastructure must never turn a successful fallback answer into an error.
            log.error("Model failure notification scheduling failed: type={}", failure.getClass().getSimpleName());
        }
    }
}
