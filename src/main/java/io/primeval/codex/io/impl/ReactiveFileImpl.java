package io.primeval.codex.io.impl;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;

import io.primeval.codex.io.file.ReactiveFile;
import io.primeval.codex.util.Procedure;

public final class ReactiveFileImpl implements ReactiveFile {

    private final Path path;
    private final long length;
    private final Supplier<Publisher<ByteBuffer>> contentSupplier;
    private final BooleanSupplier openState;
    private final Consumer<Procedure> closeHandler;

    public ReactiveFileImpl(Path path, long length, Supplier<Publisher<ByteBuffer>> contentSupplier,
            BooleanSupplier openState, Consumer<Procedure> closeHandler) {
        super();
        this.path = path;
        this.length = length;
        this.contentSupplier = contentSupplier;
        this.openState = openState;
        this.closeHandler = closeHandler;
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public Publisher<ByteBuffer> content() {
        return contentSupplier.get();
    }
    
    @Override
    public boolean isOpen() {
        return openState.getAsBoolean();
    }

    @Override
    public void close(Procedure onClose) {
        closeHandler.accept(onClose);
    }

}
