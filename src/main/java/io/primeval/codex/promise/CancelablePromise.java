package io.primeval.codex.promise;

import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

public interface CancelablePromise<T> extends Promise<T> {

    boolean cancel(String reason, boolean tryToInterrupt);

    public static <T> CancelablePromise<T> resolved(T value) {
        Promise<T> resolved = Promises.resolved(value);
        return new DelegatingCancelablePromise<T>(resolved) {
            @Override
            public boolean cancel(String reason, boolean tryToInterrupt) {
                return false;
            }
        };
    }

    public static <T> CancelablePromise<T> failed(Throwable exception) {
        Promise<T> failed = Promises.failed(exception);
        return new DelegatingCancelablePromise<T>(failed) {
            @Override
            public boolean cancel(String reason, boolean tryToInterrupt) {
                return false;
            }
        };
    }

    public static <T> CancelablePromise<T> wrap(Promise<T> pms) {
        if (pms instanceof CancelablePromise) {
            return (CancelablePromise<T>) pms;
        }
        return new DelegatingCancelablePromise<T>(pms) {

            @Override
            public boolean cancel(String reason, boolean tryToInterrupt) {
                return false;
            }
        };
    }

}
