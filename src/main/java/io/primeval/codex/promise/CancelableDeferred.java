package io.primeval.codex.promise;

import java.util.concurrent.CancellationException;

import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

public final class CancelableDeferred<T> {

    public volatile boolean canceled = false;
    public final Deferred<T> deferred = new Deferred<>();

    public synchronized boolean cancel(String reason) {
        if (getPromise().isDone()) {
            return false;
        }
        try {
            deferred.fail(new CancellationException(reason));
            canceled = true;
            return true;
        } catch (IllegalStateException ise) {
            // already resolved
            return false;
        }

    }

    public Promise<T> getPromise() {
        return deferred.getPromise();
    }

    public synchronized void resolve(T value) {
        if (!canceled) {
            deferred.resolve(value);
        }
    }

    public synchronized void fail(Throwable failure) {
        if (!canceled) {
            deferred.fail(failure);
        }
    }

    public synchronized Promise<Void> resolveWith(Promise<? extends T> with) {
        if (!canceled) {
            return deferred.resolveWith(with);
        } else {
            return Promises.failed(new CancellationException());
        }
    }
    
    public static <T> CancelableDeferred<T> fromPromise(Promise<T> pms) {
        CancelableDeferred<T> cancelableDeferred = new CancelableDeferred<>();
        cancelableDeferred.resolveWith(pms);
        return cancelableDeferred;
    }

}
