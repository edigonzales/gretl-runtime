package ch.so.agi.gretl.tasks.database;
import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import org.junit.jupiter.api.Test;
class DatabaseTaskProviderTest {
    @Test void serviceLoaderExposesThreeDatabaseTasksAndSqlMetadata() {
        OperationRegistry registry = OperationRegistry.load(getClass().getClassLoader());
        assertEquals(3, registry.all().size());
        assertEquals("Db2Db", registry.requireSymbol("Db2Db").symbol());
        assertEquals("DuckDbSqlExecutor", registry.requireSymbol("DuckDbSqlExecutor").symbol());
        assertEquals("SqlExecutor", registry.requireSymbol("SqlExecutor").symbol());
        OperationType<?, ?> sql = registry.requireSymbol("SqlExecutor");
        assertTrue(sql.inputs().stream().anyMatch(field -> field.name().equals("scripts")));
        assertTrue(sql.outputs().stream().anyMatch(field -> field.name().equals("executions")));
    }
}
