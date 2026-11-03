package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.language.PlanSemantics;
import ch.so.agi.gretl.runtime.runner.*;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.*;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.semantic.*;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import ch.so.agi.gretl.tooling.workspace.WorkspaceManager;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import static ch.so.agi.gretl.tooling.workspace.SourceRepository.*;
import static org.junit.jupiter.api.Assertions.*;

/** The runtime loads only trusted fixture strings; the static pass has no runtime dependency. */
class StaticSemanticsParityTest {
    @TempDir Path temp;
    private CatalogRepository catalog(){
        var op=TestGroovyOperations.echo();var inputs=op.inputs().stream().map(f->new InputDescriptor(f.name(),f.description(),f.required(),f.defaultValue().isPresent(),f.defaultValue().map(v->new DataValue("Text",v.toString(),List.of(),Map.of())).orElse(null),f.allowedValues(),f.example(),ValueShape.fromMetadata(f.valueType()),null,false,Map.<String,String>of())).toList();
        var outputs=op.outputs().stream().map(f->new OutputDescriptor(f.name(),f.description(),ValueShape.fromMetadata(f.valueType()),f.nullable())).toList();var codec=new CatalogJsonCodec();var catalog=new CatalogRepository();catalog.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(1,"test","1","",List.of("fixture"),List.of(new OperationDescriptor(op.id(),op.symbol(),op.description(),"fixture",inputs,outputs,op.example(),false)),"COMPLETE"))),null);assertEquals(CatalogJsonCodec.State.AVAILABLE,catalog.snapshot().result().state());return catalog;
    }
    private Analysis staticAnalysis(String text){
        URI root=URI.create("memory:/fixture"),job=URI.create("memory:/fixture/job.groovy");var store=new DocumentStore();store.open(job,"gretl",1,text);
        SourceRepository repository=new SourceRepository(){public SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation){return SourceRead.unavailable("SOURCE_MISSING",id);}public SourceListing listProjectEntries(URI id,ScanBudget budget,CancellationToken cancellation){throw new AssertionError("No scan required");}};
        try(var manager=new WorkspaceManager(store,catalog())){manager.addRoot(root,repository);return new ProjectAnalyzer().analyze(manager.capture(new ProjectResolver.ProjectRequest(root,job),CancellationToken.NONE),CancellationToken.NONE);}
    }
    private static List<String> errors(Analysis analysis){return analysis.diagnostics().stream().filter(d->d.severity()==Severity.ERROR).map(Diagnostic::code).toList();}
    @Test void realGroovyRegistrationsAndRunnerSelectionMatchStaticGraph() throws Exception {
        var harness=new GroovyHarness(temp);
        for(String text:List.of(
            "def first=action('a') {}; action('b',dependsOn:first) {}; action('c',after:'a') {}; defaults 'b'; defaults 'c'",
            "action('a') {}; action('b',dependsOn:'a') {}; action('c',dependsOn:'b',after:'a') {}; defaults 'c'",
            "action('outsideA',after:'outsideB') {}; action('outsideB',after:'outsideA') {}; action('selected') {}; defaults 'selected'")){
            var definition=harness.loadSource("fixture.groovy",text);var staticModel=staticAnalysis(text);assertEquals(List.of(),errors(staticModel));assertEquals(definition.taskIds(),staticModel.symbols().keySet().stream().filter(k->k.namespace()==Namespace.TASK).map(SymbolId::qualifiedName).collect(java.util.stream.Collectors.toSet()));assertEquals(definition.defaultTargets(),staticModel.defaultTargets());
            var runtime=new ExecutionPlanner().plan(definition,List.of());var pure=new PlanSemantics().plan(staticModel.graph(),List.of(),staticModel.defaultTargets());assertEquals(runtime.selectedTasks().stream().map(TaskDefinition::id).toList(),pure.orderedIds());assertEquals(runtime.dataPredecessors(),pure.dataPredecessors());
        }
    }
    @Test void actualPlannerRejectsTheSameUnknownAndCyclicEdgesEvenOutsideTargets() throws Exception {
        var harness=new GroovyHarness(temp);
        var fixtures=Map.of("action('a',dependsOn:'b') {}; action('b',dependsOn:'a') {}; action('selected') {}; defaults 'selected'","DEPENDENCY_CYCLE","action('a',after:'missing') {}; action('selected') {}; defaults 'selected'","UNKNOWN_TASK_REFERENCE","action('a',after:'b') {}; action('b',dependsOn:'a') {}; defaults 'b'","PLAN_CYCLE");
        for(var entry:fixtures.entrySet()){
            if(entry.getValue().equals("UNKNOWN_TASK_REFERENCE")){assertEquals("UNKNOWN_TASK_REFERENCE",assertThrows(DefinitionException.class,()->harness.loadSource("bad-graph.groovy",entry.getKey())).code());assertTrue(errors(staticAnalysis(entry.getKey())).contains(entry.getValue()));continue;}
            var definition=harness.loadSource("bad-graph.groovy",entry.getKey());var failure=assertThrows(PlanException.class,()->new ExecutionPlanner().plan(definition,List.of()));assertEquals(entry.getValue(),failure.code());assertTrue(errors(staticAnalysis(entry.getKey())).contains(failure.code()));
        }
    }
    @Test void declaredParameterDefaultsMatchRuntimeIncludingNegativeIntegersAndNullDefaults() throws Exception {
        var harness=new GroovyHarness(temp);
        for(String expression:List.of("type:'integer',defaultValue:-5","type:'integer',defaultValue:'999999999999999999999'","type:'boolean',required:true,defaultValue:null","type:'',defaultValue:'text'")){
            String text="parameter('p',description:'P',"+expression+"); action('a') {}; defaults 'a'";assertEquals(1,harness.loadSource("parameter.groovy",text).parameters().size());assertEquals(List.of(),errors(staticAnalysis(text)),text);
        }
        for(String expression:List.of("type:'integer',defaultValue:5G","type:'boolean',required:true,defaultValue:true","type:'boolean',defaultValue:'TRUE'")){
            String text="parameter('p',description:'P',"+expression+"); action('a') {}; defaults 'a'";assertThrows(RuntimeException.class,()->harness.loadSource("invalid-parameter.groovy",text));assertFalse(errors(staticAnalysis(text)).isEmpty(),text);
        }
        String read="parameter('p')";assertEquals("TOPLEVEL_PARAMETER_READ",assertThrows(GroovyDslException.class,()->harness.loadSource("read.groovy",read)).code());assertTrue(errors(staticAnalysis(read)).contains("TOPLEVEL_PARAMETER_READ"));
    }
    @Test void runtimeResultsConfirmDataRightsOutputMetadataAndActionFreedom() throws Exception {
        var harness=new GroovyHarness(temp);
        for(String helper:List.of("result","resultOptional")){
            String text="task('producer', Echo) { text='value' }; action('consumer',after:'producer') { "+helper+"('producer') }; defaults 'producer','consumer'";
            assertTrue(errors(staticAnalysis(text)).contains("RESULT_DEPENDENCY_REQUIRED"));assertEquals(RunStatus.FAILED,harness.run(harness.loadSource(helper+".groovy",text),Map.of()).status());
        }
        for(String text:List.of("task('producer', Echo) { text='value' }; action('middle',dependsOn:'producer') {}; action('consumer',dependsOn:'middle') { result('producer').result }; defaults 'consumer'","action('producer') { [arbitrary:'value'] }; action('consumer',dependsOn:'producer') { result('producer').arbitrary }; defaults 'consumer'")){
            assertEquals(List.of(),errors(staticAnalysis(text)));assertEquals(RunStatus.SUCCEEDED,harness.run(harness.loadSource("valid-result.groovy",text),Map.of()).status());
        }
        String wrong="task('producer', Echo) { text='value' }; action('consumer',dependsOn:'producer') { result('producer').missing }; defaults 'consumer'";assertTrue(errors(staticAnalysis(wrong)).contains("UNKNOWN_OUTPUT"));var outcome=harness.run(harness.loadSource("wrong-output.groovy",wrong),Map.of());assertEquals("UNKNOWN_OUTPUT_FIELD",outcome.task("consumer").orElseThrow().failure().orElseThrow().diagnostic().orElseThrow().code());
    }
}
