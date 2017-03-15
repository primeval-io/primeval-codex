package io.primeval.codex.scheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.osgi.util.promise.Promise;

import io.primeval.codex.promise.CancelableDeferred;
import io.primeval.codex.promise.CancelablePromise;

public interface Scheduler {

    default CancelablePromise<Void> schedule(Runnable task, long delay, TimeUnit unit, boolean inheritExecutionContext) {
        return schedule(() -> {
            task.run();
            return null;
        }, delay, unit, inheritExecutionContext);
    }

    default CancelablePromise<Void> schedule(Runnable task, long delay, TimeUnit unit) {
        return schedule(task, delay, unit, true);
    }

    <T> CancelablePromise<T> schedule(Callable<T> task, long delay, TimeUnit unit, boolean inheritExecutionContext);

    default <T> CancelablePromise<T> schedule(Callable<T> task, long delay, TimeUnit unit) {
        return schedule(task, delay, unit, true);
    }

    void scheduleAndForget(Runnable task, long delay, TimeUnit unit, boolean inheritExecutionContext);

    default void scheduleAndForget(Runnable task, long delay, TimeUnit unit) {
        scheduleAndForget(task, delay, unit, true);
    }

    default <T> Promise<T> timeLimit(Promise<T> promise, long delay, TimeUnit unit) {
        return timeLimit(promise, delay, unit, "Promise timeout");
    }

    default <T> CancelablePromise<T> timeLimit(CancelablePromise<T> promise, long delay, TimeUnit unit) {
        return timeLimit(promise, delay, unit, "Promise timeout");
    }

    default <T> Promise<T> timeLimit(Promise<T> promise, long delay, TimeUnit unit, String reason) {
        if (promise.isDone()) {
            return promise;
        }

        CancelableDeferred<T> def = new CancelableDeferred<>();
        def.resolveWith(promise);
        schedule(() -> def.cancel(reason + " (timeout: " + delay + " " + unit.name() + ")"), delay, unit);
        return def.getPromise();
    }

    default <T> CancelablePromise<T> timeLimit(CancelablePromise<T> promise, long delay, TimeUnit unit, String reason) {
        if (promise.isDone()) {
            return promise;
        }

        schedule(() -> promise.cancel(reason + " (timeout: " + delay + " " + unit.name() + ")", true), delay, unit);
        return promise;
    }

}
