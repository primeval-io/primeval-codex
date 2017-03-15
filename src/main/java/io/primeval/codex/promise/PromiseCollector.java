package io.primeval.codex.promise;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;

import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

public final class PromiseCollector {

    public static <T> Collector<Promise<T>, List<Promise<T>>, Promise<List<T>>> all() {
        return Collector.of(
                ArrayList::new,
                List::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                Promises::all);
    }

    public static <T> Collector<Promise<T>, List<Promise<T>>, Promise<T>> any() {
        return Collector.of(
                ArrayList::new,
                List::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                PromiseHelper::anySuccessful);
    }

    public static <T> Collector<Promise<T>, List<Promise<T>>, Promise<List<T>>> allSuccessful() {
        return Collector.of(
                ArrayList::new,
                List::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                PromiseHelper::allSuccessful);
    }

}
