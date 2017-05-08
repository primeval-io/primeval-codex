package io.primeval.codex.io.resource;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;

import org.osgi.util.promise.Promise;

import io.primeval.codex.io.file.ReactiveFile;
import io.primeval.codex.io.file.ReactiveFileReader;

public interface ReactiveResourceReader {

    Promise<ReactiveFile> readResource(ClassLoader classLoader, String resourcePath,
            IntFunction<ByteBuffer> byteBufferFactory, int bufferSize);

    default Promise<ReactiveFile> readResource(Class<?> clazz, String resourcePath,
            IntFunction<ByteBuffer> byteBufferFactory, int bufferSize) {
        return readResource(clazz.getClassLoader(), resourcePath, byteBufferFactory, bufferSize);
    }

    default Promise<ReactiveFile> readResource(ClassLoader classLoader, String resourcePath) {
        return readResource(classLoader, resourcePath, ByteBuffer::allocate,
                ReactiveFileReader.DEFAULT_BUFFER_SIZE);
    }

    default Promise<ReactiveFile> readResource(Class<?> clazz, String resourcePath) {
        return readResource(clazz.getClassLoader(), resourcePath);
    }

}
