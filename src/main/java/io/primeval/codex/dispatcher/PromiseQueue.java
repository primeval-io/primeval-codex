package io.primeval.codex.dispatcher;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
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

    /** The queued jobs. */
    volatile int queuedJobs;

    /** The concurrency. */
    private final int concurrency;

    /** The jobs. */
    private final Queue<QueueJob<T>> jobs;

    /** The max queue size. */
    private final int maxQueueSize;

    /**
     * Instantiates a new promise queue.
     * 
     * The dispatcher function will be used to dispatch jobs. Queued jobs will be triggered when running jobs are resolved, by default in their thread. It is
     * thus advised to make sure the dispatch takes place in a new thread, for instance by using a handle to {@link Dispatcher#dispatch(Callable)} as dispatcher
     * function.
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
        int q = queuedJobs;
        if (maxQueueSize > 0 && q >= maxQueueSize) {
            return Promises.failed(new RejectedExecutionException("max job count reached"));
        }
        queuedJobs = q + 1;
        jobs.offer(new QueueJob<>(fun, def));
        dispatchNext(false);
        return def.getPromise();
    }

    /**
     * Dispatch next.
     *
     * @param jobCount
     *            the job count
     */
    private synchronized void dispatchNext(boolean jobDone) {
        int r = runningJobs;
        if (jobDone) {
            runningJobs = --r;
        }
        if (r < concurrency) {
            QueueJob<T> job = jobs.poll();
            if (job == null) {
                return;
            }
            queuedJobs--;
            runningJobs = r + 1;
            Promise<T> promise = dispatcher.apply(job.function);
            PromiseHelper.onResolve(promise, () -> {
                dispatchNext(true);
            }, job.deferred::resolve, job.deferred::fail);
        }
    }

    public int currentJobCount() {
        return runningJobs;
    }

    public int queuedJobCount() {
        return queuedJobs;
    }

}
