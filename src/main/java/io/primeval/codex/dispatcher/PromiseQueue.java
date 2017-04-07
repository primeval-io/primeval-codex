package io.primeval.codex.dispatcher;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;

import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

import io.primeval.codex.promise.PromiseHelper;

/**
 * The Class PromiseQueue.
 *
 * @param <T>
 *            the generic type
 */
public final class PromiseQueue<T> {

    /**
     * The Class QueueJob.
     *
     * @param <T>
     *            the generic type
     */
    private static final class QueueJob<T> {

        /** The function. */
        public final Callable<T> function;

        /** The deferred. */
        public final Deferred<T> deferred;

        /**
         * Instantiates a new queue job.
         *
         * @param function
         *            the function
         * @param deferred
         *            the deferred
         */
        public QueueJob(Callable<T> function, Deferred<T> deferred) {
            super();
            this.function = function;
            this.deferred = deferred;
        }

    }

    /** The dispatcher. */
    private final Function<Callable<T>, Promise<T>> dispatcher;

    /** The running jobs. */
    volatile int runningJobs;
    @SuppressWarnings("rawtypes")
    static final AtomicIntegerFieldUpdater<PromiseQueue> RUNNING_JOBS =
            AtomicIntegerFieldUpdater.newUpdater(PromiseQueue.class, "runningJobs");

    /** The concurrency. */
    private final int concurrency;

    /** The jobs. */
    private final Queue<QueueJob<T>> jobs;

    /** The max queue size. */
    private final int maxQueueSize;

    /**
     * Instantiates a new promise queue.
     *
     * @param dispatcher
     *            the dispatcher
     * @param concurrency
     *            the concurrency
     * @param maxQueueSize
     *            the max queue size, 0 for unbounded size.
     */
    public PromiseQueue(Function<Callable<T>, Promise<T>> dispatcher, int concurrency, int maxQueueSize) {
        this.dispatcher = dispatcher;
        this.concurrency = concurrency;
        this.maxQueueSize = maxQueueSize;
        this.jobs = new ConcurrentLinkedQueue<>();
    }

    /**
     * Dispatch.
     *
     * @param fun
     *            the fun
     * @return the promise
     */
    public synchronized Promise<T> dispatch(Callable<T> fun) {
        Deferred<T> def = new Deferred<>();
        if (maxQueueSize > 0 && runningJobs > maxQueueSize) {
            return Promises.failed(new RejectedExecutionException("max job count reached"));
        }
        jobs.offer(new QueueJob<>(fun, def));
        dispatchNext();
        return def.getPromise();
    }

    /**
     * Dispatch next.
     *
     * @param jobCount
     *            the job count
     */
    private synchronized void dispatchNext() {
        if (runningJobs < concurrency) {
            QueueJob<T> job = jobs.poll();
            if (job == null) {
                return;
            }
            RUNNING_JOBS.incrementAndGet(this);
            Promise<T> promise = dispatcher.apply(job.function);
            PromiseHelper.onResolve(promise, () -> {
                RUNNING_JOBS.decrementAndGet(this);
                dispatchNext();
            }, job.deferred::resolve, job.deferred::fail);
        }
    }

    public int currentJobCount() {
        return runningJobs;
    }

}
