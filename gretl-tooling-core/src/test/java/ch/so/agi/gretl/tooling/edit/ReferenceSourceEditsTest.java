package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace.*;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceSourceEditsTest {
    SymbolId id(String name){return new SymbolId(Namespace.TASK,name);}

    @Test void renamePreservesQuotesOrdinaryStringsCommentsAliasesAndEveryReferenceKind() {
        try(var fixture=new EditTestWorkspace()) {
            String source="// 'a' stays\r\ndef ref=task(\"a\", Echo) { input='a' }\r\n"
                    +"action('use', dependsOn:ref, after:'a') { result(ref); resultOptional(\"a\") }\r\n"
                    +"defaults 'a','use'\r\ndef ordinary='a'\r\n";
            fixture.files.put(JOB,source);
            var before=fixture.capture();
            var transaction=new EditTransaction(before,EditStyle.defaults());
            new ReferenceSourceEdits().rename(transaction,new SemanticEditIntent.RenameSymbol(TASK,"new's😀"),CancellationToken.NONE);
            var plan=transaction.finish("Rename",CancellationToken.NONE);
            String actual=plan.postimages().get(JOB);
            assertTrue(actual.startsWith("// 'a' stays\r\ndef ref=task(\"new's😀\", Echo) { input='a' }"));
            assertTrue(actual.contains("dependsOn:ref, after:'new\\'s😀'"));
            assertTrue(actual.contains("result(ref); resultOptional(\"new's😀\")"));
            assertTrue(actual.contains("defaults 'new\\'s😀','use'"));
            assertTrue(actual.endsWith("def ordinary='a'\r\n"));
            assertEquals(before.references().referencesTo(TASK).uses().size(),plan.postimage().references().referencesTo(id("new's😀")).uses().size());
            assertTrue(plan.postimage().analysis().unique(TASK).isEmpty());
        }
    }

    @Test void projectRenameUpdatesSettingsAndOtherSubprojectsWhileKeepingLocalReferencesLocal() {
        try(var fixture=new EditTestWorkspace()) {
            URI a=URI.create("file:/work/a/job.groovy"),b=URI.create("file:/work/b/job.groovy");
            fixture.files.put(SETTINGS,"include 'a','b'\ndefaults ':a:run', ':b:use'");
            fixture.files.put(a,"task('run', Echo) { input='x' }; action('local', dependsOn:'run') { result('run') }");
            fixture.files.put(b,"action('use', dependsOn:':a:run', after:':a:run') { resultOptional(':a:run') }");
            var before=fixture.capture();
            var transaction=new EditTransaction(before,EditStyle.defaults());
            new ReferenceSourceEdits().rename(transaction,new SemanticEditIntent.RenameSymbol(id(":a:run"),"renamed"),CancellationToken.NONE);
            var plan=transaction.finish("Rename project task",CancellationToken.NONE);
            assertEquals(java.util.Set.of(SETTINGS,a,b),plan.changes().keySet());
            assertTrue(plan.postimages().get(a).contains("dependsOn:'renamed') { result('renamed')"));
            assertTrue(plan.postimages().get(b).contains("dependsOn:':a:renamed', after:':a:renamed'"));
            assertEquals(List.of(":a:renamed",":b:use"),plan.postimage().analysis().defaultTargets());
        }
    }

    @Test void parameterRenameIsNamespaceSpecificAndReportsTheExternalCliBoundary() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.files.put(JOB,"parameter('p', description:'Input'); task('p', Echo) { input=parameter('p') }; defaults 'p'");
            var before=fixture.capture();
            var transaction=new EditTransaction(before,EditStyle.defaults());
            new ReferenceSourceEdits().rename(transaction,new SemanticEditIntent.RenameSymbol(new SymbolId(Namespace.PARAMETER,"p"),"value"),CancellationToken.NONE);
            var plan=transaction.finish("Rename parameter",CancellationToken.NONE);
            assertTrue(plan.postimages().get(JOB).contains("task('p', Echo) { input=parameter('value') }; defaults 'p'"));
            assertTrue(plan.postimages().get(JOB).startsWith("parameter('value'"));
            assertTrue(plan.notices().getFirst().contains("External CLI"));
        }
    }

    @Test void renameDoesNotAccidentallyCapturePreviouslyUnresolvedReferences() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.files.put(JOB,"task('a', Echo) { input='x' }; action('use', dependsOn:'b') { result('b') }; defaults 'a'");
            var transaction=new EditTransaction(fixture.capture(),EditStyle.defaults());
            new ReferenceSourceEdits().rename(transaction,new SemanticEditIntent.RenameSymbol(TASK,"b"),CancellationToken.NONE);
            assertEquals("EDIT_POSTCONDITION",assertThrows(IllegalArgumentException.class,()->transaction.finish("Unsafe capture",CancellationToken.NONE)).getMessage());
        }
    }

    @Test void deleteOnlyAnUnreferencedOwnedStatementAndKeepAdjacentComments() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.files.put(JOB,"// before\ndef ref=task('a', Echo) { input='x' } // after\naction('keep') {}\n");
            var transaction=new EditTransaction(fixture.capture(),EditStyle.defaults());
            new ReferenceSourceEdits().delete(transaction,new SemanticEditIntent.DeleteTask(TASK),CancellationToken.NONE);
            var plan=transaction.finish("Delete",CancellationToken.NONE);
            assertEquals("// before\n // after\naction('keep') {}\n",plan.postimages().get(JOB));
            assertEquals(java.util.Set.of(id("keep")),plan.postimage().analysis().symbols().keySet());
        }
    }

    @Test void aliasEscapesBlockDeleteAndRenameWhileKnownUsesInsideInterpolationRemainSafe() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.files.put(JOB,"def ref=task('a', Echo) { input='x' }; action('use') { println ref }");
            var before=fixture.capture();
            assertEquals("EDIT_ALIAS_ESCAPE",assertThrows(IllegalArgumentException.class,()->new ReferenceSourceEdits().delete(new EditTransaction(before,EditStyle.defaults()),new SemanticEditIntent.DeleteTask(TASK),CancellationToken.NONE)).getMessage());
            assertEquals("EDIT_ALIAS_ESCAPE",assertThrows(IllegalArgumentException.class,()->new ReferenceSourceEdits().rename(new EditTransaction(before,EditStyle.defaults()),new SemanticEditIntent.RenameSymbol(TASK,"b"),CancellationToken.NONE)).getMessage());
            fixture.files.put(JOB,"def a=task('a', Echo) { input='x' }; action('use', dependsOn:a) { println \"${result('a')}\" }");
            var transaction=new EditTransaction(fixture.capture(),EditStyle.defaults());
            new ReferenceSourceEdits().rename(transaction,new SemanticEditIntent.RenameSymbol(TASK,"b"),CancellationToken.NONE);
            var plan=transaction.finish("Rename interpolation reference",CancellationToken.NONE);
            assertTrue(plan.postimages().get(JOB).contains("dependsOn:a) { println \"${result('b')}\""));
        }
    }
}
