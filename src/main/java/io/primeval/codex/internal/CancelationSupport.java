package io.primeval.codex.internal;

import io.primeval.codex.promise.CancelableDeferred;

public final class CancelationSupport {

    public volatile Thread currentThread;
    private final CancelableDeferred<?> deferred;

    public CancelationSupport(CancelableDeferred<?> deferred) {
        this.deferred = deferred;
    }

    public void start() {
        currentThread = Thread.currentThread();
    }
    
    public void stop() {
        currentThread = null;
    }

    // returns false if it had completed before
    public boolean cancel(String reason, boolean tryToInterrupt) {
        boolean canceled = deferred.cancel(reason);
        if (canceled && tryToInterrupt && currentThread != null) {
            currentThread.interrupt();
        }
        return canceled;
    }

}
