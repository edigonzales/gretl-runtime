package ch.so.agi.gretl.tasks.core;

import ch.so.agi.gretl.runtime.api.OperationProvider;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.tasks.xslt.XslTransformerOperation;
import java.util.Collection;
import java.util.List;

/** Core task catalog beyond the HTTP/archive/SQL basic module. */
public final class CoreTaskProvider implements OperationProvider {
    @Override
    public Collection<OperationType<?, ?>> operations() {
        return List.of(XslTransformerOperation.type());
    }
}
