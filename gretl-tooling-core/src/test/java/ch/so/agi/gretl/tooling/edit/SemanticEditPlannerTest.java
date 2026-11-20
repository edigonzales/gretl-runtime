package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace;
import ch.so.agi.gretl.tooling.workspace.DocumentStore.TextChange;
import ch.so.agi.gretl.language.PlanSemantics.EdgeKind;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace.*;
import static org.junit.jupiter.api.Assertions.*;

class SemanticEditPlannerTest {
    SymbolId id(String name) { return new SymbolId(Namespace.TASK,name); }

    @Test void allFieldAndFormPathsEnforceResultPortCompatibilityIncludingNestedValues() {
        for(String kind:List.of("Text","Opaque","Path","NullablePath","ListPath"))try(var fixture=new EditTestWorkspace()) {
            fixture.addResultOperations();
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
            var form=planner.planConfiguration(snapshot,id("c"),Map.of("input",value),Set.of(),Map.of(),EditStyle.defaults(),CancellationToken.NONE);
            for(var result:List.of(direct,form)) {
                assertEquals(compatible?EditCapability.State.ALLOWED:EditCapability.State.REJECTED,result.capability().state(),kind+": "+result.capability());
                if(compatible)assertTrue(result.plan().orElseThrow().postimages().get(JOB).contains("result('p').file"));
                else {assertTrue(result.plan().isEmpty());assertEquals("EDIT_PORT_COMPATIBILITY",result.capability().reasons().getFirst().code());}
            }
        }
    }

