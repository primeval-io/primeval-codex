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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.IntFunction;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.primeval.codex.io.IODispatcher;
import io.primeval.codex.io.file.ReactiveFile;
import io.primeval.codex.io.file.ReactiveFileReader;
import io.primeval.codex.io.resource.ReactiveResourceReader;
import io.primeval.codex.io.resource.ResourceFinder;
import io.primeval.codex.promise.PromiseHelper;

@Component
public final class ResourceFinderReaderImpl implements ResourceFinder, ReactiveResourceReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceFinderReaderImpl.class);

    private IODispatcher ioDispatcher;
    private ReactiveFileReader reactiveFileReader;

    private final ConcurrentMap<Path, Promise<Path>> currentPaths = new ConcurrentHashMap<>();

    @Override
    public Promise<ReactiveFile> readResource(ClassLoader classLoader, String resourcePath,
            IntFunction<ByteBuffer> byteBufferFactory, int bufferSize) {
        if (classLoader instanceof BundleReference) {
            return ioDispatcher.dispatch(() -> {
                Promise<Path> pathPms = getBundleResourcePath(classLoader, resourcePath);
                return PromiseHelper.mapFallible(pathPms,
                        path -> ReactiveFileReaderImpl.getReactiveFileBlocking(ioDispatcher, path,
                                byteBufferFactory, bufferSize));
            }).flatMap(x -> x);

        } else {
            return PromiseHelper
                    .wrapPromise(() -> getNonBundleResourcePath(classLoader, resourcePath)
                            .flatMap(path -> reactiveFileReader.read(path,
                                    byteBufferFactory, bufferSize)));
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
        }).flatMap(x -> x);

    }

    private Promise<Path> getNonBundleResourcePath(ClassLoader classLoader, String resourcePath)
            throws URISyntaxException {
        return PromiseHelper.wrap(() -> {
            URL resource = classLoader.getResource(resourcePath);
            if (resource == null) {
                throw new FileNotFoundException(resourcePath.toString());
            }
            return Paths.get(resource.toURI());
        });
    }

    private Promise<Path> getBundleResourcePath(ClassLoader classLoader, String resourcePath)
            throws FileNotFoundException, IOException {
        Bundle bundle = ((BundleReference) classLoader).getBundle();
        File rootPersistentArea = bundle.getDataFile("");

        Path path = Paths.get(rootPersistentArea.getAbsolutePath(), resourcePath);

        // Deal with several threads asking for the copy the resource the first time.

        // The idea is that threads get a promise a path that is ready to consume
        Promise<Path> pPms = currentPaths.computeIfAbsent(path, p -> {
            return PromiseHelper.wrapPromise(() -> {
                if (Files.notExists(p)) {
                    Deferred<Path> def = new Deferred<>();
                    URL resource = bundle.getResource(resourcePath);
                    if (resource == null) {
                        throw new FileNotFoundException(resourcePath);
                    }
                    def.resolveWith(copyResource(p, resource));
                    return def.getPromise();
                } else {
                    return Promises.resolved(p);
                }
            });
        });
        
        // Don't keep paths more than needed.
        pPms.onResolve(() -> currentPaths.remove(path));
        return pPms;

    }

    private Promise<Path> copyResource(Path path, URL resource) throws IOException {
        return PromiseHelper.wrap(() -> {
            Files.createDirectories(path.getParent());
            try (InputStream inputStream = resource.openStream()) {
                Files.copy(inputStream, path);
                return path;
            } catch (Exception e) {
                LOGGER.error("Error while copying bundle resource to storage area", e);
                throw e;
            }
        });

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
