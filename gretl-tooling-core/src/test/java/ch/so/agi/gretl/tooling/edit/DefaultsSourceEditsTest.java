package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.DeclarationKind;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace.*;
import static org.junit.jupiter.api.Assertions.*;

class DefaultsSourceEditsTest {
    SymbolId id(String name){return new SymbolId(Namespace.TASK,name);}
    String tasks(){return "task('a', Echo) { input='a' }\r\ntask('b', Echo) { input='b' }\r\n";}

    @Test void updatesAllStandaloneDefaultsAndKeepsEveryCommentAndSelectedTarget() {
        try(var fixture=new EditTestWorkspace()) {
            String before=tasks()+"defaults 'a' // first\r\n// between\r\ndefaults 'b' // second\r\n";
            fixture.files.put(JOB,before);
            var snapshot=fixture.capture();
            var unchanged=new EditTransaction(snapshot,EditStyle.defaults());
            new DefaultsSourceEdits().set(unchanged,new SemanticEditIntent.SetDefaults(JOB,List.of(id("a"),id("b"))));
            assertTrue(unchanged.finish("No change",CancellationToken.NONE).noOp());
            var transaction=new EditTransaction(snapshot,EditStyle.defaults());
            new DefaultsSourceEdits().set(transaction,new SemanticEditIntent.SetDefaults(JOB,List.of(id("b"),id("a"))));
            var plan=transaction.finish("Set defaults",CancellationToken.NONE);
            assertEquals(List.of("b","a"),plan.postimage().analysis().defaultTargets());
            assertEquals(tasks()+"defaults 'b', 'a' // first\r\n// between\r\n  // second\r\n",plan.postimages().get(JOB));
        }
    }

    @Test void clearingDefaultsKeepsInternalCommentsWithoutDeletingTasks() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.files.put(JOB,tasks()+"defaults('a', /* keep */ 'b')\r\n");
            var transaction=new EditTransaction(fixture.capture(),EditStyle.defaults());
            new DefaultsSourceEdits().set(transaction,new SemanticEditIntent.SetDefaults(JOB,List.of()));
            var plan=transaction.finish("Clear defaults",CancellationToken.NONE);
            assertTrue(plan.postimage().analysis().defaultTargets().isEmpty());
            assertTrue(plan.postimage().document(JOB).orElseThrow().declarations().stream().noneMatch(d->d.kind()==DeclarationKind.DEFAULTS));
            assertTrue(plan.postimages().get(JOB).startsWith(tasks()));
            assertTrue(plan.postimages().get(JOB).contains("/* keep */"));
            assertEquals(2,plan.postimage().analysis().symbols().size());
        }
    }

    @Test void projectDefaultsEditOnlySettingsAndPreserveAllQualifiedSelections() {
        try(var fixture=new EditTestWorkspace()) {
            URI sub=URI.create("file:/work/sub/job.groovy");
            fixture.files.put(sub,"task('b', Echo) { input='b' }");
            fixture.files.put(SETTINGS,"include 'sub'\r\ndefaults ':a' // selected\r\n");
            var before=fixture.capture();
            var transaction=new EditTransaction(before,EditStyle.defaults());
            new DefaultsSourceEdits().set(transaction,new SemanticEditIntent.SetDefaults(SETTINGS,List.of(id(":sub:b"),id(":a"))));
            var plan=transaction.finish("Project defaults",CancellationToken.NONE);
            assertEquals(java.util.Set.of(SETTINGS),plan.changes().keySet());
            assertEquals("include 'sub'\r\ndefaults ':sub:b', ':a' // selected\r\n",plan.postimages().get(SETTINGS));
            assertEquals(List.of(":sub:b",":a"),plan.postimage().analysis().defaultTargets());
            assertTrue(plan.preconditions().sources().containsKey(sub));
            assertEquals("EDIT_DEFAULTS_SCOPE",assertThrows(IllegalArgumentException.class,()->new DefaultsSourceEdits().set(new EditTransaction(before,EditStyle.defaults()),
                    new SemanticEditIntent.SetDefaults(JOB,List.of(id(":a"))))).getMessage());
        }
    }

    @Test void canAddMissingDefaultsAndRemoveAnExplicitlyDeselectedUnresolvedTarget() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.files.put(JOB,tasks());
            var transaction=new EditTransaction(fixture.capture(),EditStyle.defaults());
            new DefaultsSourceEdits().set(transaction,new SemanticEditIntent.SetDefaults(JOB,List.of(id("a"),id("b"))));
            var plan=transaction.finish("Add defaults",CancellationToken.NONE);
            assertEquals(tasks()+"defaults 'a', 'b'\r\n",plan.postimages().get(JOB));
            fixture.files.put(JOB,tasks()+"defaults 'a', 'missing'\r\n");
            var repair=new EditTransaction(fixture.capture(),EditStyle.defaults());
            new DefaultsSourceEdits().set(repair,new SemanticEditIntent.SetDefaults(JOB,List.of(id("a"))));
            var repaired=repair.finish("Remove missing target",CancellationToken.NONE);
            assertEquals(List.of("a"),repaired.postimage().analysis().defaultTargets());
            assertTrue(repaired.postimage().analysis().diagnostics().stream().noneMatch(d->d.severity()==Severity.ERROR));
        }
    }
}
