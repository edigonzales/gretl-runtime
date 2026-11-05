package ch.so.agi.gretl.tooling.semantic;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.workspace.*;
import ch.so.agi.gretl.tooling.graph.*;
import ch.so.agi.gretl.tooling.edit.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import static org.junit.jupiter.api.Assertions.*;

class IncludeSemanticsTest {
    @TempDir Path root;
    Path write(String path,String text)throws Exception{var file=root.resolve(path);Files.createDirectories(file.getParent());Files.writeString(file,text);return file;}
    AnalysisSnapshot analyze(String entry)throws Exception{
        try(var manager=new WorkspaceManager(new DocumentStore(),ProjectAnalyzerTest.catalog())){
            manager.addRoot(root.toUri(),new FileSourceRepository(root.toUri()));
            return AnalysisSnapshot.of(new ProjectAnalyzer().analyze(manager.capture(new ProjectResolver.ProjectRequest(root.toUri(),root.resolve(entry).toUri()),CancellationToken.NONE),CancellationToken.NONE));
        }
    }
    List<String> errors(AnalysisSnapshot s){return s.analysis().diagnostics().stream().filter(d->d.severity()==Severity.ERROR).map(Diagnostic::code).toList();}
    @Test void staticIncludesAreCompleteAndRegisterDepthFirstWithOriginalSources()throws Exception{
        write("job.groovy","task('first',Echo){input='first'}\napply from:'tasks/middle.groovy'\ntask('last',Echo,dependsOn:'middle'){input=result('middle').file}\ndefaults 'last'");
        var middle=write("tasks/middle.groovy","parameter('p',description:'P',defaultValue:'x')\napply from:'nested.groovy'\ntask('middle',Echo,dependsOn:'nested'){input=parameter('p')}");
        write("tasks/nested.groovy","task('nested',Echo,dependsOn:'first'){input=result('first').file}");
        var s=analyze("job.groovy");assertEquals(List.of(),errors(s));
        assertTrue(s.analysis().capabilities().declarationsComplete());assertTrue(s.analysis().capabilities().referencesComplete());
        assertEquals(List.of("first","nested","middle","last"),s.analysis().graph().nodes().stream().map(ch.so.agi.gretl.language.PlanSemantics.Node::id).toList());
        assertEquals(middle.toUri(),s.analysis().unique(new SymbolId(Namespace.TASK,"middle")).orElseThrow().anchor().documentId());
        var graph=new JobGraphProjector().project(s,JobGraphProjector.GraphOptions.defaults(),CancellationToken.NONE);
        assertEquals(JobGraph.Status.COMPLETE,graph.status());assertFalse(graph.nodes().stream().anyMatch(n->n.kind()==JobGraph.NodeKind.DYNAMIC_CODE));
        assertEquals(3,s.analysis().graph().edges().size());
        var preview=new TargetPreviewProjector().project(s,List.of(),CancellationToken.NONE);assertTrue(preview.available());assertEquals(List.of("first","nested","middle","last"),preview.orderedSymbols());
        var form=new GraphFormProjector().project(s,graph,CancellationToken.NONE);
        var node=graph.nodes().stream().filter(n->n.title().equals("middle")).findFirst().orElseThrow();
        assertEquals(new SymbolId(Namespace.PARAMETER,"p"),form.nodes().get(node.id()).fields().get("input").value().symbol());
        assertEquals(EditCapability.State.REJECTED,new SemanticEditPlanner().plan(s,new SemanticEditIntent.RenameSymbol(new SymbolId(Namespace.TASK,"middle"),"renamed"),new EditStyle("    ",false),CancellationToken.NONE).capability().state());
    }
    @Test void sharedAliasesResourcesAndSqlDiagnosticsKeepTheirApplication()throws Exception{
        write("settings.gretl","include 'north','south'");
        for(String side:List.of("north","south"))write(side+"/job.groovy","apply from:'../shared/tasks.groovy'");
        var shared=write("shared/tasks.groovy","def source=task('source',Echo){input='x'}\ntask('consumer',Echo,dependsOn:source){input=result(source).file}\ntask('sql',DuckDbSqlExecutor){sqlFiles=[file('sql/input.sql')]}");
        write("north/sql/input.sql","select ${missing}");write("south/sql/input.sql","select 1");
        var s=analyze("north/job.groovy");
        assertEquals(List.of("SQL_PARAMETER_MISSING"),errors(s));
        assertEquals(":north",s.analysis().diagnostics().stream().filter(d->d.code().equals("SQL_PARAMETER_MISSING")).findFirst().orElseThrow().application().ownerScope());
        assertEquals(6,s.analysis().symbols().size());assertTrue(s.analysis().references().stream().allMatch(r->r.resolution()==Resolution.RESOLVED));
        assertEquals(Set.of(root.resolve("north/sql/input.sql").toUri(),root.resolve("south/sql/input.sql").toUri()),s.analysis().paths().stream().map(PathReference::resourceId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(shared.toUri(),s.analysis().unique(new SymbolId(Namespace.TASK,":south:consumer")).orElseThrow().anchor().documentId());
        var graph=new JobGraphProjector().project(s,JobGraphProjector.GraphOptions.defaults(),CancellationToken.NONE);
        for(var node:graph.nodes())if(node.symbol()!=null)assertNotNull(node.parentId(),node.toString());
    }
    @Test void aliasesDoNotExportAndAfterDoesNotGrantResultRights()throws Exception{
        write("job.groovy","apply from:'a.groovy'\ntask('bad',Echo,dependsOn:local){input='x'}");
        write("a.groovy","def local=task('producer',Echo){input='x'}\ntask('consumer',Echo,after:'producer'){input=result('producer').file}");
        var s=analyze("job.groovy");assertTrue(s.analysis().references().stream().anyMatch(r->r.resolution()==Resolution.DYNAMIC));
        write("job.groovy","apply from:'a.groovy'");
        assertTrue(errors(analyze("job.groovy")).contains("RESULT_DEPENDENCY_REQUIRED"));
    }
    @Test void resolvedIncludesDoNotEraseUnknownControlFlowOrRecovery()throws Exception{
        write("f.groovy","task('f',Echo){input='x'}");
        for(String code:List.of("return\n","throw new AssertionError('never')\n","if(flag){task('hidden',Echo){input='x'}}\n","def helper(){ task('hidden',Echo){input='x'} }; helper()\n")){
            write("job.groovy",code+"apply from:'f.groovy'");var s=analyze("job.groovy");assertFalse(s.analysis().capabilities().declarationsComplete(),code);
        }
        write("job.groovy","apply from:'f.groovy'");write("f.groovy","return\ntask('unreachable',Echo){input='x'}");
        assertFalse(analyze("job.groovy").analysis().capabilities().declarationsComplete());
        write("job.groovy","apply from:'f.groovy'\ntask('broken',Echo){");assertFalse(analyze("job.groovy").analysis().capabilities().declarationsComplete());
    }

    @Test void navigationOutlineAndSharedQueriesUseOriginalSelectedApplications()throws Exception{
        write("settings.gretl","include 'north','south'");
        for(String side:List.of("north","south"))write(side+"/job.groovy","apply from:'../shared/tasks.groovy'");
        String text="task('source',Echo){input='x'}\ntask('consumer',Echo,dependsOn:'source'){input=result('source').file}";
        var file=write("shared/tasks.groovy",text);var s=analyze("north/job.groovy");var queries=new ch.so.agi.gretl.tooling.query.LanguageQueries();
        int reference=text.indexOf("'source'",text.indexOf("consumer"))+2;
        var ambiguous=new ch.so.agi.gretl.tooling.query.QueryContext(s,file.toUri(),reference);
        assertTrue(ambiguous.ambiguous());assertTrue(queries.definitions(ambiguous).isEmpty());assertTrue(queries.hover(ambiguous).isEmpty());
        assertEquals(2,queries.documentSymbols(ambiguous).size());
        for(var app:s.analysis().capture().project().includeClosure().applications())if(app.id().sourceDocument().equals(file.toUri())){
            var selected=new ch.so.agi.gretl.tooling.query.QueryContext(s,file.toUri(),reference,app.id());
            assertEquals(1,queries.definitions(selected).size());assertEquals(file.toUri(),queries.definitions(selected).getFirst().documentId());
            assertEquals(app.id().ownerScope()+":source",queries.hover(selected).orElseThrow().title());
            assertEquals(3,queries.references(selected,true).locations().size());
            assertTrue(queries.complete(selected).stream().anyMatch(p->p.label().equals("source")));
            var entry=s.source(app.id().entryDocument()).orElseThrow();var include=new ch.so.agi.gretl.tooling.query.QueryContext(s,app.id().entryDocument(),entry.text().indexOf("tasks")+1);
            assertEquals(file.toUri(),queries.definitions(include).getFirst().documentId());assertEquals(file.toUri(),queries.documentLinks(include).getFirst().target());
            assertTrue(queries.documentSymbols(include).isEmpty());
        }
    }
    @Test void includePathCompletionUsesCapturedSafeListingsAndPreservesQuoteAndEscapes()throws Exception{
        write("tasks/one.groovy","// one");write("tasks/two.txt","not groovy");Files.createDirectories(root.resolve("tasks/nested"));
        Files.createSymbolicLink(root.resolve("tasks/link.groovy"),root.resolve("tasks/one.groovy"));
        var queries=new ch.so.agi.gretl.tooling.query.LanguageQueries();
        for(String text:List.of("apply from:'tasks/on'","apply from:\"tasks/on\"","apply from:'tasks/on","apply from:'ta\\u0073ks/on'")){
            var job=write("job.groovy",text);var s=analyze("job.groovy");int caret=text.indexOf("/on")+3;
            var context=new ch.so.agi.gretl.tooling.query.QueryContext(s,job.toUri(),caret);var proposals=queries.complete(context);
            assertEquals(List.of("one.groovy"),proposals.stream().map(p->p.label()).toList(),text+" "+s.analysis().capture().project().sources().pathListings());
            assertFalse(queries.completionIncomplete(context));
            var p=proposals.getFirst();assertEquals(text.indexOf("/on")+1,p.replacement().startUtf16());
            assertEquals(text.endsWith("on")?"one.groovy'":"one.groovy",p.newText());
            if(text.endsWith("on"))assertFalse(s.analysis().capabilities().declarationsComplete());
        }
        var job=write("job.groovy","apply from:'tasks/'");var s=analyze("job.groovy");
        var proposals=queries.complete(new ch.so.agi.gretl.tooling.query.QueryContext(s,job.toUri(),"apply from:'tasks/".length()));
        assertEquals(Set.of("nested/","one.groovy"),proposals.stream().map(p->p.label()).collect(java.util.stream.Collectors.toSet()));
        for(String text:List.of("// apply from:'tasks/on","def text=\"apply from:'tasks/on\"","receiver.apply from:'tasks/on","if(flag){\napply from:'tasks/on")){
            write("job.groovy",text);s=analyze("job.groovy");assertTrue(queries.complete(new ch.so.agi.gretl.tooling.query.QueryContext(s,job.toUri(),text.length())).stream().noneMatch(p->p.kind().equals("FILE")),text);
        }
    }
    @Test void unsupportedPathListingIsExplicitlyIncompleteAndQueriesDoNotPerformIo()throws Exception{
        String text="apply from:'tasks/new";var job=write("job.groovy",text);var reads=new java.util.concurrent.atomic.AtomicBoolean(true);
        var files=new FileSourceRepository(root.toUri());SourceRepository repository=new SourceRepository(){
            public SourceRead read(java.net.URI id,ReadPolicy policy,CancellationToken cancellation){assertTrue(reads.get());return files.read(id,policy,cancellation);}
            public SourceListing listProjectEntries(java.net.URI id,ScanBudget budget,CancellationToken cancellation){assertTrue(reads.get());return files.listProjectEntries(id,budget,cancellation);}
        };
        try(var manager=new WorkspaceManager(new DocumentStore(),ProjectAnalyzerTest.catalog())){
            manager.addRoot(root.toUri(),repository);var captured=manager.capture(new ProjectResolver.ProjectRequest(root.toUri(),job.toUri()),CancellationToken.NONE);reads.set(false);
            var s=AnalysisSnapshot.of(new ProjectAnalyzer().analyze(captured,CancellationToken.NONE));var context=new ch.so.agi.gretl.tooling.query.QueryContext(s,job.toUri(),text.length());
            var queries=new ch.so.agi.gretl.tooling.query.LanguageQueries();assertTrue(queries.complete(context).isEmpty());assertTrue(queries.completionIncomplete(context));
        }
    }
}
