package io.primeval.codex.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

import io.primeval.codex.promise.CancelablePromise;

public final class Executions {
    private Executions() {
    }

    public static <T> Promise<List<T>> all(Collection<CancelablePromise<T>> promises) {
        Collection<Promise<T>> c = new ArrayList<>();
        for (CancelablePromise<T> p : promises) {
            c.add(p);
        }
        return Promises.all(c);
    }
}
