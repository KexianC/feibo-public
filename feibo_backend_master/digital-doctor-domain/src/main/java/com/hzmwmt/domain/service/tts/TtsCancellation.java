package com.hzmwmt.domain.service.tts;

import com.hzmwmt.types.enums.ResponseCode;
import com.hzmwmt.types.exception.AppException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;

/** Request-local cancellation. Contains neither audio nor medical text. */
public final class TtsCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CopyOnWriteArrayList<Runnable> callbacks = new CopyOnWriteArrayList<>();

    public boolean isCancelled() { return cancelled.get(); }

    public void check() {
        if (isCancelled()) throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "本次语音播报已取消");
    }

    public AutoCloseable onCancel(Runnable callback) {
        AtomicBoolean invoked = new AtomicBoolean();
        Runnable once = () -> {
            synchronized (invoked) { if (invoked.compareAndSet(false, true)) callback.run(); }
        };
        callbacks.add(once);
        if (isCancelled()) once.run();
        return () -> { synchronized (invoked) { invoked.set(true); callbacks.remove(once); } };
    }

    public void cancel() {
        prepareCancel().run();
    }

    /** Atomically closes the gate; run returned potentially blocking cleanup outside owner locks. */
    public Runnable prepareCancel() {
        if (!cancelled.compareAndSet(false, true)) return () -> {};
        ArrayList<Runnable> pending = new ArrayList<>(callbacks);
        callbacks.clear();
        return () -> {
            for (Runnable callback : pending) {
                try { callback.run(); } catch (Exception ignored) { /* Run every cleanup. */ }
            }
        };
    }
}
