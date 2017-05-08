package io.primeval.codex.io.resource;

import java.nio.file.Path;

import org.osgi.util.promise.Promise;

public interface ResourceFinder {

    Promise<Path> findResource(ClassLoader classLoader, String resourcePath);
    
    default Promise<Path> findResource(Class<?> clazz, String resourcePath) {
        return findResource(clazz.getClassLoader(), resourcePath);
    }
    
}
