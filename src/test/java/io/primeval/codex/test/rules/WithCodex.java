package io.primeval.codex.test.rules;

import org.junit.rules.ExternalResource;

import io.primeval.codex.dispatcher.DetachedDispatcherManager;
import io.primeval.codex.dispatcher.Dispatcher;
import io.primeval.codex.internal.DetachedDispatcherManagerImpl;
import io.primeval.codex.internal.DispatcherImpl;
import io.primeval.codex.internal.ExecutionContextManagerImpl;
import io.primeval.codex.scheduler.Scheduler;
import io.primeval.common.test.rules.TestResource;

public final class WithCodex extends ExternalResource implements TestResource {

    private ExecutionContextManagerImpl executionContextManagerImpl;
    private DispatcherImpl dispatcherImpl;
    private DetachedDispatcherManagerImpl detachedDispatcherManagerImpl;

    @Override
    public void before() throws Throwable {
        super.before();

        executionContextManagerImpl = new ExecutionContextManagerImpl();
        dispatcherImpl = new DispatcherImpl();
        dispatcherImpl.setExecutionContextManager(executionContextManagerImpl);
        dispatcherImpl.activate();

        detachedDispatcherManagerImpl = new DetachedDispatcherManagerImpl();
        detachedDispatcherManagerImpl.setDispatcher(dispatcherImpl);
        detachedDispatcherManagerImpl.setExecutionContextManager(executionContextManagerImpl);

    }

    @Override
    public void after() {
        dispatcherImpl.deactivate();
        super.after();
    }

    public ExecutionContextManagerImpl getExecutionContext() {
        return executionContextManagerImpl;
    }

    public ExecutionContextManagerImpl getExecutionContextManager() {
        return executionContextManagerImpl;
    }

    public Dispatcher getDispatcher() {
        return dispatcherImpl;
    }

    public Scheduler getScheduler() {
        return dispatcherImpl;
    }

    public DetachedDispatcherManager getDetachedDispatcherManager() {
        return detachedDispatcherManagerImpl;
    }

}
