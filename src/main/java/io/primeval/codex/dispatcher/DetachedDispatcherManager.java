package io.primeval.codex.dispatcher;

public interface DetachedDispatcherManager {

    DetachedDispatcher create(String name, int minThreads, int maxThreads);

}
