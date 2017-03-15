package io.primeval.codex.context;

import java.util.Optional;

import io.primeval.common.type.TypeTag;

public interface TypeTagExecutionContext {

    <T> T getCurrent(TypeTag<T> typeTag);

    default <T> T getCurrent(Class<T> clazz) {
        return getCurrent(TypeTag.of(clazz));
    }

    default <T> Optional<T> tryCurrent(TypeTag<T> typeTag) {
        return Optional.ofNullable(getCurrent(typeTag));
    }

    default <T> Optional<T> tryCurrent(Class<T> clazz) {
        return Optional.ofNullable(getCurrent(clazz));
    }

    <T> T setCurrent(TypeTag<T> typeTag, T value);

    <T> T removeCurrent(TypeTag<T> typeTag);

    default <T> T setCurrent(Class<T> clazz, T value) {
        return setCurrent(TypeTag.of(clazz), value);
    }

    default <T> T removeCurrent(Class<T> clazz) {
        return removeCurrent(TypeTag.of(clazz));
    }

}
