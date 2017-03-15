package io.primeval.codex.publisher;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import reactor.core.publisher.BlockingSink;
import reactor.core.publisher.UnicastProcessor;

public final class UnicastPublisher<T> implements Publisher<T> {

    private final BlockingSink<T> sink;
    private final UnicastProcessor<T> emitter;

    public UnicastPublisher() {
        emitter = UnicastProcessor.create();
        this.sink = emitter.connectSink();
    }

    public void complete() {
        sink.complete();
    }

    public void next(T t) {
        sink.next(t);
    }

    public void error(Throwable t) {
        sink.error(t);
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        emitter.subscribe(s);
    }

}
