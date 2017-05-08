package io.primeval.codex.util;

import java.util.function.Function;

@FunctionalInterface
public interface Procedure {

    public static final Procedure NOOP = () -> {};

    void call() throws Exception;

    public static Procedure fromRunnable(Runnable r) {
        return new Procedure() {

            @Override
            public void call() throws Exception {
                r.run();
            }
        };
    }

    public static Runnable toRunnable(Procedure p) {
        return toRunnable(p, RuntimeException::new);
    }

    public static Runnable toRunnable(Procedure p, Function<Exception, RuntimeException> exceptionConverter) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    p.call();
                } catch (RuntimeException re) {
                    throw re;
                } catch (Exception e) {
                    throw exceptionConverter.apply(e);
                }
            }
        };
    }
}
