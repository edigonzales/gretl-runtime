package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.edit.EditVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class FieldSourceEditsTest {
    @Test void anEquivalentQualifiedParameterReferenceDoesNotRewriteItsSpelling() {
        try (var fixture = new Fixture()) {
            fixture.files.put(SETTINGS,"defaults ':a'");
            String before = "parameter('p', description:'Input'); task('a', Echo) { input=parameter(':p') }";
            fixture.files.put(JOB,before);
            var transaction = new EditTransaction(fixture.capture(),EditStyle.defaults());
            var task = new ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId(ch.so.agi.gretl.tooling.semantic.SemanticModel.Namespace.TASK,":a");
            var parameter = new ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId(ch.so.agi.gretl.tooling.semantic.SemanticModel.Namespace.PARAMETER,":p");
            new FieldSourceEdits().set(transaction,task,"input",new EditableValue.ParameterRef(parameter));
            var plan = transaction.finish("No change",CancellationToken.NONE);
            assertTrue(plan.noOp());
            assertEquals(before,plan.postimage().source(JOB).orElseThrow().text());
        }
    }
    @Test void changesOnlyTheQuotedRhsAndKeepsOpaqueCodeAndInlineComment() {
        try (var fixture = new Fixture()) {
            String before = "task('a', Echo) {\r\n  input=\"old\" // keep\r\n  other=opaqueCall()\r\n}\r\n";
            fixture.files.put(JOB, before);
            var snapshot = fixture.capture();
            var intent = new SemanticEditIntent.SetField(TASK,"input",new EditableValue.Literal("new\"${text}"));
            assertEquals(EditCapability.State.ALLOWED, new EditabilityAnalyzer().assess(snapshot,intent,EditStyle.defaults()).state());
            var transaction = new EditTransaction(snapshot,EditStyle.defaults());
            new FieldSourceEdits().set(transaction,TASK,"input",intent.value());
            var plan = transaction.finish("Set input",CancellationToken.NONE);
            assertEquals("task('a', Echo) {\r\n  input=\"new\\\"\\${text}\" // keep\r\n  other=opaqueCall()\r\n}\r\n",plan.postimages().get(JOB));
            assertEquals(1,plan.changes().get(JOB).size());
            assertEquals("\"old\"",plan.changes().get(JOB).getFirst().expectedText());
            assertEquals(before,fixture.files.get(JOB));
        }
    }

    @Test void equivalentDecodedLiteralDoesNotNormalizeQuotesOrEscapes() {
        try (var fixture = new Fixture()) {
            String before = "task('a', Echo) { input=\"\\u006f\\u006c\\u0064\" }";
            fixture.files.put(JOB,before);
            var transaction = new EditTransaction(fixture.capture(),EditStyle.defaults());
            new FieldSourceEdits().set(transaction,TASK,"input",new EditableValue.Literal("old"));
            var plan = transaction.finish("No change",CancellationToken.NONE);
            assertTrue(plan.noOp());
            assertTrue(plan.postimages().isEmpty());
            assertEquals(before,plan.postimage().source(JOB).orElseThrow().text());
        }
    }

    @Test void insertingFieldPreservesTheExistingClosingBraceIndentationAndCrLf() {
        try (var fixture = new Fixture()) {
            fixture.files.put(JOB,"  task('a', Echo) {\r\n    input='old'\r\n  }\r\n");
            var transaction = new EditTransaction(fixture.capture(),EditStyle.defaults());
            new FieldSourceEdits().set(transaction,TASK,"mode",new EditableValue.Literal("FAST"));
            var plan = transaction.finish("Set mode",CancellationToken.NONE);
            assertEquals("  task('a', Echo) {\r\n    input='old'\r\n    mode = 'FAST'\r\n  }\r\n",plan.postimages().get(JOB));
        }
    }

    @Test void removalRetainsCommentsAndDoesNotRemoveRequiredFields() {
        try (var fixture = new Fixture()) {
            fixture.files.put(JOB,"task('a', Echo) { input='old'; mode='FAST' /* retained */ }");
            var before = fixture.capture();
            var transaction = new EditTransaction(before,EditStyle.defaults());
            new FieldSourceEdits().remove(transaction,TASK,"mode");
            var plan = transaction.finish("Remove mode",CancellationToken.NONE);
            assertEquals("task('a', Echo) { input='old';  /* retained */ }",plan.postimages().get(JOB));
            assertThrows(IllegalArgumentException.class,()->new FieldSourceEdits().remove(new EditTransaction(before,EditStyle.defaults()),TASK,"input"));
        }
    }

    @Test void commentsInsideAnEditedValueAreNotSilentlyDiscarded() {
        try (var fixture = new Fixture()) {
            fixture.files.put(JOB,"task('a', Echo) { input=['a', /* keep */ 'b'] }");
            var transaction = new EditTransaction(fixture.capture(),EditStyle.defaults());
            var value = new EditableValue.Sequence(java.util.List.of(new EditableValue.Literal("new")));
            var failure = assertThrows(IllegalArgumentException.class,()->new FieldSourceEdits().set(transaction,TASK,"input",value));
            assertEquals("EDIT_COMMENT_OWNERSHIP",failure.getMessage());
        }
    }
}
