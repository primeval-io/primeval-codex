package io.primeval.codex.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Function;

import io.primeval.codex.context.ExecutionContextManager;
import io.primeval.codex.context.ExecutionContextSwitch;
import io.primeval.codex.promise.CancelableDeferred;
import io.primeval.codex.promise.CancelablePromise;

public final class Dispatchers {

  

    static <T> void execute(Executor executor, ExecutionContextManager executionContextManager, Runnable task,
            boolean inheritExecutionContext) {

        ExecutionContextSwitch executionContextSwitch = inheritExecutionContext ? executionContextManager.onDispatch()
                : ExecutionContextManagerImpl.NOOP_EXECUTION_CONTEXT_SWITCH;

        executor.execute(() -> {
            executionContextSwitch.apply();
            try {
                task.run();
            } finally {
                executionContextSwitch.unapply();
            }
        });

    }

    static <T> CancelablePromise<T> dispatch(Executor executor, ExecutionContextManager executionContextManager, Callable<T> task,
            boolean inheritExecutionContext) {
        CancelableDeferred<T> deferred = new CancelableDeferred<>();
        CancelationSupport cancelationSupport = new CancelationSupport(deferred);

        ExecutionContextSwitch executionContextSwitch = inheritExecutionContext ? executionContextManager.onDispatch()
                : ExecutionContextManagerImpl.NOOP_EXECUTION_CONTEXT_SWITCH;

        executor.execute(() -> {
            // If canceled before start, we never run.
            if (deferred.canceled) {
                return;
            }
            // Start to get the interruption thingy.
            try {
                cancelationSupport.start();
                executionContextSwitch.apply();
                T call = task.call();
                deferred.resolve(call);
            } catch (Exception e) {
                deferred.fail(e);
            } finally {
                cancelationSupport.stop();
                    executionContextSwitch.unapply();
            }
        });
        return new DispatchedPromise<T>(deferred.getPromise(), cancelationSupport);
    }

    static <T> List<CancelablePromise<T>> dispatchAll(Function<Callable<T>, CancelablePromise<T>> dispatchingFunction,
            List<? extends Callable<T>> tasks, boolean inheritExecutionContext) {
        List<CancelablePromise<T>> b = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            CancelablePromise<T> promise = dispatchingFunction.apply(task);
            b.add(promise);
        }
        return b;
    }
}
