package ch.so.agi.gretl.tooling.semantic;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.*;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import static ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import static ch.so.agi.gretl.tooling.workspace.ProjectResolver.*;
import static ch.so.agi.gretl.tooling.workspace.SourceRepository.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectAnalyzerTest {
    static final URI ROOT=URI.create("file:/work"),JOB=URI.create("file:/work/job.groovy");
    static InputDescriptor input(String name,String type,boolean required,List<String> values){return new InputDescriptor(name,"Input",required,false,null,values,"example",ValueShape.fromMetadata(type),null,false,Map.of());}
    static CatalogRepository catalog(){var repository=new CatalogRepository();var codec=new CatalogJsonCodec();var operations=List.of(
        new OperationDescriptor("test.echo","Echo","Test","fixture",List.of(input("input","text",true,List.of()),input("mode","text",false,List.of("COPY","MOVE"))),List.of(new OutputDescriptor("file","File",ValueShape.fromMetadata("path"),false)),"example",false),
        new OperationDescriptor("test.duckdb","DuckDbSqlExecutor","Test","fixture",List.of(input("sqlFiles","list",true,List.of()),input("sqlParameters","map",false,List.of()),input("sqlParameterSets","list",false,List.of())),List.of(),"example",true),
        new OperationDescriptor("test.db2db","Db2Db","Test","fixture",List.of(input("transfers","list",true,List.of()),input("sqlParameters","map",false,List.of()),input("sqlParameterSets","list",false,List.of())),List.of(),"example",true),
        new OperationDescriptor("test.sql","SqlExecutor","Test","fixture",List.of(input("scripts","list",false,List.of())),List.of(),"example",true));
        var load=repository.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(1,"test","test","",List.of("fixture"),operations,"COMPLETE"))),null);assertEquals(CatalogJsonCodec.State.AVAILABLE,load.result().state());return repository;}
    static Analysis analyze(String text){return analyze(Map.of(JOB,text),JOB,new DocumentStore(),catalog(),"en");}
    static Analysis analyze(Map<URI,String> files,URI document,DocumentStore store,CatalogRepository catalogs,String language){
        var prohibit=new AtomicBoolean();SourceRepository repository=new SourceRepository(){
            public SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation){assertFalse(prohibit.get(),"Semantic analysis must use only captured sources");var text=files.get(id);return text==null?SourceRead.unavailable("SOURCE_MISSING",id):SourceRead.found(new SourceText(id,text,null,"DISK",true,text));}
            public SourceListing listProjectEntries(URI root,ScanBudget budget,CancellationToken cancellation){throw new AssertionError("No project scan needed");}
        };
        try(var manager=new WorkspaceManager(store,catalogs)){manager.addRoot(ROOT,repository);manager.updateConfiguration(ROOT,new WorkspaceManager.Configuration(language,Map.of()));var captured=manager.capture(new ProjectRequest(ROOT,document),CancellationToken.NONE);prohibit.set(true);return new ProjectAnalyzer().analyze(captured,CancellationToken.NONE);}
    }
    static List<String> codes(Analysis analysis){return analysis.diagnostics().stream().map(Diagnostic::code).toList();}
    @Test void bindingsNamespacesAliasesAndTransitiveResultsShareTheDataGraph(){
        var analysis=analyze("""
            parameter('source', description:'Path', type:'path')
            def first=task('source', Echo) { input='ok' }
            action('middle', dependsOn:first) { result(first).file }
            task('end', Echo, dependsOn:'middle') { input=result('source').file }
            cleanup('source') { parameter('source') }
            defaults 'end'
            """);
        assertEquals(List.of(),codes(analysis),analysis.diagnostics().toString());assertTrue(analysis.capabilities().graphComplete());assertEquals(5,analysis.symbols().size());assertEquals(List.of("end"),analysis.defaultTargets());
        assertEquals(2,analysis.graph().edges().size());assertTrue(analysis.references().stream().allMatch(r->r.resolution()==Resolution.RESOLVED));
        assertThrows(UnsupportedOperationException.class,()->analysis.symbols().clear());assertThrows(UnsupportedOperationException.class,()->analysis.symbols().values().iterator().next().clear());
    }
    @Test void afterNeverGrantsResultRightsAndOptionalHasTheSameDependencyRule(){
        var analysis=analyze("task('a', Echo) { input='x' }; action('b', after:'a') { result('a').file; resultOptional('a') }; defaults 'a','b'");
        assertEquals(2,Collections.frequency(codes(analysis),"RESULT_DEPENDENCY_REQUIRED"));assertFalse(codes(analysis).contains("UNKNOWN_OUTPUT"));
        var action=analyze("action('a') { return [anything:42] }; action('b', dependsOn:'a') { result('a').arbitrary }");assertFalse(codes(action).contains("UNKNOWN_OUTPUT"));
        var wrong=analyze("task('a', Echo) { input='x' }; action('b', dependsOn:'a') { result('a').class; result('a').metaClass; result('a').missing }; cleanup('last') { result('a') }");assertTrue(codes(wrong).containsAll(List.of("UNKNOWN_OUTPUT","RESULT_SCOPE")));assertEquals(1,Collections.frequency(codes(wrong),"UNKNOWN_OUTPUT"));
    }
    @Test void duplicatesRemainAmbiguousAndUnknownRegistrationSuppressesCertainMissingReferences(){
        var duplicate=analyze("action('a') {}; task('a', Echo) { input='x' }; action('b', dependsOn:'a') {}");assertEquals(2,Collections.frequency(codes(duplicate),"DUPLICATE_TASK_ID"));assertTrue(codes(duplicate).contains("AMBIGUOUS_REFERENCE"));assertFalse(duplicate.capabilities().graphComplete());assertEquals(2,duplicate.symbols().get(new SymbolId(Namespace.TASK,"a")).size());
        var partial=analyze("names.each { name -> action(name) {} }; action('b', dependsOn:'generated') { result('generated') }");assertFalse(codes(partial).contains("UNKNOWN_TASK_REFERENCE"));assertFalse(partial.capabilities().declarationsComplete());assertTrue(codes(partial).contains("PARTIAL_ANALYSIS"));
        var dynamic=analyze("action('a') {}; action('b', dependsOn:selectTarget()) {}");assertTrue(codes(dynamic).contains("DYNAMIC_REFERENCE"));assertFalse(dynamic.capabilities().graphComplete());
    }
    @Test void projectIncludesResolveQualifiedNamesAndIgnoreScriptDefaults(){
        var settings=URI.create("file:/work/settings.gretl");var sub=URI.create("file:/work/load/job.groovy");var analysis=analyze(Map.of(settings,"include 'load'\ndefaults ':load:run'",JOB,"action('start') {}; defaults 'ignored'",sub,"parameter('p',description:'P'); action('run',dependsOn:':start') { parameter('p'); result(':start') }; defaults 'alsoIgnored'"),sub,new DocumentStore(),catalog(),"en");
        assertEquals(List.of(":load:run"),analysis.defaultTargets());assertTrue(analysis.symbols().containsKey(new SymbolId(Namespace.PARAMETER,":load:p")));assertFalse(codes(analysis).contains("UNKNOWN_TASK_REFERENCE"));assertEquals(List.of(),codes(analysis));
    }
    @Test void allStandaloneDefaultsAreCollectedWithoutFlatteningListsAndCyclesAreDetected(){
        var analysis=analyze("action('a') {}; action('b') {}; defaults 'a'; defaults 'b'; defaults 'a'");assertEquals(List.of("a","b"),analysis.defaultTargets());assertTrue(codes(analysis).contains("DUPLICATE_DEFAULT_TARGET"));
        var list=analyze("action('a') {}; defaults(['a'])");assertTrue(codes(list).contains("DYNAMIC_REFERENCE"));assertTrue(list.defaultTargets().isEmpty());
        var data=analyze("action('a', dependsOn:'b') {}; action('b', dependsOn:'a') {}");assertTrue(codes(data).contains("DEPENDENCY_CYCLE"));
        var mixed=analyze("action('a', after:'b') {}; action('b', dependsOn:'a') {}; defaults 'b'");assertTrue(codes(mixed).contains("PLAN_CYCLE"));
    }
    @Test void parameterDefaultsDescriptionsTypesAndActualLongReadsAreCheckedConservatively(){
        var valid=analyze("parameter('n',description:'N',type:'INTEGER',defaultValue:-5); parameter('large',description:'N',type:'integer',defaultValue:'999999999999999999999999'); parameter('b',description:'B',type:'boolean',required:true,defaultValue:null); action('a') { parameter('n',Long) }");assertEquals(List.of(),codes(valid));
        var dynamic=analyze("parameter('dynamic',description:'P',required:true,defaultValue:lookup())");assertFalse(codes(dynamic).contains("PARAMETER_REQUIRED_DEFAULT"));
        var invalid=analyze("parameter('read'); parameter('n',description:' ',type:'integer',defaultValue:5G); parameter('b',description:'B',type:'boolean',required:true,defaultValue:'TRUE'); action('a') { parameter('n',Integer); parameter('unknown') }");assertTrue(codes(invalid).containsAll(List.of("TOPLEVEL_PARAMETER_READ","PARAMETER_DESCRIPTION","PARAMETER_DEFAULT","PARAMETER_REQUIRED_DEFAULT","PARAMETER_READ_TYPE","UNKNOWN_PARAMETER")));
    }
    @Test void catalogOutageIsVisibleWithoutFalseUnknownOperationsAndFieldsUseCoverage(){
        var unavailable=analyze(Map.of(JOB,"task('a', Echo) { impossible='x' }"),JOB,new DocumentStore(),new CatalogRepository(),"fr");assertTrue(codes(unavailable).contains("CATALOG_UNAVAILABLE"));assertFalse(codes(unavailable).contains("UNKNOWN_OPERATION"));assertFalse(codes(unavailable).contains("UNKNOWN_INPUT"));assertEquals("Operation catalog is unavailable or incomplete.",unavailable.diagnostics().getFirst().message());
        var fields=analyze("task('a', Echo) { typo='x'; mode='invalid' }; task('b', Echo) { configureSomehow() }");assertTrue(codes(fields).containsAll(List.of("UNKNOWN_INPUT","INPUT_ENUM","REQUIRED_INPUT")));assertEquals(1,Collections.frequency(codes(fields),"REQUIRED_INPUT"));
        var shadowed=analyze("def Echo=chooseType(); task('a', Echo) { typo='x' }; action('b') { ctx.run(Echo) { typo='x' } }");assertFalse(codes(shadowed).contains("UNKNOWN_INPUT"));
    }
    @Test void sqlReadsDirtyJobRelativeSourcesAndMatchesActualGlobalTemplateSemantics(){
        var sub=URI.create("file:/work/sub/job.groovy");var sql=URI.create("file:/work/sub/sql/query.sql");var store=new DocumentStore();store.open(sql,"sql",7,"select '${value}' -- ${missing}\n/* ${comment} */ ${bad space}");
        var analysis=analyze(Map.of(sub,"task('sql', DuckDbSqlExecutor) { sqlFiles=[file('sql/query.sql')]; sqlParameters=[value:'ok', comment:'ok'] }; task('plain', SqlExecutor) { scripts=['select \\${notATemplate}'] }",sql,"select 1"),sub,store,catalog(),"de");
        assertEquals(1,Collections.frequency(codes(analysis),"SQL_PARAMETER_MISSING"),analysis.diagnostics().toString());var problem=analysis.diagnostics().stream().filter(d->d.code().equals("SQL_PARAMETER_MISSING")).findFirst().orElseThrow();assertEquals(sql,problem.anchor().documentId());assertEquals("${missing}",analysis.capture().project().sources().texts().get(sql).slice(problem.anchor().span()));assertTrue(problem.message().startsWith("SQLvorlage"));
    }
    @Test void innerInvocationsUseTheirOwnCatalogWithoutRegisteringTasksAndReservedFieldsAreRejected(){
        var analysis=analyze("task('producer', Echo) { input='x' }; action('consumer',dependsOn:'producer',when:{ parameter('enabled') }) { ctx.run(Echo) { input=result('producer').file; owner='bad' } }; parameter('enabled',description:'E',type:'boolean')");
        assertEquals(2,analysis.graph().nodes().size());assertTrue(codes(analysis).contains("RESERVED_CONFIGURATION_FIELD"));assertFalse(codes(analysis).contains("UNKNOWN_INPUT"));assertFalse(codes(analysis).contains("RESULT_DEPENDENCY_REQUIRED"));
        var cleanup=analyze("cleanup('end') { ctx.run(Echo) { input='x' } }");assertTrue(codes(cleanup).contains("INNER_INVOCATION_SCOPE"));
        var local=analyze("task('a', Echo) { def input='local'; mode='COPY' }");assertEquals(1,Collections.frequency(codes(local),"REQUIRED_INPUT"));assertFalse(codes(local).contains("UNKNOWN_INPUT"));
    }
    @Test void pathsDistinguishJobResourcesWorkAndExternalParametersWithoutGuessingTheirValues(){
        var analysis=analyze("parameter('external',description:'P',type:'path'); action('a') { file('sql/input.sql'); workPath('output'); parameter('external'); path('raw-relative') }");
        assertEquals(List.of(PathKind.JOB_RESOURCE,PathKind.WORK,PathKind.EXTERNAL_PARAMETER,PathKind.DYNAMIC),analysis.paths().stream().map(PathReference::kind).toList());assertEquals(URI.create("file:/work/sql/input.sql"),analysis.paths().getFirst().resourceId());assertNull(analysis.paths().get(1).resourceId());assertNull(analysis.paths().get(2).resourceId());
    }
    @Test void sqlSourcesWithEqualNamesStayInTheirSubprojectsAndMissingMapsRemainErrors(){
        var settings=URI.create("file:/work/settings.gretl");var a=URI.create("file:/work/a/job.groovy");var b=URI.create("file:/work/b/job.groovy");var sqlA=URI.create("file:/work/a/sql/update.sql");var sqlB=URI.create("file:/work/b/sql/update.sql");
        String job="task('sql', DuckDbSqlExecutor) { sqlFiles=[file('sql/update.sql')] }";
        var analysis=analyze(Map.of(settings,"include 'a','b'",a,job,b,job,sqlA,"select ${onlyA}",sqlB,"select ${onlyB}"),a,new DocumentStore(),catalog(),"en");
        assertEquals(Set.of(sqlA,sqlB),analysis.diagnostics().stream().filter(d->d.code().equals("SQL_PARAMETER_MISSING")).map(d->d.anchor().documentId()).collect(java.util.stream.Collectors.toSet()));assertEquals(2,analysis.symbols().size());
        var dynamic=analyze(Map.of(JOB,"task('sql', DuckDbSqlExecutor) { sqlFiles=[file('sql/query.sql')]; sqlParameters=loadMap() }",URI.create("file:/work/sql/query.sql"),"select ${unknown}"),JOB,new DocumentStore(),catalog(),"en");assertTrue(codes(dynamic).contains("SQL_PARAMETERS_DYNAMIC"));assertFalse(codes(dynamic).contains("SQL_PARAMETER_MISSING"));
    }
    @Test void parameterSetsCheckEveryExecutionAndTheUnchangedBufferSnapshotSurvivesAnalysis(){
        var sql=URI.create("file:/work/sql/query-without-extension");var analysis=analyze(Map.of(JOB,"task('sql', DuckDbSqlExecutor) { sqlFiles=[file('sql/query-without-extension')]; sqlParameterSets=[[name:'yes'],[name:null]] }",sql,"select ${name}"),JOB,new DocumentStore(),catalog(),"en");assertEquals(1,Collections.frequency(codes(analysis),"SQL_PARAMETER_MISSING"));
        assertThrows(java.util.concurrent.CancellationException.class,()->new ProjectAnalyzer().analyze(analysis.capture(),()->true));
    }

    @Test void explicitOptionMapsUseOnlyWinningClosuresWhileDynamicKeysStayPartial(){
        var explicit=analyze("action('a') {}; action([dependsOn:'missing', dependsOn:'a',when:{parameter('missing')},when:{true}], 'b') {}; defaults 'b'");assertEquals(List.of(),codes(explicit));assertEquals(1,explicit.graph().edges().size());assertEquals("a",explicit.graph().edges().getFirst().fromId());
        var dynamic=analyze("def key(){ 'description' }; parameter([(key()):'P'], 'p')");assertFalse(codes(dynamic).contains("PARAMETER_DESCRIPTION"));assertFalse(dynamic.capabilities().declarationsComplete());
        var evaluated=analyze("parameter('p',description:'P'); action([description:parameter('p'), description:'last'], 'a') {}");assertTrue(codes(evaluated).contains("TOPLEVEL_PARAMETER_READ"));
        var inlineWhen=analyze("parameter('p',description:'P'); action('a',when:parameter('p')) {}");assertTrue(codes(inlineWhen).contains("TOPLEVEL_PARAMETER_READ"));
        var nested=analyze("parameter('p',description:'P', defaultValue:parameter('other'))");assertTrue(codes(nested).contains("TOPLEVEL_PARAMETER_READ"));
    }
    @Test void db2dbTransferSqlUsesTheSameRuntimeParametersAndOnlyTheLastSourceAssignment(){
        var sql=URI.create("file:/work/sql/query");var analysis=analyze(Map.of(JOB,"task('copy', Db2Db) { transfers=[[sqlFile:file('not-used.sql'),targetTable:'t']]; transfers=[[sqlFile:file('sql/query'),targetTable:'t']]; sqlParameters=[known:'yes'] }",sql,"select ${known}, ${missing}"),JOB,new DocumentStore(),catalog(),"en");
        assertEquals(1,Collections.frequency(codes(analysis),"SQL_PARAMETER_MISSING"));assertFalse(codes(analysis).contains("SQL_SOURCE_NOT_INSPECTED"));assertFalse(analysis.capture().project().sources().versions().containsKey(URI.create("file:/work/not-used.sql")));
    }

    @Test void requiredDiagnosticsDistinguishConditionalExecutionAndUnknownNullConversion(){
        var conditional=analyze("parameter('flag',description:'F',type:'boolean',defaultValue:false); task('a', Echo,when:{false}) {}; task('b', Echo,when:{parameter('flag')}) {}; task('c', Echo) {}");
        assertEquals(1,conditional.diagnostics().stream().filter(d->d.code().equals("REQUIRED_INPUT")&&d.severity()==Severity.ERROR).count());assertEquals(2,conditional.diagnostics().stream().filter(d->d.code().equals("REQUIRED_INPUT")&&d.severity()==Severity.WARNING).count());
        var explicitNull=analyze("task('a', Echo) { input=null }");assertTrue(explicitNull.diagnostics().stream().anyMatch(d->d.code().equals("REQUIRED_INPUT")&&d.severity()==Severity.WARNING&&d.message().contains("null")));
        var raw=analyze("def a=task('a', Echo) { input='x' }; action('b',dependsOn:a) { result(a).rawProperty; resultOptional('a').present; result('a').missing }");assertEquals(1,Collections.frequency(codes(raw),"UNKNOWN_OUTPUT"));assertFalse(codes(raw).contains("RESULT_DEPENDENCY_REQUIRED"));
    }

    @Test void partialCatalogsLimitFieldCertaintyWithoutSuppressingKnownReservedNames(){
        var incomplete=catalog();var original=incomplete.snapshot().result().catalog().orElseThrow();var codec=new CatalogJsonCodec();incomplete.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(1,original.runtimeVersion(),original.dslVersion(),"",original.producers(),original.operations(),"PARTIAL"))),null);
        var analysis=analyze(Map.of(JOB,"task('a', Echo) { typo='x'; owner='bad' }"),JOB,new DocumentStore(),incomplete,"en");
        assertTrue(analysis.diagnostics().stream().anyMatch(d->d.code().equals("UNKNOWN_INPUT")&&d.severity()==Severity.WARNING));assertTrue(analysis.diagnostics().stream().anyMatch(d->d.code().equals("REQUIRED_INPUT")&&d.severity()==Severity.WARNING));assertTrue(analysis.diagnostics().stream().anyMatch(d->d.code().equals("RESERVED_CONFIGURATION_FIELD")&&d.severity()==Severity.ERROR));assertFalse(analysis.capabilities().catalogAvailable());
    }

    @Test void conditionalBodiesKeepRuntimeWarningsWhileRegistrationAndWhenEvaluationRemainDefinite(){
        var sql=URI.create("file:/work/query.sql");var analysis=analyze(Map.of(JOB,"task('a', Echo) { input='x' }; task('b', Echo,when:{false}) { typo='x'; input=result('a').missing; parameter('unknown') }; task('sql', DuckDbSqlExecutor,when:{false}) { sqlFiles=[file('query.sql')] }",sql,"select ${missing}"),JOB,new DocumentStore(),catalog(),"en");
        for(String code:List.of("UNKNOWN_INPUT","RESULT_DEPENDENCY_REQUIRED","UNKNOWN_OUTPUT","UNKNOWN_PARAMETER","SQL_PARAMETER_MISSING"))assertTrue(analysis.diagnostics().stream().anyMatch(d->d.code().equals(code)&&d.severity()==Severity.WARNING),code+analysis.diagnostics());
        assertFalse(analysis.diagnostics().stream().anyMatch(d->d.severity()==Severity.ERROR));
        var header=analyze("task('a', Echo,dependsOn:'missing',when:{parameter('unknown')}) { input='x' }");assertTrue(header.diagnostics().stream().anyMatch(d->d.code().equals("UNKNOWN_TASK_REFERENCE")&&d.severity()==Severity.ERROR));assertTrue(header.diagnostics().stream().anyMatch(d->d.code().equals("UNKNOWN_PARAMETER")&&d.severity()==Severity.ERROR));
    }

    @Test void guardedReferencesAndInnerConfigurationsAreUncertainWithoutWeakeningFollowingCode(){
        var analysis=analyze("action('a') { if(false) { parameter('maybe'); ctx.run(Echo) {} }; parameter('definite') }");
        assertTrue(analysis.diagnostics().stream().anyMatch(d->d.code().equals("UNKNOWN_PARAMETER")&&d.severity()==Severity.WARNING));assertTrue(analysis.diagnostics().stream().anyMatch(d->d.code().equals("UNKNOWN_PARAMETER")&&d.severity()==Severity.ERROR));assertTrue(analysis.diagnostics().stream().anyMatch(d->d.code().equals("REQUIRED_INPUT")&&d.severity()==Severity.WARNING));assertFalse(analysis.capabilities().executionComplete());
        var task=analyze("task('a', Echo) { input='x'; if(false) { ctx.run(Echo) {} } }");assertFalse(task.capabilities().fieldAssignmentsComplete());assertFalse(task.capabilities().executionComplete());assertTrue(task.capabilities().referencesComplete());
        var cleanup=analyze("cleanup('end') { if(false) { ctx.run(Echo) {} } }");assertTrue(cleanup.diagnostics().stream().anyMatch(d->d.code().equals("INNER_INVOCATION_SCOPE")&&d.severity()==Severity.WARNING));
        var registration=analyze("def hidden=[action('hidden') {}]; task('a', Echo) { typo='x' }");assertFalse(registration.capabilities().declarationsComplete());assertTrue(registration.diagnostics().stream().anyMatch(d->d.code().equals("UNKNOWN_INPUT")&&d.severity()==Severity.WARNING));
    }
    @Test void guardedSqlAndUncertainAssignmentsDoNotClaimProvenRuntimeParameterFailures(){
        var sql=URI.create("file:/work/query.sql");
        for(String text:List.of("action('a') { if(false) { ctx.run(DuckDbSqlExecutor) { sqlFiles=[file('query.sql')] } } }","task('a', DuckDbSqlExecutor) { sqlFiles=[file('query.sql')]; sqlParameters=[name:'value']; return; sqlParameters=[:] }")){
            var analysis=analyze(Map.of(JOB,text,sql,"select ${name}"),JOB,new DocumentStore(),catalog(),"en");assertTrue(analysis.diagnostics().stream().anyMatch(d->d.code().equals("SQL_PARAMETER_MISSING")&&d.severity()==Severity.WARNING),analysis.diagnostics().toString());assertFalse(analysis.diagnostics().stream().anyMatch(d->d.severity()==Severity.ERROR));assertFalse(analysis.capabilities().executionComplete());
        }
        var incomplete=catalog();var original=incomplete.snapshot().result().catalog().orElseThrow();var codec=new CatalogJsonCodec();incomplete.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(1,original.runtimeVersion(),original.dslVersion(),"",original.producers(),original.operations(),"PARTIAL"))),null);
        var partial=analyze(Map.of(JOB,"task('p', Echo) { input='x' }; action('a',dependsOn:'p') { result('p').unknown }"),JOB,new DocumentStore(),incomplete,"en");assertTrue(partial.diagnostics().stream().anyMatch(d->d.code().equals("UNKNOWN_OUTPUT")&&d.severity()==Severity.WARNING));
    }

}
