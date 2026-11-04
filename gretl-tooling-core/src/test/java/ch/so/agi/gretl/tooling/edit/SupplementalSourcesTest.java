package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.edit.EditVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class SupplementalSourcesTest {
    static final URI SQL = URI.create("file:/work/sql/query.sql");

    void sqlCatalog(Fixture fixture) {
        var files = new InputDescriptor("sqlFiles","Files",true,false,null,List.of(),"files",ValueShape.fromMetadata("list"),null,false,Map.of());
        var parameters = new InputDescriptor("sqlParameters","Parameters",false,false,null,List.of(),"parameters",ValueShape.fromMetadata("map"),null,false,Map.of());
        fixture.catalog.mergeExplicitDescriptors(List.of(new OperationDescriptor("test.duckdb","DuckDbSqlExecutor","SQL","fixture",List.of(files,parameters),List.of(),"example",true)));
        fixture.manager.onCatalogChanged();
    }

    PlannedEdit plan(Fixture fixture, AnalysisSnapshot before) {
        var transaction = new EditTransaction(before,EditStyle.defaults());
        new DeclarationSourceEdits().addTask(transaction,new SemanticEditIntent.AddTask(JOB,fixture.files.get(JOB).length(),"sql","test.duckdb",
                Map.of("sqlFiles",new EditableValue.Sequence(List.of(new EditableValue.JobFileRef("sql/query.sql"))),
                        "sqlParameters",new EditableValue.Mapping(Map.of("key",new EditableValue.Literal("yes"))))));
        return transaction.finish("Add SQL task",CancellationToken.NONE);
    }

    @Test void newSqlDependencyRequiresAnExplicitCaptureAndItsHashInvalidatesThePlan() {
        try (var fixture = new Fixture()) {
            sqlCatalog(fixture);
            fixture.files.put(SQL,"select ${key}");
            var missing = fixture.capture();
            assertEquals("EDIT_SOURCE_NOT_CAPTURED",assertThrows(IllegalArgumentException.class,()->plan(fixture,missing)).getMessage());
            var captured = fixture.capture(List.of(SQL));
            var plan = plan(fixture,captured);
            assertNotNull(plan.preconditions().sources().get(SQL).stamp());
            assertEquals(captured.source(SQL).orElseThrow().stamp(),plan.preconditions().sources().get(SQL).stamp());
            assertTrue(plan.postimage().analysis().diagnostics().stream().noneMatch(d->d.code().equals("SQL_PARAMETER_MISSING")));
            fixture.files.put(SQL,"select ${changed}");
            var changed = fixture.capture(List.of(SQL));
            assertEquals(captured.analysis().capture().generation(),changed.analysis().capture().generation());
            assertFalse(plan.isCurrent(new AnalysisSnapshot(captured.id(),changed.analysis())));
        }
    }

    @Test void requestedDirtySqlBufferWinsAndIsPartOfThePreconditionVector() {
        try (var fixture = new Fixture()) {
            sqlCatalog(fixture);
            fixture.files.put(SQL,"select ${diskOnly}");
            fixture.buffers.open(SQL,"sql",7,"select ${key}");
            var captured = fixture.capture(List.of(SQL));
            var plan = plan(fixture,captured);
            assertEquals(7,plan.preconditions().sources().get(SQL).stamp().bufferVersion());
            assertNotNull(plan.preconditions().sources().get(SQL).bufferIncarnation());
            assertEquals("select ${key}",plan.postimage().source(SQL).orElseThrow().text());
            assertTrue(plan.postimage().analysis().diagnostics().stream().noneMatch(d->d.code().equals("SQL_PARAMETER_MISSING")));
        }
    }

    @Test void extraSourceRequestsAreBoundedAndDoNotBypassTheRootReadPolicy() {
        try (var fixture = new Fixture()) {
            URI outside = URI.create("file:/outside/secret.sql");
            fixture.files.put(outside,"must not be read");
            var capture = fixture.capture(List.of(outside));
            assertTrue(capture.source(outside).isEmpty());
            assertEquals("SOURCE_OUTSIDE_POLICY",capture.analysis().capture().project().sources().versions().get(outside).unavailableCode());
            var tooMany = java.util.stream.IntStream.range(0,33).mapToObj(i->URI.create("file:/work/"+i+".sql")).toList();
            assertThrows(IllegalArgumentException.class,()->new ProjectRequest(ROOT,JOB,false,List.of(),tooMany));
        }
    }
}
