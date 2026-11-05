package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.PlanSemantics.EdgeKind;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.edit.EditVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplyEditCompatibilityTest {
    private SymbolId task(String name){return new SymbolId(Namespace.TASK,name);}
    private void setup(Fixture f){
        var input=new InputDescriptor("input","Input",true,false,null,List.of(),"input",ValueShape.fromMetadata("path"),null,false,Map.of());
        var overwrite=new InputDescriptor("overwrite","Overwrite",false,false,null,List.of(),"overwrite",ValueShape.fromMetadata("boolean"),null,false,Map.of());
        f.catalog.mergeExplicitDescriptors(List.of(new OperationDescriptor("test.source","Source","Source","fixture",List.of(),List.of(new OutputDescriptor("file","File",ValueShape.fromMetadata("path"),false)),"example",false),
                new OperationDescriptor("test.sink","Sink","Sink","fixture",List.of(input,overwrite,
                        new InputDescriptor("items","Items",false,false,null,List.of(),"items",new ValueShape("ListOf","list",List.of(ValueShape.fromMetadata("path"))),null,false,Map.of()),
                        new InputDescriptor("options","Options",false,false,null,List.of(),"options",new ValueShape("MapOf","map",List.of(ValueShape.fromMetadata("text"),ValueShape.fromMetadata("path"))),null,false,Map.of())),List.of(),"example",true)));f.manager.onCatalogChanged();
        f.files.put(JOB,"// ä😀 keep\r\napply from: 'tasks/extra.groovy' // include unchanged\r\ntask('p', Source) {}\r\ntask('a', Sink, dependsOn:'p', after:'p') {\r\n input = file('data/input.txt')\r\n overwrite = true // local comment\r\n}\r\ndefaults 'a'\r\n");
        f.files.put(java.net.URI.create("file:/work/tasks/extra.groovy"),"throw new AssertionError('never execute or resolve')");
    }
    @Test void allGraphAndReferenceRoutesRejectPartialIncludesWithoutEdits(){
        try(var f=new Fixture()){
            setup(f);var before=f.capture();String original=f.files.get(JOB);var planner=new SemanticEditPlanner();
            var intents=List.<SemanticEditIntent>of(new SemanticEditIntent.RenameSymbol(task("a"),"renamed"),new SemanticEditIntent.DeleteTask(task("a")),
                    new SemanticEditIntent.AddDependency(task("p"),task("a"),EdgeKind.DEPENDS_ON),new SemanticEditIntent.RemoveDependency(task("p"),task("a"),EdgeKind.AFTER),
                    new SemanticEditIntent.BindResult(task("p"),"file",task("a"),"input"),
                    new SemanticEditIntent.SetTaskOption(task("a"),"dependsOn",new EditableValue.Sequence(List.of())),
                    new SemanticEditIntent.SetTaskOption(task("a"),"after",new EditableValue.Sequence(List.of())),
                    new SemanticEditIntent.SetField(task("a"),"input",new EditableValue.ResultFieldRef(task("p"),"file")),
                    new SemanticEditIntent.SetDefaults(JOB,List.of(task("a"))));
            for(var intent:intents){var result=planner.plan(before,intent,EditStyle.defaults(),CancellationToken.NONE);assertTrue(result.plan().isEmpty(),intent.toString());assertEquals(EditCapability.State.REJECTED,result.capability().state(),intent.toString());assertTrue(result.capability().reasons().stream().anyMatch(r->r.code().equals("EDIT_REFERENCE_COVERAGE")),intent+": "+result.capability());}
            assertEquals(original,f.files.get(JOB));
        }
    }
    @Test void resultFieldSetterUsesTheSameReferenceCoverageRuleAsBindResult(){
        try(var f=new Fixture()){
            setup(f);var result=new SemanticEditPlanner().plan(f.capture(),new SemanticEditIntent.SetField(task("a"),"input",new EditableValue.ResultFieldRef(task("p"),"file")),EditStyle.defaults(),CancellationToken.NONE);
            assertTrue(result.plan().isEmpty());assertEquals(EditCapability.State.REJECTED,result.capability().state());
        }
    }
    @Test void nestedReferenceValuesCannotBypassTheCoverageGuard(){
        try(var f=new Fixture()){
            setup(f);var before=f.capture();var ref=new EditableValue.ResultFieldRef(task("p"),"file");
            for(var intent:List.of(new SemanticEditIntent.SetField(task("a"),"items",new EditableValue.Sequence(List.of(ref))),
                    new SemanticEditIntent.SetField(task("a"),"options",new EditableValue.Mapping(Map.of("path",ref))))){
                var result=new SemanticEditPlanner().plan(before,intent,EditStyle.defaults(),CancellationToken.NONE);assertTrue(result.plan().isEmpty());assertEquals("EDIT_REFERENCE_COVERAGE",result.capability().reasons().getFirst().code());
            }
        }
    }
    @Test void parameterReferencesAlsoRequireKnownDeclarationCoverage(){
        try(var f=new Fixture()){
            setup(f);f.files.put(JOB,"parameter('path',type:'path',description:'Path')\r\n"+f.files.get(JOB));
            var result=new SemanticEditPlanner().plan(f.capture(),new SemanticEditIntent.SetField(task("a"),"input",new EditableValue.ParameterRef(new SymbolId(Namespace.PARAMETER,"path"))),EditStyle.defaults(),CancellationToken.NONE);
            assertTrue(result.plan().isEmpty());assertEquals("EDIT_REFERENCE_COVERAGE",result.capability().reasons().getFirst().code());
        }
    }
    @Test void aFormCannotHideGraphMutationBehindAnOtherwiseSafeLocalChange(){
        try(var f=new Fixture()){
            setup(f);var result=new SemanticEditPlanner().planConfiguration(f.capture(),task("a"),Map.of("overwrite",new EditableValue.Literal(false)),Set.of(),Map.of("dependsOn",new EditableValue.Sequence(List.of())),EditStyle.defaults(),CancellationToken.NONE);
            assertTrue(result.plan().isEmpty());assertEquals(EditCapability.State.REJECTED,result.capability().state());
        }
    }
    @Test void localScalarEditPreservesEveryOtherByteAndDoesNotCaptureFragments(){
        try(var f=new Fixture()){
            setup(f);String original=f.files.get(JOB);var before=f.capture();
            assertFalse(before.analysis().capture().project().sources().versions().containsKey(java.net.URI.create("file:/work/tasks/extra.groovy")));
            var result=new SemanticEditPlanner().plan(before,new SemanticEditIntent.SetField(task("a"),"overwrite",new EditableValue.Literal(false)),EditStyle.defaults(),CancellationToken.NONE);
            var plan=result.plan().orElseThrow();assertEquals(original.replace("overwrite = true","overwrite = false"),plan.postimages().get(JOB));assertEquals(Set.of(JOB),plan.changes().keySet());assertEquals(original,f.files.get(JOB));
        }
    }
    @Test void unknownExistingDefaultsCannotDisappearThroughASetter(){
        try(var f=new Fixture()){
            setup(f);f.files.put(JOB,f.files.get(JOB).replace("defaults 'a'","defaults 'a', 'fragmentTarget'"));String original=f.files.get(JOB);
            var result=new SemanticEditPlanner().plan(f.capture(),new SemanticEditIntent.SetDefaults(JOB,List.of(task("a"))),EditStyle.defaults(),CancellationToken.NONE);assertTrue(result.plan().isEmpty());assertEquals(original,f.files.get(JOB));
        }
    }
}
