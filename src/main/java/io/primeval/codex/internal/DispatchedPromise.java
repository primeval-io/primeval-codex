package io.primeval.codex.internal;


import org.osgi.util.promise.Promise;

import io.primeval.codex.promise.DelegatingCancelablePromise;

public final class DispatchedPromise<T> extends DelegatingCancelablePromise<T> {

    private final CancelationSupport cancelationSupport;

    public DispatchedPromise(Promise<T> promise, CancelationSupport cancelationSupport) {
        super(promise);
        this.cancelationSupport = cancelationSupport;
    }

    @Override
    public boolean cancel(String reason, boolean tryToInterrupt) {
        return cancelationSupport.cancel(reason, tryToInterrupt);
    }
}