package io.primeval.codex.dispatcher;

import java.io.Closeable;

public interface DetachedDispatcher extends Dispatcher, AutoCloseable, Closeable {

    // Quiet close.
    void close();
}
