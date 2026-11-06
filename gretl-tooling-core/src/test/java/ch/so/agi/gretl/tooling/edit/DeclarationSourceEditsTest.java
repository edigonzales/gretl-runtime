package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace.*;
import static org.junit.jupiter.api.Assertions.*;

class DeclarationSourceEditsTest {
    @Test void createsARealTaskFromCatalogDataWithoutInventingRequiredValues() {
        try (var fixture = new EditTestWorkspace()) {
            var before = fixture.capture();
            var transaction = new EditTransaction(before,EditStyle.defaults());
            String name = "next's😀";
            new DeclarationSourceEdits().addTask(transaction,new SemanticEditIntent.AddTask(JOB,fixture.files.get(JOB).length(),name,"test.echo",
                    Map.of("input",new EditableValue.Literal("ready"))));
            var plan = transaction.finish("Add task",CancellationToken.NONE);
            var created = plan.postimage().analysis().unique(new SymbolId(Namespace.TASK,name)).orElseThrow();
            assertEquals(DeclarationKind.TASK,created.declaration().kind());
            assertEquals("test.echo",created.operation().operationId());
            assertEquals("ready",((LiteralValue)created.declaration().fields().getFirst().value()).value());
            assertTrue(plan.postimages().get(JOB).startsWith(fixture.files.get(JOB)));
            assertTrue(plan.postimages().get(JOB).contains("task('next\\'s😀', Echo)"));
            assertEquals(before.analysis().defaultTargets(),plan.postimage().analysis().defaultTargets());
            assertThrows(IllegalArgumentException.class,()->new DeclarationSourceEdits().addTask(new EditTransaction(before,EditStyle.defaults()),
                    new SemanticEditIntent.AddTask(JOB,fixture.files.get(JOB).length(),"missing","test.echo",Map.of())));
        }
    }

    @Test void actionAndParameterHaveActualDslShapesAndIndependentNamespaces() {
        try (var fixture = new EditTestWorkspace()) {
            var before = fixture.capture();
            var action = new EditTransaction(before,EditStyle.defaults());
            new DeclarationSourceEdits().addAction(action,new SemanticEditIntent.AddAction(JOB,fixture.files.get(JOB).length(),"work"));
            var actionPlan = action.finish("Add action",CancellationToken.NONE);
            var created = actionPlan.postimage().analysis().unique(new SymbolId(Namespace.TASK,"work")).orElseThrow();
            assertEquals(DeclarationKind.ACTION,created.declaration().kind());
            assertNull(created.operation());
            assertTrue(actionPlan.postimages().get(JOB).contains("println 'work'"));
            var parameter = new EditTransaction(before,EditStyle.defaults());
            new DeclarationSourceEdits().addParameter(parameter,new SemanticEditIntent.AddParameter(JOB,fixture.files.get(JOB).length(),
                    new SemanticEditIntent.ParameterDeclaration("a","Count","integer",false,new EditableValue.Literal(Long.MAX_VALUE))));
            var parameterPlan = parameter.finish("Add parameter",CancellationToken.NONE);
            assertTrue(parameterPlan.postimage().analysis().unique(TASK).isPresent());
            var declaration = parameterPlan.postimage().analysis().unique(new SymbolId(Namespace.PARAMETER,"a")).orElseThrow().declaration();
            assertEquals(DeclarationKind.PARAMETER,declaration.kind());
            assertEquals(Long.MAX_VALUE,((LiteralValue)declaration.options().stream().filter(e->((LiteralValue)e.key()).value().equals("defaultValue")).findFirst().orElseThrow().value()).value());
        }
    }

    @Test void unsafeInsertionOffsetsAndCollidingNamesAreRejectedBeforeApplication() {
        try (var fixture = new EditTestWorkspace()) {
            var before = fixture.capture();
            var edits = new DeclarationSourceEdits();
            assertThrows(IllegalArgumentException.class,()->edits.addAction(new EditTransaction(before,EditStyle.defaults()),
                    new SemanticEditIntent.AddAction(JOB,fixture.files.get(JOB).indexOf("input") + 1,"new")));
            assertThrows(IllegalArgumentException.class,()->edits.addAction(new EditTransaction(before,EditStyle.defaults()),
                    new SemanticEditIntent.AddAction(JOB,fixture.files.get(JOB).indexOf('\n'),"new")));
            assertThrows(IllegalArgumentException.class,()->edits.addAction(new EditTransaction(before,EditStyle.defaults()),
                    new SemanticEditIntent.AddAction(JOB,fixture.files.get(JOB).length(),"a")));
            fixture.files.put(SETTINGS,"defaults ':a'");
            var project = fixture.capture();
            assertEquals("EDIT_INVALID_NAME",assertThrows(IllegalArgumentException.class,()->edits.addAction(new EditTransaction(project,EditStyle.defaults()),
                    new SemanticEditIntent.AddAction(JOB,fixture.files.get(JOB).length(),"bad:name"))).getMessage());
        }
    }

    @Test void rejectsInvalidParameterContractsAndKeepsALeadingBomWhenAppending() {
        try (var fixture = new EditTestWorkspace()) {
            fixture.files.put(JOB,"\uFEFF");
            var before = fixture.capture();
            var transaction = new EditTransaction(before,EditStyle.defaults());
            var edits = new DeclarationSourceEdits();
            edits.addTask(transaction,new SemanticEditIntent.AddTask(JOB,1,"new","test.echo",Map.of("input",new EditableValue.Literal("x"))));
            var plan = transaction.finish("Add task",CancellationToken.NONE);
            assertTrue(plan.postimages().get(JOB).startsWith("\uFEFFtask('new', Echo)"));
            assertThrows(IllegalArgumentException.class,()->edits.addParameter(new EditTransaction(before,EditStyle.defaults()),
                    new SemanticEditIntent.AddParameter(JOB,1,new SemanticEditIntent.ParameterDeclaration("p","Description","text",true,new EditableValue.Literal("default")))));
        }
    }
}
