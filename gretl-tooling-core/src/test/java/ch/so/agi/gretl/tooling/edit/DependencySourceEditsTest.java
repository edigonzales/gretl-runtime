package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace;
import ch.so.agi.gretl.language.PlanSemantics;
import ch.so.agi.gretl.language.PlanSemantics.EdgeKind;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace.*;
import static org.junit.jupiter.api.Assertions.*;

class DependencySourceEditsTest {
    SymbolId id(String name){return new SymbolId(Namespace.TASK,name);}
    @Test void creatingAConsumerWithAnInitialResultBindingIncludesItsDataDependency() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.addResultOperations();
            fixture.files.put(JOB,"task('p', Source) {}\n");
            var transaction=new EditTransaction(fixture.capture(),EditStyle.defaults());
            new DeclarationSourceEdits().addTask(transaction,new SemanticEditIntent.AddTask(JOB,fixture.files.get(JOB).length(),"c","test.sink",
                    Map.of("input",new EditableValue.ResultFieldRef(id("p"),"file"))));
            var plan=transaction.finish("Add bound consumer",CancellationToken.NONE);
            assertTrue(plan.postimages().get(JOB).contains("task('c', Sink, dependsOn: 'p')"));
            assertTrue(plan.postimages().get(JOB).contains("input = result('p').file"));
            assertTrue(new PlanSemantics().dataPredecessors(plan.postimage().analysis().graph(),"c").contains("p"));
            assertEquals(1,plan.changes().get(JOB).size());
        }
    }

    @Test void disconnectAlsoProtectsDownstreamTransitiveResultAccess() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.addResultOperations();
            fixture.files.put(JOB,"task('p', Source) {}; action('middle', dependsOn:'p') {}; task('c', Sink, dependsOn:'middle') { input=result('p').file }; defaults 'c'");
            var transaction=new EditTransaction(fixture.capture(),EditStyle.defaults());
            assertEquals("EDIT_RESULT_DEPENDENCY",assertThrows(IllegalArgumentException.class,()->new DependencySourceEdits().remove(transaction,id("p"),id("middle"),EdgeKind.DEPENDS_ON)).getMessage());
        }
    }

    @Test void resultBindingAddsTheFieldAndDataDependencyAtomicallyWithoutChangingAfter() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.addResultOperations();
            String source="task('p', Source) {}; task('c', Sink, after:'p') { input=workPath('old') }; defaults 'c'";
            fixture.files.put(JOB,source);
            var transaction=new EditTransaction(fixture.capture(),EditStyle.defaults());
            new DependencySourceEdits().bind(transaction,new SemanticEditIntent.BindResult(id("p"),"file",id("c"),"input"));
            var plan=transaction.finish("Bind result",CancellationToken.NONE);
            assertEquals(2,plan.changes().get(JOB).size());
            assertEquals(2,plan.postimage().analysis().graph().edges().size());
            assertTrue(plan.postimage().analysis().graph().edges().stream().anyMatch(e->e.kind()==EdgeKind.AFTER));
            assertTrue(plan.postimage().analysis().graph().edges().stream().anyMatch(e->e.kind()==EdgeKind.DEPENDS_ON));
            var field=assertInstanceOf(PropertyValue.class,plan.postimage().analysis().unique(id("c")).orElseThrow().declaration().fields().getFirst().value());
            assertEquals("file",field.property());
            assertEquals("p",assertInstanceOf(LiteralValue.class,assertInstanceOf(CallValue.class,field.receiver()).arguments().getFirst()).value());
            assertEquals(source,fixture.files.get(JOB));
        }
    }

    @Test void aTransitiveDataChainIsEnoughForBindingAndNeedsNoExtraEdge() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.addResultOperations();
            fixture.files.put(JOB,"task('p', Source) {}; action('middle', dependsOn:'p') {}; task('c', Sink, dependsOn:'middle') { input=workPath('old') }; defaults 'c'");
            var before=fixture.capture();
            var transaction=new EditTransaction(before,EditStyle.defaults());
            new DependencySourceEdits().bind(transaction,new SemanticEditIntent.BindResult(id("p"),"file",id("c"),"input"));
            var plan=transaction.finish("Bind result",CancellationToken.NONE);
            assertEquals(1,plan.changes().get(JOB).size());
            assertEquals(before.analysis().graph().edges(),plan.postimage().analysis().graph().edges());
            assertTrue(new PlanSemantics().dataPredecessors(plan.postimage().analysis().graph(),"c").contains("p"));
        }
    }

    @Test void removingLastDataRightIsRejectedButRemovingAfterDoesNotRemoveDataRights() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.addResultOperations();
            fixture.files.put(JOB,"task('p', Source) {}; task('c', Sink, dependsOn:'p', after:'p') { input=result('p').file }; defaults 'c'");
            var before=fixture.capture();
            var edits=new DependencySourceEdits();
            assertEquals("EDIT_RESULT_DEPENDENCY",assertThrows(IllegalArgumentException.class,()->edits.remove(new EditTransaction(before,EditStyle.defaults()),id("p"),id("c"),EdgeKind.DEPENDS_ON)).getMessage());
            var transaction=new EditTransaction(before,EditStyle.defaults());
            edits.remove(transaction,id("p"),id("c"),EdgeKind.AFTER);
            var plan=transaction.finish("Remove after",CancellationToken.NONE);
            assertEquals(1,plan.postimage().analysis().graph().edges().size());
            assertEquals(EdgeKind.DEPENDS_ON,plan.postimage().analysis().graph().edges().getFirst().kind());
            assertTrue(plan.postimages().get(JOB).contains("input=result('p').file"));
        }
    }

    @Test void removingRedundantDirectDependencyKeepsCommentsAndTransitiveAccess() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.addResultOperations();
            fixture.files.put(JOB,"task('p', Source) {}; action('middle', dependsOn:'p') {}; task('c', Sink, dependsOn:['p', /* keep */ 'middle']) { input=result('p').file }; defaults 'c'");
            var transaction=new EditTransaction(fixture.capture(),EditStyle.defaults());
            new DependencySourceEdits().remove(transaction,id("p"),id("c"),EdgeKind.DEPENDS_ON);
            var plan=transaction.finish("Remove redundant dependency",CancellationToken.NONE);
            assertTrue(plan.postimages().get(JOB).contains("dependsOn:[ /* keep */ 'middle']"));
            assertTrue(new PlanSemantics().dataPredecessors(plan.postimage().analysis().graph(),"c").contains("p"));
        }
    }

    @Test void listInsertionPreservesCommentsAndAlreadyConnectedIsANoOp() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.addResultOperations();
            fixture.files.put(JOB,"task('p', Source) {}; task('q', Source) {}; task('text', Echo) { input='x' }; task('c', Sink, dependsOn:['p', // keep\n]) { input=workPath('old') }; defaults 'c'");
            var before=fixture.capture();
            var edits=new DependencySourceEdits();
            var unchanged=new EditTransaction(before,EditStyle.defaults());
            edits.add(unchanged,id("p"),id("c"),EdgeKind.DEPENDS_ON);
            assertTrue(unchanged.finish("No change",CancellationToken.NONE).noOp());
            var transaction=new EditTransaction(before,EditStyle.defaults());
            edits.add(transaction,id("q"),id("c"),EdgeKind.DEPENDS_ON);
            var plan=transaction.finish("Connect q",CancellationToken.NONE);
            assertTrue(plan.postimages().get(JOB).contains("'p', // keep\n    'q'\n]"));
            assertEquals(2,plan.postimage().analysis().graph().edges().size());
            assertEquals("EDIT_PORT_COMPATIBILITY",assertThrows(IllegalArgumentException.class,()->edits.bind(new EditTransaction(before,EditStyle.defaults()),
                    new SemanticEditIntent.BindResult(id("p"),"file",id("text"),"input"))).getMessage());
        }
    }
}
