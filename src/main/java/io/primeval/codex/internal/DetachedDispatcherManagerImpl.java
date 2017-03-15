package io.primeval.codex.internal;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.primeval.codex.context.ExecutionContextManager;
import io.primeval.codex.dispatcher.DetachedDispatcher;
import io.primeval.codex.dispatcher.DetachedDispatcherManager;
import io.primeval.codex.dispatcher.Dispatcher;

@Component
public final class DetachedDispatcherManagerImpl implements DetachedDispatcherManager {

    private ExecutionContextManager executionContextManager;
    private Dispatcher dispatcher;

    @Override
    public DetachedDispatcher create(String name, int minThreads, int maxThreads) {
        return new DetachedDispatcherImpl(dispatcher, executionContextManager, name, minThreads, maxThreads);
    }

    @Reference
    public void setExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
    }

    @Reference
    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

}
