package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.runtime.api.OperationProvider;
import ch.so.agi.gretl.runtime.api.OperationType;
import java.util.Collection;
import java.util.List;

/** Standard A task catalog: HTTP, archive and SQL. */
public final class BasicTaskProvider implements OperationProvider {
    @Override
    public Collection<OperationType<?, ?>> operations() {
        return List.of(
                HttpDownloadOperation.type(),
                CurlOperation.type(),
                Db2DbOperation.type(),
                DuckDbSqlExecutorOperation.type(),
                GzipOperation.type(),
                ExtractOperation.type(),
                SqlExecutorOperation.type());
    }
}
