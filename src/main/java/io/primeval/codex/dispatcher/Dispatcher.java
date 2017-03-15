package io.primeval.codex.dispatcher;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import io.primeval.codex.promise.CancelablePromise;
import io.primeval.codex.util.Procedure;

public interface Dispatcher extends Executor {

    default void execute(Runnable task) {
        execute(task, true);
    }

    void execute(Runnable task, boolean inheritExecutionContext);

    <T> CancelablePromise<T> dispatch(Callable<T> task, boolean inheritExecutionContext);

    default <T> CancelablePromise<T> dispatch(Callable<T> task) {
        return dispatch(task, true);
    }

    default CancelablePromise<Void> dispatch(Procedure r, boolean inheritExecutionContext) {
        return dispatch(() -> {
            r.call();
            return null;
        } , inheritExecutionContext);
    }

    default CancelablePromise<Void> dispatch(Procedure r) {
        return dispatch(r, true);
    }

    <T> List<CancelablePromise<T>> dispatchAll(List<? extends Callable<T>> tasks, boolean inheritExecutionContext);

    default <T> List<CancelablePromise<T>> dispatchAll(List<? extends Callable<T>> tasks) {
        return dispatchAll(tasks, true);
    }

}
