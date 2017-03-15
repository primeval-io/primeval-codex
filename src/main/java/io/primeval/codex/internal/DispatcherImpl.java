package io.primeval.codex.internal;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.primeval.codex.context.ExecutionContextManager;
import io.primeval.codex.context.ExecutionContextSwitch;
import io.primeval.codex.dispatcher.Dispatcher;
import io.primeval.codex.promise.CancelableDeferred;
import io.primeval.codex.promise.CancelablePromise;
import io.primeval.codex.scheduler.Scheduler;


@Component
public final class DispatcherImpl implements Dispatcher, Scheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DispatcherImpl.class);
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;

    private ExecutionContextManager executionContextManager;

    @Activate
    public void activate() {
        int parallelism = Runtime.getRuntime().availableProcessors() * 2;
        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                LOGGER.error("Uncaught error in thread {}", t, e);
            }
        };
        executor = new ForkJoinPool(parallelism,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory, uncaughtExceptionHandler, false);
        scheduler = Executors.newScheduledThreadPool(1,
                new ThreadFactoryBuilder().setNameFormat("scheduler-pool-%d").setUncaughtExceptionHandler(uncaughtExceptionHandler)
                        .build());

    }

    @Deactivate
    public void deactivate() {
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    @Reference
    public void setExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
    }

    @Override
    public void execute(Runnable task, boolean inheritExecutionContext) {
        Dispatchers.execute(executor, executionContextManager, task, inheritExecutionContext);
    }

    @Override
    public <T> CancelablePromise<T> dispatch(Callable<T> task, boolean inheritExecutionContext) {
        return Dispatchers.dispatch(executor, executionContextManager, task, inheritExecutionContext);
    }

    @Override
    public <T> List<CancelablePromise<T>> dispatchAll(List<? extends Callable<T>> tasks, boolean inheritExecutionContext) {
        return Dispatchers.dispatchAll(task -> dispatch(task, inheritExecutionContext), tasks, inheritExecutionContext);
    }

    @Override
    public <T> CancelablePromise<T> schedule(Callable<T> task, long delay, TimeUnit unit, boolean inheritExecutionContext) {
        CancelableDeferred<T> deferred = new CancelableDeferred<>();
        CancelationSupport cancelationSupport = new CancelationSupport(deferred);

        ExecutionContextSwitch executionContextSwitch = inheritExecutionContext ? executionContextManager.onDispatch()
                : ExecutionContextManagerImpl.NOOP_EXECUTION_CONTEXT_SWITCH;
        
        // TODO make cancellation cancel before run as well (using the scheduled future?)
        scheduler.schedule(() -> {
            // If canceled before start, we never run.
            if (deferred.canceled) {
                return;
            }
            // Start to get the interruption thingy.
            cancelationSupport.start();
            executionContextSwitch.apply();
            try {
                T call = task.call();
                // We have returned but it was already canceled.
                deferred.resolve(call);
            } catch (Exception e) {
                deferred.fail(e);
            } finally {
                cancelationSupport.stop();
                executionContextSwitch.unapply();
            }
        }, delay, unit);
        
        return new DispatchedPromise<T>(deferred.getPromise(), cancelationSupport);
    }

    @Override
    public void scheduleAndForget(Runnable task, long delay, TimeUnit unit, boolean inheritExecutionContext) {

        ExecutionContextSwitch executionContextSwitch = inheritExecutionContext ? executionContextManager.onDispatch()
                : ExecutionContextManagerImpl.NOOP_EXECUTION_CONTEXT_SWITCH;
        
        scheduler.schedule(() -> {
            executionContextSwitch.apply();
            try {
                task.run();
            } finally {
                executionContextSwitch.unapply();
            }
        }, delay, unit);
    }

}
