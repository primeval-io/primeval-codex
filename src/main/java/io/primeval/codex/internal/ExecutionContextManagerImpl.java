package io.primeval.codex.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import io.primeval.codex.context.ExecutionContextHook;
import io.primeval.codex.context.ExecutionContextManager;
import io.primeval.codex.context.ExecutionContextSwitch;

@Component
public final class ExecutionContextManagerImpl implements ExecutionContextManager {
    
    static final ExecutionContextSwitch NOOP_EXECUTION_CONTEXT_SWITCH = new ExecutionContextSwitch() {

        @Override
        public void unapply() {
            // do nothing
        }

        @Override
        public void apply() {
            // do nothing
        }
    };

    private final List<ExecutionContextHook> executionContextHooks = new CopyOnWriteArrayList<>();
    
    @Override
    public ExecutionContextSwitch onDispatch() {
        List<ExecutionContextSwitch> contextSwitchs = new ArrayList<>();
        for (ExecutionContextHook executionContextHook: executionContextHooks) {
            ExecutionContextSwitch contextSwitch = executionContextHook.onDispatch();
            contextSwitchs.add(contextSwitch);
        }
        return new ExecutionContextSwitch() {
            
            @Override
            public void unapply() {
                for (ExecutionContextSwitch ecs: contextSwitchs) {
                    ecs.unapply();
                }
            }
            
            @Override
            public void apply() {
                for (ExecutionContextSwitch ecs: contextSwitchs) {
                    ecs.apply();
                }                
            }
        };
        
    }
    

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void addExecutionContextHook(ExecutionContextHook executionContextHook) {
        executionContextHooks.add(executionContextHook);
    }

    public void removeExecutionContextHook(ExecutionContextHook executionContextHook) {
        executionContextHooks.remove(executionContextHook);
    }
}
