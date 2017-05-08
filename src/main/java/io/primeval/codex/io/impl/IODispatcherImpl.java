package io.primeval.codex.io.impl;

import java.util.List;
import java.util.concurrent.Callable;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import io.primeval.codex.dispatcher.DetachedDispatcher;
import io.primeval.codex.dispatcher.DetachedDispatcherManager;
import io.primeval.codex.io.IODispatcher;
import io.primeval.codex.promise.CancelablePromise;

@Component
public final class IODispatcherImpl implements IODispatcher {

    private DetachedDispatcherManager detachedDispatcherManager;
    private DetachedDispatcher dispatcher;

    @Activate
    public void activate() {
        dispatcher = detachedDispatcherManager.create("Blocking IO", 2, 10);
    }
    
    @Deactivate
    public void deactivate() {
        dispatcher.close();
    }

    @Override
    public void execute(Runnable command) {
        dispatcher.execute(command);
    }

    @Override
    public void execute(Runnable task, boolean inheritExecutionContext) {
        dispatcher.execute(task, inheritExecutionContext);
    }

    @Override
    public <T> CancelablePromise<T> dispatch(Callable<T> task, boolean inheritExecutionContext) {
        return dispatcher.dispatch(task, inheritExecutionContext);
    }

    @Override
    public <T> List<CancelablePromise<T>> dispatchAll(List<? extends Callable<T>> tasks, boolean inheritExecutionContext) {
        return dispatcher.dispatchAll(tasks, inheritExecutionContext);
    }

    @Reference
    public void setDetachedDispatcherManager(DetachedDispatcherManager detachedDispatcherManager) {
        this.detachedDispatcherManager = detachedDispatcherManager;
    }

}