    @Test void aFormValidatesResultDependenciesInTheFinalState() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.addResultOperations();
            fixture.files.put(JOB,"task('p', Source) {}; task('c', Sink) { input=workPath('old') }");
            var snapshot=fixture.capture();var planner=new SemanticEditPlanner();
            var fields=Map.<String,EditableValue>of("input",new EditableValue.ResultFieldRef(id("p"),"file"));
            var valid=planner.planConfiguration(snapshot,id("c"),fields,Set.of(),Map.of("dependsOn",new EditableValue.Literal("p")),EditStyle.defaults(),CancellationToken.NONE);
            assertEquals(EditCapability.State.ALLOWED,valid.capability().state(),valid.capability().reasons().toString());
            assertTrue(valid.plan().orElseThrow().postimage().analysis().diagnostics().stream().noneMatch(d->d.severity()==ch.so.agi.gretl.tooling.semantic.SemanticModel.Severity.ERROR));
            var invalid=planner.planConfiguration(snapshot,id("c"),fields,Set.of(),Map.of(),EditStyle.defaults(),CancellationToken.NONE);
            assertEquals(EditCapability.State.REJECTED,invalid.capability().state());assertTrue(invalid.plan().isEmpty());
        }
    }

    @Test void oneFormConfirmationUsesSharedAnchorsWithoutReformattingOrPartialApplication() {
        try(var fixture=new EditTestWorkspace()) {
            addWideOperation(fixture);
            String source="// heading 😀\r\ntask([:], 'a', Wide) {\r\n  input='old' // retain\r\n}\r\n";
            fixture.files.put(JOB,source);var before=fixture.capture();var planner=new SemanticEditPlanner();
            var values = new LinkedHashMap<String, EditableValue>();
            values.put("second", new EditableValue.Literal("two"));
            values.put("first", new EditableValue.Literal("one"));
            var options = new LinkedHashMap<String, EditableValue>();
            options.put("timeout", new EditableValue.Literal("PT1S"));
            options.put("description", new EditableValue.Literal("Description"));
            var result=planner.planConfiguration(before,TASK,values,Set.of(),options,EditStyle.defaults(),CancellationToken.NONE);
            assertEquals(EditCapability.State.ALLOWED,result.capability().state(),result.capability().reasons().toString());
            var plan=result.plan().orElseThrow();String text=plan.postimages().get(JOB);
            assertEquals("// heading 😀\r\ntask([description: 'Description', timeout: 'PT1S'], 'a', Wide) {\r\n"
                    + "  input='old' // retain\r\n  first = 'one'\r\n  second = 'two'\r\n}\r\n", text);
            var reordered = planner.planConfiguration(before, TASK, values.reversed(), Set.of(), options.reversed(),
                    EditStyle.defaults(), CancellationToken.NONE).plan().orElseThrow();
            assertEquals(plan.changes(), reordered.changes());
            assertEquals(2,plan.changes().get(JOB).size(),"One header patch and one body insertion; untouched text stays outside the edits");
            assertTrue(SourceEditBuilder.verifyPreservation(before.source(JOB).orElseThrow(),text,plan.changes().get(JOB)));
            var repeated = planner.planConfiguration(plan.postimage(), TASK, values, Set.of(), options,
                    EditStyle.defaults(), CancellationToken.NONE).plan().orElseThrow();
            assertTrue(repeated.noOp());
            assertSame(plan.postimage(), repeated.postimage());
            var refused=planner.planConfiguration(before,TASK,values,Set.of(),Map.of("timeout",new EditableValue.Literal("invalid")),EditStyle.defaults(),CancellationToken.NONE);
            assertEquals(EditCapability.State.REJECTED,refused.capability().state());assertTrue(refused.plan().isEmpty());
            assertEquals(source,fixture.files.get(JOB));
        }
    }

    private void addWideOperation(EditTestWorkspace fixture) {
        var shape=ch.so.agi.gretl.tooling.catalog.OperationCatalog.ValueShape.fromMetadata("text");
        var fields=List.of("input","first","second","mode").stream().map(name->new ch.so.agi.gretl.tooling.catalog.OperationCatalog.InputDescriptor(name,name,name.equals("input"),false,null,List.<String>of(),"example",shape,null,false,Map.<String,String>of())).toList();
        fixture.catalog.mergeExplicitDescriptors(List.of(new ch.so.agi.gretl.tooling.catalog.OperationCatalog.OperationDescriptor("test.wide","Wide","Wide","fixture",fields,List.of(),"example",true)));
        fixture.manager.onCatalogChanged();
    }

    @Test void mixedFormEditsKeepOriginalCoordinatesAndBecomeStaleAfterABufferChange() {
        try (var fixture = new EditTestWorkspace()) {
            addWideOperation(fixture);
            String header = "\uFEFFtask('a', Wide, description:\"old\") {\r\n";
            String body = "  input=\"old\" // Zürich 😀\r\n  mode='FAST' // keep\r\n";
            String tail = "}\r\naction('neighbor') { println(\"\\u0061\") }\r\n";
            String source = header + body + tail;
            fixture.buffers.open(JOB, "gretl", 1, source);
            var before = fixture.capture();
            var planner = new SemanticEditPlanner();
            var result = planner.planConfiguration(before, TASK,
                    Map.of("input", new EditableValue.Literal("new\"$"), "first", new EditableValue.Literal("added")), Set.of("mode"),
                    Map.of("description", new EditableValue.Literal("changed"), "timeout", new EditableValue.Literal("PT1S")),
                    EditStyle.openDocuments(), CancellationToken.NONE);
            var plan = result.plan().orElseThrow(() -> new AssertionError(result.capability()));
            assertEquals("\uFEFFtask('a', Wide, description:\"changed\", timeout: 'PT1S') {\r\n"
                    + "  input=\"new\\\"\\$\" // Zürich 😀\r\n   // keep\r\n  first = 'added'\r\n" + tail, plan.postimages().get(JOB));
            assertTrue(planner.validate(plan, before).current());
            fixture.buffers.applyChanges(JOB, 2, List.of(TextChange.full(source)));
            assertFalse(planner.validate(plan, fixture.capture()).current());
        }
    }

    @Test void emptyAndRejectedFormsStillCheckTheirTargetAndLimits() {
        try (var fixture = new EditTestWorkspace()) {
            var before = fixture.capture();
            var planner = new SemanticEditPlanner();
            var empty = planner.planConfiguration(before, TASK, Map.of(), Set.of(), Map.of(),
                    EditStyle.openDocuments(), CancellationToken.NONE);
            assertEquals(EditCapability.State.NEEDS_OPEN_DOCUMENTS, empty.capability().state());
            assertEquals(List.of(JOB), empty.capability().requiredOpenDocuments());
            assertTrue(empty.plan().isEmpty());
            var invalid = planner.planConfiguration(before, TASK,
                    Map.of("input", new EditableValue.Literal("old"), "unknown", new EditableValue.Literal(false)),
                    Set.of(), Map.of(), EditStyle.openDocuments(), CancellationToken.NONE);
            assertEquals(EditCapability.State.REJECTED, invalid.capability().state());
            assertEquals("EDIT_UNKNOWN_FIELD", invalid.capability().reasons().getFirst().code());
            assertTrue(invalid.plan().isEmpty());
            fixture.buffers.open(JOB, "gretl", 1, fixture.files.get(JOB), false);
            var readOnly = planner.planConfiguration(fixture.capture(), TASK, Map.of("mode", new EditableValue.Literal("FAST")),
                    Set.of(), Map.of("description", new EditableValue.Literal("Changed")), EditStyle.defaults(), CancellationToken.NONE);
            assertEquals("EDIT_READONLY", readOnly.capability().reasons().getFirst().code());
            assertTrue(readOnly.plan().isEmpty());
            var tooMany = IntStream.range(0, 129).mapToObj(i -> "field" + i).collect(Collectors.toSet());
            assertEquals("EDIT_CONFIGURATION_LIMIT", assertThrows(IllegalArgumentException.class,
                    () -> planner.planConfiguration(before, TASK, Map.of(), tooMany, Map.of(), EditStyle.defaults(),
                            CancellationToken.NONE)).getMessage());
            assertThrows(CancellationException.class, () -> planner.planConfiguration(before, TASK,
                    Map.of("input", new EditableValue.Literal("new")), Set.of(), Map.of(), EditStyle.defaults(), () -> true));
        }
    }

    @Test void everyIntentUsesTheSameAppliedVerifiedPlanBoundary() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.addResultOperations();
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
        try(var fixture=new EditTestWorkspace()) {
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
