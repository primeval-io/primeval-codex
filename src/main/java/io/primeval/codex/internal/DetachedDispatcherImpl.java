package io.primeval.codex.internal;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.osgi.util.promise.Deferred;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.primeval.codex.context.ExecutionContextManager;
import io.primeval.codex.dispatcher.DetachedDispatcher;
import io.primeval.codex.dispatcher.Dispatcher;
import io.primeval.codex.promise.CancelablePromise;
import io.primeval.codex.promise.DelegatingCancelablePromise;

public final class DetachedDispatcherImpl implements DetachedDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DetachedDispatcherImpl.class);
    private final ThreadPoolExecutor executor;
    private final ExecutionContextManager executionContextManager;
    private final Dispatcher dispatcher;

    public DetachedDispatcherImpl(Dispatcher dispatcher, ExecutionContextManager executionContextManager, String name, int minThreads,
            int maxThreads) {
        this.dispatcher = dispatcher;
        this.executionContextManager = executionContextManager;
        LinkedBlockingDeque<Runnable> queue = new LinkedBlockingDeque<>();
          
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(minThreads, maxThreads, 10, TimeUnit.SECONDS, queue,
                new ThreadFactoryBuilder().setNameFormat(name + "-pool-%d").setUncaughtExceptionHandler(new UncaughtExceptionHandler() {

                    @Override
                    public void uncaughtException(Thread t, Throwable e) {
                        LOGGER.error("Uncaught error in thread {}", t, e);
                    }
                }).build(), new RejectedExecutionHandler() {
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        // this will block if the queue is full, which is unlikely since it is virtually unbounded...!
                        try {
                            executor.getQueue().put(r);
                        } catch (InterruptedException e) {
                            LOGGER.debug("Thread interrupted while re-trying to fill full queue", e);
                            throw new RejectedExecutionException("Tried to push task, but was interrupted");

                        }
                    }
                });
        this.executor = threadPool;
    }

    @Override
    public void execute(Runnable task, boolean inheritExecutionContext) {
        Dispatchers.execute(executor, executionContextManager, task, inheritExecutionContext);
    }

    @Override
    public <T> CancelablePromise<T> dispatch(Callable<T> task, boolean inheritExecutionContext) {
        CancelablePromise<T> dispatchedPromise = Dispatchers.dispatch(executor, executionContextManager, task, inheritExecutionContext);

        // Bridge the promise resolution callback back to the non-blocking dispatcher!
        Deferred<T> deferred = new Deferred<>();
        dispatchedPromise.onResolve(() -> {
            dispatcher.execute(() -> {
                deferred.resolveWith(dispatchedPromise);
            } , inheritExecutionContext);
        });

        return new DelegatingCancelablePromise<T>(deferred.getPromise()) {
            @Override
            public boolean cancel(String reason, boolean tryToInterrupt) {
                return dispatchedPromise.cancel(reason, tryToInterrupt);
            }
        };
    }

    @Override
    public <T> List<CancelablePromise<T>> dispatchAll(List<? extends Callable<T>> tasks, boolean inheritExecutionContext) {
        return Dispatchers.dispatchAll(task -> dispatch(task, inheritExecutionContext), tasks,
                inheritExecutionContext);
    }

    @Override
    public void close() {
        executor.shutdown();
    }

}
