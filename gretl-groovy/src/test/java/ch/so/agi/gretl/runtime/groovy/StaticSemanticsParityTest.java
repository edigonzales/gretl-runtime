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
    private CatalogRepository catalog(){return catalog(TestGroovyOperations.echo());}
    private CatalogRepository catalog(ch.so.agi.gretl.runtime.api.OperationType<?,?> op){
        var inputs=op.inputs().stream().map(f->new InputDescriptor(f.name(),f.description(),f.required(),f.defaultValue().isPresent(),f.defaultValue().map(v->new DataValue("Text",v.toString(),List.of(),Map.of())).orElse(null),f.allowedValues(),f.example(),ValueShape.fromMetadata(f.valueType()),null,false,Map.<String,String>of())).toList();
        var outputs=op.outputs().stream().map(f->new OutputDescriptor(f.name(),f.description(),ValueShape.fromMetadata(f.valueType()),f.nullable())).toList();var codec=new CatalogJsonCodec();var catalog=new CatalogRepository();catalog.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(1,"test","1","",List.of("fixture"),List.of(new OperationDescriptor(op.id(),op.symbol(),op.description(),"fixture",inputs,outputs,op.example(),false)),"COMPLETE"))),null);assertEquals(CatalogJsonCodec.State.AVAILABLE,catalog.snapshot().result().state());return catalog;
    }
    private Analysis staticAnalysis(String text){return staticAnalysis(text,catalog());}
    private Analysis staticAnalysis(String text,CatalogRepository descriptors){
        URI root=URI.create("memory:/fixture"),job=URI.create("memory:/fixture/job.groovy");var store=new DocumentStore();store.open(job,"gretl",1,text);
        SourceRepository repository=new SourceRepository(){public SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation){return SourceRead.unavailable("SOURCE_MISSING",id);}public SourceListing listProjectEntries(URI id,ScanBudget budget,CancellationToken cancellation){throw new AssertionError("No scan required");}};
        try(var manager=new WorkspaceManager(store,descriptors)){manager.addRoot(root,repository);return new ProjectAnalyzer().analyze(manager.capture(new ProjectResolver.ProjectRequest(root,job),CancellationToken.NONE),CancellationToken.NONE);}
    }
    private static List<String> errors(Analysis analysis){return analysis.diagnostics().stream().filter(d->d.severity()==Severity.ERROR).map(Diagnostic::code).toList();}
    @Test void realGroovyRegistrationsAndRunnerSelectionMatchStaticGraph() throws Exception {
        var harness=new GroovyHarness(temp);
        for(String text:List.of(
            "def first=action('a') {}; action('b',dependsOn:first) {}; action('c',after:'a') {}; defaults 'b'; defaults 'c'",
            "action('a') {}; action('b',dependsOn:'a') {}; action('c',dependsOn:'b',after:'a') {}; defaults 'c'",
            "action('a') {}; action([dependsOn:'missing',dependsOn:'a',when:{parameter('notRead')},when:{true}],'b') {}; defaults 'b'",
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
        String dynamic="def key(){ 'description' }; parameter([(key()):'P'], 'p')";assertEquals(1,harness.loadSource("dynamic-parameter.groovy",dynamic).parameters().size());assertEquals(List.of(),errors(staticAnalysis(dynamic)));
        String inlineWhen="parameter('p',description:'P'); action('a',when:parameter('p')) {}";assertEquals("TOPLEVEL_PARAMETER_READ",assertThrows(GroovyDslException.class,()->harness.loadSource("inline-when.groovy",inlineWhen)).code());assertTrue(errors(staticAnalysis(inlineWhen)).contains("TOPLEVEL_PARAMETER_READ"));
        String read="parameter('p')";assertEquals("TOPLEVEL_PARAMETER_READ",assertThrows(GroovyDslException.class,()->harness.loadSource("read.groovy",read)).code());assertTrue(errors(staticAnalysis(read)).contains("TOPLEVEL_PARAMETER_READ"));
    }
    @Test void runtimeResultsConfirmDataRightsOutputMetadataAndActionFreedom() throws Exception {
        var harness=new GroovyHarness(temp);
        for(String helper:List.of("result","resultOptional")){
            String text="task('producer', Echo) { text='value' }; action('consumer',after:'producer') { "+helper+"('producer') }; defaults 'producer','consumer'";
            assertTrue(errors(staticAnalysis(text)).contains("RESULT_DEPENDENCY_REQUIRED"));assertEquals(RunStatus.FAILED,harness.run(harness.loadSource(helper+".groovy",text),Map.of()).status());
        }
        for(String text:List.of("task('producer', Echo) { text='value' }; action('middle',dependsOn:'producer') {}; action('consumer',dependsOn:'middle') { result('producer').class; result('producer').metaClass; result('producer').result }; defaults 'consumer'","action('producer') { [arbitrary:'value'] }; action('consumer',dependsOn:'producer') { result('producer').arbitrary }; defaults 'consumer'")){
            assertEquals(List.of(),errors(staticAnalysis(text)));assertEquals(RunStatus.SUCCEEDED,harness.run(harness.loadSource("valid-result.groovy",text),Map.of()).status());
        }
        String wrong="task('producer', Echo) { text='value' }; action('consumer',dependsOn:'producer') { result('producer').missing }; defaults 'consumer'";assertTrue(errors(staticAnalysis(wrong)).contains("UNKNOWN_OUTPUT"));var outcome=harness.run(harness.loadSource("wrong-output.groovy",wrong),Map.of());assertEquals("UNKNOWN_OUTPUT_FIELD",outcome.task("consumer").orElseThrow().failure().orElseThrow().diagnostic().orElseThrow().code());
    }
    @Test void missingRequiredInputsAreConditionalWhenTheRuntimeSkipsConfiguration() throws Exception {
        var harness=new GroovyHarness(temp);var descriptors=catalog(TestGroovyOperations.required());
        for(String condition:List.of("false","parameter('flag')")){
            String text="parameter('flag',description:'F',type:'boolean',defaultValue:false); task('t', RequiredText, when:{"+condition+"}) {}; defaults 't'";
            TestGroovyOperations.reset();var outcome=harness.run(harness.loadSource("conditional.groovy",text),Map.of());assertEquals(RunStatus.SUCCEEDED,outcome.status());assertEquals(TaskStatus.SKIPPED,outcome.task("t").orElseThrow().status());assertEquals(0,TestGroovyOperations.BUILDER_CREATIONS.get());assertEquals(0,TestGroovyOperations.HANDLER_CALLS.get());
            var analysis=staticAnalysis(text,descriptors);assertFalse(errors(analysis).contains("REQUIRED_INPUT"));assertTrue(analysis.diagnostics().stream().anyMatch(d->d.code().equals("REQUIRED_INPUT")&&d.severity()==Severity.WARNING));
        }
        String unconditional="task('t', RequiredText) {}; defaults 't'";assertEquals(RunStatus.FAILED,harness.run(harness.loadSource("unconditional.groovy",unconditional),Map.of()).status());assertTrue(errors(staticAnalysis(unconditional,descriptors)).contains("REQUIRED_INPUT"));
    }
    @Test void nullRequiredInputsAreDiagnosedWithoutExecutingOrAssumingAConverter() throws Exception {
        var harness=new GroovyHarness(temp);String rejected="task('t', RequiredText) { text=null }; defaults 't'";
        var outcome=harness.run(harness.loadSource("null-input.groovy",rejected),Map.of());assertEquals(RunStatus.FAILED,outcome.status());assertEquals("INVALID_CONFIGURATION_VALUE",outcome.task("t").orElseThrow().failure().orElseThrow().diagnostic().orElseThrow().code());
        assertTrue(staticAnalysis(rejected,catalog(TestGroovyOperations.required())).diagnostics().stream().anyMatch(d->d.code().equals("REQUIRED_INPUT")||d.code().equals("INPUT_TYPE")));
        // SPI converters may deliberately normalize null to a valid nonnull value.
        var input=new ch.so.agi.gretl.runtime.api.InputField<TestGroovyOperations.RequiredRequest,String>("text","text","Text",true,Optional.empty(),List.of(),"text=null",v->v==null?"fallback":v.toString(),(builder,value)->((TestGroovyOperations.RequiredBuilder)builder).setText(value));
        var type=new ch.so.agi.gretl.runtime.api.OperationType<TestGroovyOperations.RequiredRequest,TestGroovyOperations.EchoResult>("test.null-default","NullDefault","Null default",TestGroovyOperations.RequiredRequest.class,TestGroovyOperations.EchoResult.class,TestGroovyOperations.RequiredBuilder::new,()->(request,context)->new TestGroovyOperations.EchoResult(request.text()),List.of(input),List.of(),"task('t', NullDefault) { text=null }");
        String normalized="task('t', NullDefault) { text=null }; defaults 't'";assertEquals(RunStatus.SUCCEEDED,runCustom(type,normalized).status());var uncertain=staticAnalysis(normalized,catalog(type));assertEquals(List.of(),errors(uncertain));assertTrue(uncertain.diagnostics().stream().anyMatch(d->d.code().equals("REQUIRED_INPUT")&&d.severity()==Severity.WARNING));
        String coerced="task('t', NullDefault) { text=7 }; defaults 't'";assertEquals(RunStatus.SUCCEEDED,runCustom(type,coerced).status());var converted=staticAnalysis(coerced,catalog(type));assertEquals(List.of(),errors(converted));assertTrue(converted.diagnostics().stream().anyMatch(d->d.code().equals("INPUT_TYPE")&&d.severity()==Severity.WARNING));
    }
    public record RawHidden(String hidden) { }
    @Test void typedTaskRefsReturnRawResultsWhileStringRefsUseTheCatalogView() throws Exception {
        var output=new ch.so.agi.gretl.runtime.api.OutputField<RawHidden,String>("shown","text","Shown",false,RawHidden::hidden);
        var type=new ch.so.agi.gretl.runtime.api.OperationType<String,RawHidden>("test.raw","RawProbe","Raw probe",String.class,RawHidden.class,()->()->"request",()->(request,context)->new RawHidden("value"),List.of(),List.of(output),"task('producer', RawProbe) {}");
        for(String access:List.of("result(producer).hidden","result('producer').shown","resultOptional('producer').present","resultOptional('producer').empty","resultOptional(producer).get().hidden","resultOptional('producer').get().shown","result('producer').hidden")){
            String text="def producer=task('producer', RawProbe) {}; action('consumer',dependsOn:producer) { "+access+" }; defaults 'consumer'";boolean valid=!access.equals("result('producer').hidden");
            var outcome=runCustom(type,text);assertEquals(valid?RunStatus.SUCCEEDED:RunStatus.FAILED,outcome.status());assertEquals(valid,List.of().equals(errors(staticAnalysis(text,catalog(type)))),access);
            if(!valid)assertEquals("UNKNOWN_OUTPUT_FIELD",outcome.task("consumer").orElseThrow().failure().orElseThrow().diagnostic().orElseThrow().code());
        }
    }
    private JobOutcome runCustom(ch.so.agi.gretl.runtime.api.OperationType<?,?> type,String text) throws Exception {
        ch.so.agi.gretl.runtime.api.OperationProvider provider=()->List.of(type);var registry=OperationRegistry.fromProviders(List.of(provider));var source=temp.resolve("custom.groovy");java.nio.file.Files.writeString(source,text);var definition=new GroovyJobLoader().load(source,DefinitionEnvironment.of(registry,temp));return new SequentialJobRunner().run(definition,new ExecutionPlanner().plan(definition,List.of()),RunInput.builder().jobRoot(temp).startCwd(temp).workRoot(temp.resolve("work-custom")).build());
    }

    @Test void guardedInvocationsAndReferencesMatchActualControlFlowWithoutExecutingConditions() throws Exception {
        var harness=new GroovyHarness(temp);var descriptors=catalog();descriptors.mergeExplicitDescriptors(catalog(TestGroovyOperations.required()).snapshot().result().catalog().orElseThrow().operations());
        for(String text:List.of(
            "action('a') { if(false) { ctx.run(RequiredText) {} } }; defaults 'a'",
            "action('a') {}; cleanup('end') { if(false) { ctx.run(RequiredText) {} } }; defaults 'a'",
            "action('a') { if(true) { return 'ok' }; ctx.run(RequiredText) {} }; defaults 'a'",
            "action('a') { while(false) { ctx.run(RequiredText) {} } }; defaults 'a'",
            "action('a') { for(def i in []) { ctx.run(RequiredText) {} } }; defaults 'a'",
            "action('a') { switch(0) { case 1: ctx.run(RequiredText) {}; break; default: break } }; defaults 'a'",
            "action('a') { try { println('ok') } catch(Exception e) { parameter('unknown') } }; defaults 'a'",
            "task('t', Echo) { text='x'; if(false) { ctx.run(RequiredText) {} } }; defaults 't'",
            "task('p', Echo) { text='x' }; action('a') { if(false) { result('p').missing } }; defaults 'a'",
            "action('a') { if(false) { parameter('unknown') } }; defaults 'a'",
            "parameter('flag',description:'F',type:'boolean',defaultValue:false); action('a') { if(parameter('flag')) { ctx.run(RequiredText) {} } }; defaults 'a'",
            "action('a') { def unused={ ctx.run(RequiredText) {}; parameter('unknown') }; return 'ok' }; defaults 'a'",
            "action('a') { def value=false?parameter('unknown'):'ok'; return value }; defaults 'a'",
            "action('a') { def first=false&&parameter('unknown'); def second=true||parameter('unknown'); return first }; defaults 'a'")){
            var outcome=harness.run(harness.loadSource("guarded-flow.groovy",text),Map.of());assertEquals(RunStatus.SUCCEEDED,outcome.status(),text);assertEquals(List.of(),errors(staticAnalysis(text,descriptors)),text);
        }
        for(String body:List.of("ctx.run(RequiredText) {}","if(false) { println('skip') }; ctx.run(RequiredText) {}")){
            String text="action('a') { "+body+" }; defaults 'a'";assertEquals(RunStatus.FAILED,harness.run(harness.loadSource("unguarded-flow.groovy",text),Map.of()).status());assertTrue(errors(staticAnalysis(text,descriptors)).contains("REQUIRED_INPUT"));
        }
    }
    @Test void registrationsInsideEvaluatedContainersRemainPartialInsteadOfInventingMissingTasks() throws Exception {
        var harness=new GroovyHarness(temp);
        for(String expression:List.of("[action('hidden') {}]","[key:action('hidden') {}]","[[key:[action('hidden') {}]]]")){
            String text="def values="+expression+"; action('main',dependsOn:'hidden') {}; defaults 'main'";var definition=harness.loadSource("container.groovy",text);assertEquals(Set.of("hidden","main"),definition.taskIds());assertEquals(RunStatus.SUCCEEDED,harness.run(definition,Map.of()).status());
            var analysis=staticAnalysis(text);assertEquals(List.of(),errors(analysis));assertFalse(analysis.capabilities().declarationsComplete());assertFalse(analysis.capabilities().graphComplete());
        }
        var literal=staticAnalysis("def values=[1,[2,3],[flag:true]]; action('main') {}; defaults 'main'");assertTrue(literal.capabilities().declarationsComplete());assertTrue(literal.capabilities().graphComplete());
    }

}
