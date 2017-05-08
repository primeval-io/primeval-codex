package io.primeval.codex.file;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.IntFunction;

import org.osgi.util.promise.Promise;

public interface ReactiveFileReader {

    public static final int DEFAULT_BUFFER_SIZE = 1024;

    /**
     * Read.
     *
     * @param path
     *            the path
     * @param byteBufferFactory
     *            the byte buffer factory
     * @param bufferSize
     *            the buffer size
     * @return the promise
     */
    Promise<ReactiveFile> read(Path path, IntFunction<ByteBuffer> byteBufferFactory, int bufferSize);

    /**
     * Uses a shared lock to lock the file and read it; unlock when result contents publisher is fully read, failed or canceled.
     * 
     * @param path
     * @param byteBufferFactory
     * @param bufferSize
     * @return
     */
    Promise<ReactiveFile> readLocked(Path path, IntFunction<ByteBuffer> byteBufferFactory, int bufferSize);

    default Promise<ReactiveFile> read(File file) {
        return read(file.toPath(), ByteBuffer::allocate, DEFAULT_BUFFER_SIZE);
    }

}
