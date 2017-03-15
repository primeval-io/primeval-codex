package io.primeval.codex.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.osgi.service.component.annotations.Component;

import io.primeval.codex.context.ExecutionContextHook;
import io.primeval.codex.context.ExecutionContextSwitch;
import io.primeval.codex.context.TypeTagExecutionContext;
import io.primeval.common.base.Preconditions;
import io.primeval.common.type.TypeTag;

@Component(name = TypeTagExecutionContextImpl.NAME)
public final class TypeTagExecutionContextImpl implements TypeTagExecutionContext, ExecutionContextHook {
    public static final String NAME = "typetag-execution-context";

    private final ThreadLocal<Map<TypeTag<? extends Object>, Object>> current = new ThreadLocal<>();

    private void clearContext() {
        current.remove();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCurrent(TypeTag<T> typeToken) {
        Map<TypeTag<? extends Object>, Object> context = current.get();

        if (context == null) {
            return null;
        }

        return (T) context.get(typeToken);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T setCurrent(TypeTag<T> typeToken, T value) {
        Preconditions.checkNotNull(value, "ExecutionContexts value must not be null");
        Map<TypeTag<? extends Object>, Object> context = current.get();
        if (context == null) {
            context = new HashMap<>();
            current.set(context);
        }
        return (T) context.put(typeToken, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T removeCurrent(TypeTag<T> typeToken) {
        Map<TypeTag<? extends Object>, Object> context = current.get();
        if (context == null) {
            return null;
        }
        return (T) context.remove(typeToken);
    }

    public Map<TypeTag<? extends Object>, Object> getFullContext() {
        Map<TypeTag<? extends Object>, Object> context = current.get();
        if (context == null) {
            return null;
        }
        return copy(context);
    }

    public void setFullContext(Map<TypeTag<? extends Object>, Object> context) {
        // copy context, don't share it.
        if (context == null) {
            current.remove();
        } else {
            current.set(context);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<TypeTag<? extends Object>, Object> copy(Map<TypeTag<? extends Object>, Object> map) {
        Map<TypeTag<? extends Object>, Object> copy = new HashMap<>();
        for (Entry<TypeTag<? extends Object>, Object> kv : map.entrySet()) {
            copy.put((TypeTag<Object>) kv.getKey(), kv.getValue());
        }
        return copy;
    }

    @Override
    public ExecutionContextSwitch onDispatch() {
        Map<TypeTag<? extends Object>, Object> fullContext = getFullContext();
        return new ExecutionContextSwitch() {

            @Override
            public void unapply() {
                clearContext();
            }

            @Override
            public void apply() {
                setFullContext(fullContext);
            }
        };
    }

}
