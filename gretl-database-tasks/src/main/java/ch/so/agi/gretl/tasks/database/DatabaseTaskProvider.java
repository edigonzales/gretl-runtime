package ch.so.agi.gretl.tasks.database;

import ch.so.agi.gretl.runtime.api.*;
import java.util.*;


/** Complete database operation catalog. */
public final class DatabaseTaskProvider implements OperationProvider {
    @Override public Collection<OperationType<?, ?>> operations() {
        return List.of(SqlExecutorOperation.type(),
                Db2DbOperation.type(),
                DuckDbSqlExecutorOperation.type());
    }
}
