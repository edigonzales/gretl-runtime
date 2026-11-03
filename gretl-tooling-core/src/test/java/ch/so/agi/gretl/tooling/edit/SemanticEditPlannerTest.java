package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.PlanSemantics.EdgeKind;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.edit.EditVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class SemanticEditPlannerTest {
    SymbolId id(String name) { return new SymbolId(Namespace.TASK,name); }

    @Test void allFieldAndFormPathsEnforceResultPortCompatibilityIncludingNestedValues() {
        for(String kind:List.of("Text","Opaque","Path","NullablePath","ListPath"))try(var fixture=new Fixture()) {
            new DependencySourceEditsTest().catalog(fixture);
            var shape=new ch.so.agi.gretl.tooling.catalog.OperationCatalog.ValueShape(kind.equals("Text")?"Text":kind.equals("Opaque")?"Opaque":"Path",kind.equals("Text")?"text":kind.equals("Opaque")?"unknown":"path",List.of());
            var inputShape=kind.equals("ListPath")?new ch.so.agi.gretl.tooling.catalog.OperationCatalog.ValueShape("ListOf","list",List.of(shape)):shape;
            var input=new ch.so.agi.gretl.tooling.catalog.OperationCatalog.InputDescriptor("input","Input",true,false,null,List.of(),"input",inputShape,null,false,Map.of());
            var output=new ch.so.agi.gretl.tooling.catalog.OperationCatalog.OutputDescriptor("file","File",shape,kind.equals("NullablePath"));
            fixture.catalog.mergeExplicitDescriptors(List.of(
                    new ch.so.agi.gretl.tooling.catalog.OperationCatalog.OperationDescriptor("test.other","Other","Other","fixture",List.of(input),List.of(),"example",true),
                    new ch.so.agi.gretl.tooling.catalog.OperationCatalog.OperationDescriptor("test.nullable","Maybe","Maybe","fixture",List.of(),List.of(output),"example",false)));
            fixture.manager.onCatalogChanged();
            String producer=kind.equals("NullablePath")||kind.equals("Opaque")?"Maybe":"Source";
            String old=kind.equals("ListPath")?"[workPath('old')]":kind.equals("Text")?"'old'":"workPath('old')";
            fixture.files.put(JOB,"task('p', "+producer+") {}; task('c', Other, dependsOn:'p') { input="+old+" }");
            var snapshot=fixture.capture();var planner=new SemanticEditPlanner();
            EditableValue value=new EditableValue.ResultFieldRef(id("p"),"file");
            if(kind.equals("ListPath"))value=new EditableValue.Sequence(List.of(value));
            boolean compatible=kind.equals("Path")||kind.equals("ListPath");
            var direct=planner.plan(snapshot,new SemanticEditIntent.SetField(id("c"),"input",value),EditStyle.defaults(),CancellationToken.NONE);
            var form=planner.planConfiguration(snapshot,id("c"),Map.of("input",value),java.util.Set.of(),Map.of(),EditStyle.defaults(),CancellationToken.NONE);
            for(var result:List.of(direct,form)) {
                assertEquals(compatible?EditCapability.State.ALLOWED:EditCapability.State.REJECTED,result.capability().state(),kind+": "+result.capability());
                if(compatible)assertTrue(result.plan().orElseThrow().postimages().get(JOB).contains("result('p').file"));
                else {assertTrue(result.plan().isEmpty());assertEquals("EDIT_PORT_COMPATIBILITY",result.capability().reasons().getFirst().code());}
            }
        }
    }

    @Test void aFormMayChangeAResultAndItsDependencyTogetherButCannotPublishAnInvalidIntermediateState() {
        try(var fixture=new Fixture()) {
            new DependencySourceEditsTest().catalog(fixture);
            fixture.files.put(JOB,"task('p', Source) {}; task('c', Sink) { input=workPath('old') }");
            var snapshot=fixture.capture();var planner=new SemanticEditPlanner();
            var fields=Map.<String,EditableValue>of("input",new EditableValue.ResultFieldRef(id("p"),"file"));
            var valid=planner.planConfiguration(snapshot,id("c"),fields,java.util.Set.of(),Map.of("dependsOn",new EditableValue.Literal("p")),EditStyle.defaults(),CancellationToken.NONE);
            assertEquals(EditCapability.State.ALLOWED,valid.capability().state(),valid.capability().reasons().toString());
            assertTrue(valid.plan().orElseThrow().postimage().analysis().diagnostics().stream().noneMatch(d->d.severity()==ch.so.agi.gretl.tooling.semantic.SemanticModel.Severity.ERROR));
            var invalid=planner.planConfiguration(snapshot,id("c"),fields,java.util.Set.of(),Map.of(),EditStyle.defaults(),CancellationToken.NONE);
            assertEquals(EditCapability.State.REJECTED,invalid.capability().state());assertTrue(invalid.plan().isEmpty());
        }
    }

    @Test void oneFormConfirmationComposesSharedAnchorsWithoutReformattingOrPartialApplication() {
        try(var fixture=new Fixture()) {
            var shape=ch.so.agi.gretl.tooling.catalog.OperationCatalog.ValueShape.fromMetadata("text");
            var fields=List.of("input","first","second").stream().map(name->new ch.so.agi.gretl.tooling.catalog.OperationCatalog.InputDescriptor(name,name,name.equals("input"),false,null,List.<String>of(),"example",shape,null,false,Map.<String,String>of())).toList();
            fixture.catalog.mergeExplicitDescriptors(List.of(new ch.so.agi.gretl.tooling.catalog.OperationCatalog.OperationDescriptor("test.wide","Wide","Wide","fixture",fields,List.of(),"example",true)));
            fixture.manager.onCatalogChanged();
            String source="// heading 😀\r\ntask([:], 'a', Wide) {\r\n  input='old' // retain\r\n}\r\n";
            fixture.files.put(JOB,source);var before=fixture.capture();var planner=new SemanticEditPlanner();
            var values=Map.<String,EditableValue>of("first",new EditableValue.Literal("one"),"second",new EditableValue.Literal("two"));
            var options=Map.<String,EditableValue>of("description",new EditableValue.Literal("Description"),"timeout",new EditableValue.Literal("PT1S"));
            var result=planner.planConfiguration(before,TASK,values,java.util.Set.of(),options,EditStyle.defaults(),CancellationToken.NONE);
            assertEquals(EditCapability.State.ALLOWED,result.capability().state(),result.capability().reasons().toString());
            var plan=result.plan().orElseThrow();String text=plan.postimages().get(JOB);
            assertTrue(text.contains("first = 'one'\r\n"));assertTrue(text.contains("second = 'two'\r\n"));
            assertTrue(text.startsWith("// heading 😀\r\n"));assertTrue(text.contains("input='old' // retain\r\n"));
            assertEquals(2,plan.changes().get(JOB).size(),"One header patch and one body insertion; untouched text stays outside the edits");
            assertTrue(SourceEditBuilder.verifyPreservation(before.source(JOB).orElseThrow(),text,plan.changes().get(JOB)));
            assertTrue(planner.planConfiguration(plan.postimage(),TASK,values,java.util.Set.of(),options,EditStyle.defaults(),CancellationToken.NONE).plan().orElseThrow().noOp());
            var refused=planner.planConfiguration(before,TASK,values,java.util.Set.of(),Map.of("timeout",new EditableValue.Literal("invalid")),EditStyle.defaults(),CancellationToken.NONE);
            assertEquals(EditCapability.State.REJECTED,refused.capability().state());assertTrue(refused.plan().isEmpty());
            assertEquals(source,fixture.files.get(JOB));
        }
    }

    @Test void everyIntentUsesTheSameAppliedVerifiedPlanBoundary() {
        try(var fixture=new Fixture()) {
            new DependencySourceEditsTest().catalog(fixture);
            String job="task('a', Echo) { input='old'; mode='FAST' }\n"
                    +"task('p', Source) {}\ntask('c', Sink, after:'p') { input=workPath('old') }\naction('spare') {}\n";
            fixture.files.put(JOB,job);
            var before=fixture.capture();
            var intents=List.<SemanticEditIntent>of(
                    new SemanticEditIntent.AddTask(JOB,job.length(),"newTask","test.echo",Map.of("input",new EditableValue.Literal("value"))),
                    new SemanticEditIntent.AddAction(JOB,job.length(),"newAction"),
                    new SemanticEditIntent.AddParameter(JOB,job.length(),new SemanticEditIntent.ParameterDeclaration("arg","Argument","text",false,new EditableValue.Literal("x"))),
                    new SemanticEditIntent.SetField(id("a"),"input",new EditableValue.Literal("new")),
                    new SemanticEditIntent.RemoveField(id("a"),"mode"),
                    new SemanticEditIntent.SetTaskOption(id("a"),"description",new EditableValue.Literal("Description")),
                    new SemanticEditIntent.AddDependency(id("p"),id("a"),EdgeKind.DEPENDS_ON),
                    new SemanticEditIntent.RemoveDependency(id("p"),id("c"),EdgeKind.AFTER),
                    new SemanticEditIntent.BindResult(id("p"),"file",id("c"),"input"),
                    new SemanticEditIntent.RenameSymbol(id("a"),"renamed"),
                    new SemanticEditIntent.DeleteTask(id("spare")),
                    new SemanticEditIntent.SetDefaults(JOB,List.of(id("a"),id("c"))));
            var planner=new SemanticEditPlanner();
            for(var intent:intents) {
                var result=planner.plan(before,intent,EditStyle.defaults(),CancellationToken.NONE);
                assertEquals(EditCapability.State.ALLOWED,result.capability().state(),intent+": "+result.capability().reasons());
                var plan=result.plan().orElseThrow();
                assertFalse(plan.noOp(),intent.toString());
                assertTrue(planner.validate(plan,before).current());
                assertFalse(planner.validate(plan,plan.postimage()).current());
                assertFalse(plan.postimages().get(JOB).equals(job));
                assertEquals(job,fixture.files.get(JOB));
            }
        }
    }

    @Test void refusalsNoOpsOpenRequirementsAndCancellationRemainDistinct() {
        try(var fixture=new Fixture()) {
            var before=fixture.capture();
            var planner=new SemanticEditPlanner();
            var same=new SemanticEditIntent.SetField(TASK,"input",new EditableValue.Literal("old"));
            assertTrue(planner.plan(before,same,EditStyle.defaults(),CancellationToken.NONE).plan().orElseThrow().noOp());
            var open=planner.plan(before,same,EditStyle.openDocuments(),CancellationToken.NONE);
            assertEquals(EditCapability.State.NEEDS_OPEN_DOCUMENTS,open.capability().state());
            assertEquals(List.of(JOB),open.capability().requiredOpenDocuments());
            assertTrue(open.plan().isEmpty());
            var refused=planner.plan(before,new SemanticEditIntent.SetTaskOption(TASK,"timeout",new EditableValue.Literal("nonsense")),EditStyle.defaults(),CancellationToken.NONE);
            assertEquals(EditCapability.State.REJECTED,refused.capability().state());
            assertTrue(refused.plan().isEmpty());
            assertThrows(CancellationException.class,()->planner.plan(before,same,EditStyle.defaults(),()->true));
        }
    }
}
