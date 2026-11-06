package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.syntax.EffectiveOptions;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.LiteralValue;
import java.util.List;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace.*;
import static org.junit.jupiter.api.Assertions.*;

class HeaderSourceEditsTest {
    @Test void updatesActualNamedExplicitEmptyMapAndClosureArgumentForms() {
        for (String source : List.of("task('a', Echo) { input='x' }", "task([description:'old'], 'a', Echo) { input='x' }",
                "task([:], 'a', Echo) { input='x' }", "task('a', Echo, { input='x' })", "task 'a', Echo, { input='x' }")) {
            try (var fixture = new EditTestWorkspace()) {
                fixture.files.put(JOB,source);
                var before = fixture.capture();
                assertTrue(before.analysis().unique(TASK).isPresent(),source);
                var transaction = new EditTransaction(before,EditStyle.defaults());
                new HeaderSourceEdits().set(transaction,TASK,"description",new EditableValue.Literal("new's"));
                var plan = transaction.finish("Description",CancellationToken.NONE);
                var option = EffectiveOptions.of(plan.postimage().analysis().unique(TASK).orElseThrow().declaration()).entries().get("description");
                assertEquals("new's",assertInstanceOf(LiteralValue.class,option.value()).value());
                assertTrue(plan.postimages().get(JOB).contains("input='x'"));
                assertEquals(1,plan.postimage().analysis().symbols().size());
            }
        }
    }

    @Test void multilineMapsKeepCommentsCommasIndentationAndCrLf() {
        try (var fixture = new EditTestWorkspace()) {
            String before="task([\r\n  description:'old', // map comment\r\n], 'a', Echo) { input='x' }\r\n";
            fixture.files.put(JOB,before);
            var transaction = new EditTransaction(fixture.capture(),EditStyle.defaults());
            new HeaderSourceEdits().set(transaction,TASK,"timeout",new EditableValue.Literal("PT1S"));
            var plan = transaction.finish("Timeout",CancellationToken.NONE);
            assertEquals("task([\r\n  description:'old', // map comment\r\n  timeout: 'PT1S'\r\n], 'a', Echo) { input='x' }\r\n",plan.postimages().get(JOB));
            fixture.files.put(JOB,"task([\n  : // empty map comment\n], 'a', Echo) { input='x' }");
            var empty = new EditTransaction(fixture.capture(),EditStyle.defaults());
            new HeaderSourceEdits().set(empty,TASK,"timeout",new EditableValue.Literal("PT1S"));
            assertEquals("task([\n  timeout: 'PT1S' // empty map comment\n], 'a', Echo) { input='x' }",empty.finish("Timeout",CancellationToken.NONE).postimages().get(JOB));
        }
    }

    @Test void semanticNoOpsKeepExistingEscapesAndDoNotAddEmptyDependencyOptions() {
        try (var fixture = new EditTestWorkspace()) {
            String source="task('a', Echo, description:\"\\u006f\\u006c\\u0064\") { input='x' }";
            fixture.files.put(JOB,source);
            var transaction = new EditTransaction(fixture.capture(),EditStyle.defaults());
            var edits = new HeaderSourceEdits();
            edits.set(transaction,TASK,"description",new EditableValue.Literal("old"));
            edits.set(transaction,TASK,"dependsOn",new EditableValue.Sequence(List.of()));
            var plan = transaction.finish("No change",CancellationToken.NONE);
            assertTrue(plan.noOp());
            assertEquals(source,plan.postimage().source(JOB).orElseThrow().text());
        }
    }

    @Test void validatesCurrentDslOptionTypesAndNeverChoosesADuplicate() {
        try (var fixture = new EditTestWorkspace()) {
            var before = fixture.capture();
            var edits = new HeaderSourceEdits();
            for(var item:List.of(new SemanticEditIntent.SetTaskOption(TASK,"when",new EditableValue.Literal(false)),
                    new SemanticEditIntent.SetTaskOption(TASK,"timeout",new EditableValue.Literal("PT0S")),
                    new SemanticEditIntent.SetTaskOption(TASK,"timeout",new EditableValue.Literal(10)),
                    new SemanticEditIntent.SetTaskOption(TASK,"description",new EditableValue.Literal(" ")))) {
                assertThrows(IllegalArgumentException.class,()->edits.set(new EditTransaction(before,EditStyle.defaults()),TASK,item.option(),item.value()));
            }
            fixture.files.put(JOB,"task([description:'first', description:'last'], 'a', Echo) { input='x' }");
            var duplicate = fixture.capture();
            assertEquals("EDIT_MULTIPLE_OPTIONS",assertThrows(IllegalArgumentException.class,()->edits.set(new EditTransaction(duplicate,EditStyle.defaults()),TASK,"description",new EditableValue.Literal("new"))).getMessage());
        }
    }
}
