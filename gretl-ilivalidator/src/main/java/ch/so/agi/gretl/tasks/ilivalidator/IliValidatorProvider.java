package ch.so.agi.gretl.tasks.ilivalidator;

import ch.so.agi.gretl.runtime.api.OperationProvider;
import ch.so.agi.gretl.runtime.api.OperationType;
import java.util.Collection;
import java.util.List;

/** Stage-B provider for the real INTERLIS validator. */
public final class IliValidatorProvider implements OperationProvider {
    @Override
    public Collection<OperationType<?, ?>> operations() {
        return List.of(IliValidatorOperation.type());
    }
}
