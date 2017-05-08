package io.primeval.codex.test.rules;

import org.junit.rules.ExternalResource;

import io.primeval.codex.io.impl.IODispatcherImpl;
import io.primeval.codex.io.impl.ReactiveFileReaderImpl;
import io.primeval.codex.io.impl.ResourceFinderReaderImpl;
import io.primeval.common.test.rules.TestResource;

public final class WithCodexIO extends ExternalResource implements TestResource {

    private WithCodex withCodex;
    private IODispatcherImpl ioDispatcher;
    private ReactiveFileReaderImpl reactiveFileReaderImpl;
    private ResourceFinderReaderImpl resourceFinderReaderImpl;

    public WithCodexIO(WithCodex withCodex) {
        this.withCodex = withCodex;
    }

    @Override
    public void before() throws Throwable {
        super.before();

        ioDispatcher = new IODispatcherImpl();
        ioDispatcher.setDetachedDispatcherManager(withCodex.getDetachedDispatcherManager());
        ioDispatcher.activate();

        reactiveFileReaderImpl = new ReactiveFileReaderImpl();
        reactiveFileReaderImpl.setIODispatcher(ioDispatcher);

        resourceFinderReaderImpl = new ResourceFinderReaderImpl();
        resourceFinderReaderImpl.setIODispatcher(ioDispatcher);
        resourceFinderReaderImpl.setReactiveFileReader(reactiveFileReaderImpl);
    }

    @Override
    public void after() {
        ioDispatcher.deactivate();
        super.after();
    }

    public IODispatcherImpl getIoDispatcher() {
        return ioDispatcher;
    }

    public ReactiveFileReaderImpl getReactiveFileReader() {
        return reactiveFileReaderImpl;
    }

    public ResourceFinderReaderImpl getResourceFinder() {
        return resourceFinderReaderImpl;
    }

    public ResourceFinderReaderImpl getReactiveResourceReader() {
        return resourceFinderReaderImpl;
    }
}
