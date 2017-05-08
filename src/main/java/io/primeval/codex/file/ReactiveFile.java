package io.primeval.codex.file;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import org.reactivestreams.Publisher;

import io.primeval.codex.util.Procedure;
import reactor.core.publisher.Flux;

public interface ReactiveFile extends AutoCloseable {

    Path path();

    long length();

    /**
     * Return a new instance of a Publisher<ByteBuffer> representing the content of a file.
     * 
     * Each call to contents() restarts a read.
     * 
     * Close must be called once the reading is done.
     * 
     */
    Publisher<ByteBuffer> content();
    
    default Publisher<ByteBuffer> autoCloseContent() {
        return Flux.from(content()).doFinally(s -> close());
    }

    
    boolean isOpen();

    void close(Procedure onClose);

    default void close() {
        close(Procedure.NOOP);
    }
}
