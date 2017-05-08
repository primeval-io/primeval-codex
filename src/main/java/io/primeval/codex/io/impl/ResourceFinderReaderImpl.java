package io.primeval.codex.io.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.IntFunction;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.promise.Promise;

import io.primeval.codex.io.IODispatcher;
import io.primeval.codex.io.file.ReactiveFile;
import io.primeval.codex.io.file.ReactiveFileReader;
import io.primeval.codex.io.resource.ReactiveResourceReader;
import io.primeval.codex.io.resource.ResourceFinder;
import io.primeval.codex.promise.PromiseHelper;

@Component
public final class ResourceFinderReaderImpl implements ResourceFinder, ReactiveResourceReader {

    private IODispatcher ioDispatcher;
    private ReactiveFileReader reactiveFileReader;

    @Override
    public Promise<ReactiveFile> readResource(ClassLoader classLoader, String resourcePath,
            IntFunction<ByteBuffer> byteBufferFactory, int bufferSize) {
        if (classLoader instanceof BundleReference) {
            return ioDispatcher.dispatch(() -> {
                Path path = getBundleResourcePath(classLoader, resourcePath);
                return ReactiveFileReaderImpl.getReactiveFileBlocking(ioDispatcher, path, byteBufferFactory,
                        bufferSize);
            });

        } else {
            return PromiseHelper
                    .wrapPromise(() -> reactiveFileReader.read(getNonBundleResourcePath(classLoader, resourcePath),
                            byteBufferFactory, bufferSize));
        }
    }

    @Override
    public Promise<Path> findResource(ClassLoader classLoader, String resourcePath) {
        return ioDispatcher.dispatch(() -> {
            if (classLoader instanceof BundleReference) {
                return getBundleResourcePath(classLoader, resourcePath);

            } else {
                return getNonBundleResourcePath(classLoader, resourcePath);
            }
        });

    }

    private Path getNonBundleResourcePath(ClassLoader classLoader, String resourcePath) throws URISyntaxException {
        URL resource = classLoader.getResource(resourcePath);
        return Paths.get(resource.toURI());
    }

    private Path getBundleResourcePath(ClassLoader classLoader, String resourcePath)
            throws FileNotFoundException, IOException {
        Bundle bundle = ((BundleReference) classLoader).getBundle();
        File rootPersistentArea = bundle.getDataFile("");
        Path path = Paths.get(rootPersistentArea.getAbsolutePath(), resourcePath);
        if (Files.notExists(path)) {
            URL resource = bundle.getResource(resourcePath);
            if (resource == null) {
                throw new FileNotFoundException(resourcePath);
            }
            try (InputStream inputStream = resource.openStream()) {
                Files.copy(inputStream, path);
            }
        }
        return path;
    }

    @Reference
    public void setIODispatcher(IODispatcher ioDispatcher) {
        this.ioDispatcher = ioDispatcher;
    }

    @Reference
    public void setReactiveFileReader(ReactiveFileReader reactiveFileReader) {
        this.reactiveFileReader = reactiveFileReader;
    }

}
