package io.primeval.codex.promise;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.FailedPromisesException;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

public final class PromiseHelper {

    private PromiseHelper() {
    }

    public static final Promise<Void> VOID = Promises.resolved(null);
    @SuppressWarnings("rawtypes")
    private static final Promise EMPTY_LIST = Promises.resolved(Collections.emptyList());

    public static <T> void onResolve(Promise<T> promise, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        onResolve(promise, () -> {
        }, onSuccess, onError);
    }

    public static <T> void onResolve(Promise<T> promise, Runnable runOnResolve, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        promise.onResolve(() -> {
            runOnResolve.run();
            try {
                T val = promise.getValue();
                onSuccess.accept(val);
            } catch (InvocationTargetException ite) {
                onError.accept(ite.getCause());
            } catch (InterruptedException ie) {
                // Cannot happen by contract
                throw new AssertionError();
            }
        });
    }

    public static <T> Promise<T> wrap(Callable<T> callable) {
        return wrap(Function.identity(), callable);
    }

    public static <T> Promise<T> wrap(Function<Throwable, Throwable> wrapException, Callable<T> callable) {
        try {
            return Promises.resolved(callable.call());
        } catch (Throwable e) {
            return Promises.failed(wrapException.apply(e));
        }
    }

    public static <T> Promise<T> wrapPromise(Callable<Promise<T>> callable) {
        return wrapPromise(Function.identity(), callable);
    }

    public static <T> Promise<T> wrapPromise(Function<Throwable, Throwable> wrapException, Callable<Promise<T>> callable) {
        try {
            return callable.call();
        } catch (Throwable e) {
            return Promises.failed(wrapException.apply(e));
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Promise<List<T>> emptyList() {
        return (Promise<List<T>>) EMPTY_LIST;
    }

    public static <T> void onResolve(Promise<T> promise, Consumer<T> onSuccess) {
        onResolve(promise, onSuccess, t -> {
        });
    }

    public static <T> void onFailure(Promise<T> promise, Consumer<Throwable> onFailure) {
        onResolve(promise, t -> {
        }, onFailure);
    }

    public static <T> Promise<List<T>> allSuccessful(List<Promise<T>> promises) {
        if (promises.isEmpty()) {
            List<T> result = new ArrayList<T>();
            return Promises.resolved(result);
        }
        /* make a copy and capture the ordering */
        List<Promise<? extends T>> list = new ArrayList<Promise<? extends T>>(promises);
        Deferred<List<T>> chained = new Deferred<List<T>>();
        AllSuccessful<T> all = new AllSuccessful<T>(chained, list);
        for (Promise<? extends T> promise : list) {
            promise.onResolve(all);
        }
        return chained.getPromise();
    }

    public static <T> Promise<T> anySuccessful(List<Promise<T>> promises) {
        if (promises.isEmpty()) {
            return Promises.failed(new NoSuchElementException());
        }
        /* make a copy and capture the ordering */
        List<Promise<? extends T>> list = new ArrayList<Promise<? extends T>>(promises);
        Deferred<T> chained = new Deferred<>();
        AnySuccessful<T> all = new AnySuccessful<T>(chained, list);
        for (Promise<? extends T> promise : list) {
            promise.onResolve(all);
        }
        return chained.getPromise();
    }

    @SuppressWarnings("unchecked")
    public static <T, U extends Throwable> T recoverFrom(Promise<?> resolvedPromise, Class<U> throwableClass, Function<U, T> recovery) {
        Throwable failure = getFailure(resolvedPromise);
        if (throwableClass.isAssignableFrom(failure.getClass())) {
            return recovery.apply((U) failure);
        }
        return null; // no recovery
    }

    @SuppressWarnings("unchecked")
    public static <T, U extends Throwable> Promise<T> recoverFromWith(Promise<?> resolvedPromise, Class<U> throwableClass,
            Function<U, Promise<T>> recovery) {
        Throwable failure = getFailure(resolvedPromise);
        if (throwableClass.isAssignableFrom(failure.getClass())) {
            return recovery.apply((U) failure);
        }
        return null; // no recovery
    }

    public static Throwable getFailure(Promise<?> resolvedPromise) {
        if (!resolvedPromise.isDone()) {
            throw new IllegalArgumentException("Promise must be resolved");
        }
        try {
            return resolvedPromise.getFailure();
        } catch (InterruptedException ie) {
            throw new AssertionError(); // cannot happen!
        }
    }

    public static <T> Mono<T> toMono(Promise<T> promise) {
        MonoProcessor<T> monoP = MonoProcessor.create();
        onResolve(promise, success -> monoP.onNext(success), error -> monoP.onError(error));
        return monoP;
    }

    public static <T> Promise<T> fromMono(Mono<T> mono) {
        Deferred<T> deferred = new Deferred<>();
        mono.doOnTerminate((value, error) -> {
            if (error != null) {
                deferred.fail(error);
            } else {
                deferred.resolve(value);
            }
        }).subscribe();
        return deferred.getPromise();
    }

}

final class AllSuccessful<T> implements Runnable {
    private final Deferred<List<T>> chained;
    private final List<Promise<? extends T>> promises;
    private final AtomicInteger promiseCount;

    AllSuccessful(Deferred<List<T>> chained, List<Promise<? extends T>> promises) {
        this.chained = chained;
        this.promises = promises;
        this.promiseCount = new AtomicInteger(promises.size());
    }

    public void run() {
        if (promiseCount.decrementAndGet() != 0) {
            return;
        }
        List<T> result = new ArrayList<T>(promises.size());
        for (Promise<? extends T> promise : promises) {
            Throwable failure;
            T value;
            try {
                failure = promise.getFailure();
                value = (failure != null) ? null : promise.getValue();
            } catch (Throwable e) {
                chained.fail(e); // should never happen.
                return;
            }
            if (failure == null) {
                result.add(value);
            }
        }
        chained.resolve(result);
    }
}

final class AnySuccessful<T> implements Runnable {
    private final Deferred<T> chained;
    private final List<Promise<? extends T>> promises;
    private final AtomicInteger promiseCount;

    AnySuccessful(Deferred<T> chained, List<Promise<? extends T>> promises) {
        this.chained = chained;
        this.promises = promises;
        this.promiseCount = new AtomicInteger(promises.size());
    }

    public void run() {
        boolean last = promiseCount.decrementAndGet() == 0;
        Throwable failure = null;

        for (Promise<? extends T> promise : promises) {
            T value;
            try {
                failure = promise.getFailure();
                value = (failure != null) ? null : promise.getValue();
            } catch (Throwable e) {
                chained.fail(e); // should never happen.
                return;
            }
            if (failure == null) {
                chained.resolve(value);
                return;
            }
        }
        if (last) {
            chained.fail(new FailedPromisesException(new ArrayList<Promise<?>>(promises), failure));

        }
    }
}