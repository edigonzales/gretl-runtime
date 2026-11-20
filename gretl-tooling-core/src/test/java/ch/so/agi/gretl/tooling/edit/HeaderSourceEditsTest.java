package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.syntax.EffectiveOptions;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.LiteralValue;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Test void formInsertsMultipleOptionsAtTheOriginalHeaderAnchor() {
        var headers = Map.of(
                "task('a', Echo)", "task('a', Echo, description: 'new', timeout: 'PT1S')",
                "task('a', Echo, // retained\n)", "task('a', Echo, // retained\ndescription: 'new', timeout: 'PT1S')",
                "task('a', Echo // retained\n)", "task('a', Echo // retained\n, description: 'new', timeout: 'PT1S')",
                "task('a', Echo /* retained */)", "task('a', Echo /* retained */, description: 'new', timeout: 'PT1S')",
                "task([\r\n  : // retained\r\n], 'a', Echo)",
                "task([\r\n  description: 'new', // retained\r\n  timeout: 'PT1S'\r\n], 'a', Echo)",
                "task([\n  :], 'a', Echo)", "task([\n  description: 'new', timeout: 'PT1S'], 'a', Echo)",
                "task([after:null,], 'a', Echo)", "task([after:null, description: 'new', timeout: 'PT1S'], 'a', Echo)",
                "task([\r\n  after:null, // retained\r\n], 'a', Echo)",
                "task([\r\n  after:null, // retained\r\n  description: 'new',\r\n  timeout: 'PT1S'\r\n], 'a', Echo)",
                "task([\r\n  after:null // retained\r\n], 'a', Echo)",
                "task([\r\n  after:null, // retained\r\n  description: 'new',\r\n  timeout: 'PT1S'\r\n], 'a', Echo)");
        String body = " { input='x' }\r\n";
        for (var header : headers.entrySet()) {
            try (var fixture = new EditTestWorkspace()) {
                fixture.files.put(JOB, header.getKey() + body);
                var result = new SemanticEditPlanner().planConfiguration(fixture.capture(), TASK, Map.of(), Set.of(),
                        Map.of("timeout", new EditableValue.Literal("PT1S"), "description", new EditableValue.Literal("new")),
                        EditStyle.defaults(), CancellationToken.NONE);
                var plan = result.plan().orElseThrow(() -> new AssertionError(header.getKey() + ": " + result.capability()));
                assertEquals(header.getValue() + body, plan.postimages().get(JOB));
                assertEquals(1, plan.changes().get(JOB).stream().filter(edit -> edit.replacement().contains("description")).count());
            }
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
