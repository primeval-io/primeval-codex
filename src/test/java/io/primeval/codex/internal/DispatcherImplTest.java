package io.primeval.codex.internal;

import static com.jayway.awaitility.Awaitility.await;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.util.promise.Promise;

import io.primeval.codex.internal.DispatcherImpl;
import io.primeval.codex.internal.ExecutionContextManagerImpl;
import io.primeval.codex.internal.Executions;
import io.primeval.codex.internal.TypeTagExecutionContextImpl;
import io.primeval.codex.promise.CancelablePromise;

public final class DispatcherImplTest {

    private static DispatcherImpl dispatcher;
    private static TypeTagExecutionContextImpl executionContext;

    @BeforeClass
    public static void classSetUp() throws Exception {
        executionContext = new TypeTagExecutionContextImpl();
        dispatcher = new DispatcherImpl();
        ExecutionContextManagerImpl executionContextManager = new ExecutionContextManagerImpl();
        executionContextManager.addExecutionContextHook(executionContext);
        dispatcher.setExecutionContextManager(executionContextManager);
        dispatcher.activate();
    }

    @AfterClass
    public static void classTearDown() throws InterruptedException {
        Thread.sleep(1500L);
        dispatcher.deactivate();
    }

    @Test
    public void shouldDispatchSimpleTask() throws Exception {
        CancelablePromise<String> promise = dispatcher.dispatch(() -> "Foo");
        assertThat(promise.getValue()).isEqualTo("Foo");
    }

    @Test
    public void shouldDispatchAndInheritContext() throws Exception {
        executionContext.setCurrent(String.class, "foo");
        CancelablePromise<Boolean> promise = dispatcher
                .dispatch(() -> executionContext.getCurrent(String.class) == "foo");
        assertThat(promise.getValue()).isTrue();
    }

    @Test
    public void shouldNotCancelCompletedTask() throws Exception {
        AtomicBoolean completed = new AtomicBoolean();
        CancelablePromise<String> promise = dispatcher.dispatch(() -> {
            try {
                Thread.sleep(1000L);
                return "Foo";
            } finally {
                completed.set(true);
            }
        });
        await().atMost(3, TimeUnit.SECONDS).untilTrue(completed);
        boolean cancel = promise.cancel("Test cancel", false);
        assertThat(cancel).isFalse();
        assertThat(promise.getValue()).isEqualTo("Foo");
    }

    @Test
    public void shouldCancelUncompletedLongTaskEvenThoughItWasAccepted() throws Exception {
        AtomicBoolean accepted = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean interrupted = new AtomicBoolean();
        CancelablePromise<String> promise = dispatcher.dispatch(() -> {
            accepted.set(true);
            try {
                Thread.sleep(1000L);
            } finally {
                interrupted.set(Thread.currentThread().isInterrupted());
            }
            try {
                return "Foo";
            } finally {
                completed.set(true);
            }
        });
        await().atMost(2, TimeUnit.SECONDS).untilTrue(accepted);
        boolean cancel = promise.cancel("Test cancel", false);
        assertThat(cancel).isTrue();
        assertThat(promise.getFailure()).isInstanceOf(CancellationException.class);
        runQuietly(() -> await().atMost(2, TimeUnit.SECONDS).untilTrue(completed));
        assertThat(interrupted.get()).isFalse();
        assertThat(completed.get()).isTrue();
    }

    @Test
    public void shouldCancelAndInterruptLongTask() throws Exception {
        AtomicBoolean accepted = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean interrupted = new AtomicBoolean();
        CancelablePromise<String> promise = dispatcher.dispatch(() -> {
            accepted.set(true);
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ie) {
                interrupted.set(true);
                throw ie;
            }
            try {
                return "Foo";
            } finally {
                completed.set(true);
            }
        });
       await().atMost(2, TimeUnit.SECONDS).untilTrue(accepted);
        boolean cancel = promise.cancel("Test cancel", true);
        assertThat(cancel).isTrue();
        assertThat(promise.getFailure()).isInstanceOf(CancellationException.class);
        runQuietly(() -> await().atMost(2, TimeUnit.SECONDS).untilTrue(completed));
        assertThat(interrupted.get()).isTrue();
        assertThat(completed.get()).isFalse();
    }

    @Test
    public void shouldDispatchInParallel() throws Exception {
        // This test expects we have 2 cores ...
        if (Runtime.getRuntime().availableProcessors() < 2) {
            return;
        }

        Callable<String> c = () -> {
            Thread.sleep(1000L);
            return "foo";
        };

        long start = java.lang.System.nanoTime();
        List<CancelablePromise<String>> all = dispatcher.dispatchAll(Stream.of(c, c, c).collect(Collectors.toList()));
        Promise<List<String>> promise = Executions.all(all);

        List<String> actual = promise.getValue();
        assertThat(actual).containsExactly("foo", "foo", "foo");
        
        long end = java.lang.System.nanoTime();

        assertThat((end - start) < (2 * 1_000_000_000)).isTrue();  // 2 seconds max
    }

    private static void runQuietly(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            // swallow
        }
    }

}
