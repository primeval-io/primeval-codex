package io.primeval.codex.context;

import java.util.Optional;

public interface MappedExecutionContext {

    <K, V> V getCurrent(K key);

    default <K, V> Optional<V> tryCurrent(K key) {
        return Optional.ofNullable(getCurrent(key));
    }

    <K, V> V setCurrent(K key, V value);

    <K, V> V removeCurrent(K key);

}
