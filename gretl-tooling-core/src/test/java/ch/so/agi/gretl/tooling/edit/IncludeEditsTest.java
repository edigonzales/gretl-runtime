package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.*;
import ch.so.agi.gretl.tooling.semantic.*;
import ch.so.agi.gretl.tooling.workspace.*;
import ch.so.agi.gretl.language.PlanSemantics.EdgeKind;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import static ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import static org.junit.jupiter.api.Assertions.*;

class IncludeEditsTest {
    @TempDir Path root;
    final AtomicBoolean io=new AtomicBoolean(true);
    CatalogRepository currentCatalog;
    Path write(String file,String text)throws Exception{var path=root.resolve(file);Files.createDirectories(path.getParent());Files.writeString(path,text);return path;}
    SymbolId task(String name){return new SymbolId(Namespace.TASK,name);}
    CatalogRepository catalog(){
        var catalog=new CatalogRepository();var codec=new CatalogJsonCodec();
        var input=new InputDescriptor("input","Input",true,false,null,List.of(),"input",ValueShape.fromMetadata("path"),null,false,Map.of());
        var operation=new OperationDescriptor("test.echo","Echo","Echo","fixture",List.of(input),List.of(new OutputDescriptor("file","File",ValueShape.fromMetadata("path"),false)),"example",false);
        var loaded=catalog.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(1,"test","test","",List.of("fixture"),List.of(operation),"COMPLETE"))),null);assertEquals(CatalogJsonCodec.State.AVAILABLE,loaded.result().state(),loaded.result().problems().toString());return catalog;
    }
    WorkspaceManager manager(boolean listingComplete)throws Exception{
        var files=new FileSourceRepository(root.toUri());currentCatalog=catalog();var manager=new WorkspaceManager(new DocumentStore(),currentCatalog);
        manager.addRoot(root.toUri(),new SourceRepository(){
            public SourceRead read(URI id,ReadPolicy policy,CancellationToken token){assertTrue(io.get(),"Postimages must not do host IO");return files.read(id,policy,token);}
            public SourceListing listProjectEntries(URI directory,ScanBudget budget,CancellationToken token){assertTrue(io.get());var found=files.listProjectEntries(directory,budget,token);return new SourceListing(found.entries(),found.issues(),found.complete()&&listingComplete);}
            public List<SourceIssue> checkIncludeTraversal(IncludeTraversal path,ReadPolicy policy,CancellationToken token){assertTrue(io.get());return files.checkIncludeTraversal(path,policy,token);}
            public PathListing listIncludePaths(URI directory,ReadPolicy policy,CancellationToken token){assertTrue(io.get());return files.listIncludePaths(directory,policy,token);}
        });return manager;
    }
    AnalysisSnapshot capture(WorkspaceManager manager)throws Exception{io.set(true);var capture=manager.capture(new ProjectResolver.ProjectRequest(root.toUri(),root.resolve("job.groovy").toUri()),CancellationToken.NONE);io.set(false);return AnalysisSnapshot.of(new ProjectAnalyzer().analyze(capture,CancellationToken.NONE));}
    PlannedEdit allowed(AnalysisSnapshot snapshot,SemanticEditIntent intent){var result=new SemanticEditPlanner().plan(snapshot,intent,EditStyle.defaults(),CancellationToken.NONE);assertEquals(EditCapability.State.ALLOWED,result.capability().state(),result.capability().toString());return result.plan().orElseThrow();}
    void denied(AnalysisSnapshot snapshot,SemanticEditIntent intent,String code){var result=new SemanticEditPlanner().plan(snapshot,intent,EditStyle.defaults(),CancellationToken.NONE);assertTrue(result.plan().isEmpty());assertEquals(code,result.capability().reasons().getFirst().code());}
    @Test void finalizerGraphSetterAndBatchRespectSharedDeclaringOwnerButAllowLocalOwner()throws Exception{
        write("settings.gretl","include 'north','south'\ndefaults ':north:local'");
        for(String side:List.of("north","south"))write(side+"/job.groovy","apply from:'../shared/tasks.groovy'\naction('local'){}\n");
        write("shared/tasks.groovy","action('owner',finalizedBy:'release'){}\naction('release'){}\n");
        try(var manager=manager(true)){
            var before=capture(manager);
            denied(before,new SemanticEditIntent.AddDependency(task(":north:owner"),task(":north:local"),EdgeKind.FINALIZED_BY),"EDIT_SHARED_SOURCE");
            denied(before,new SemanticEditIntent.RemoveDependency(task(":north:owner"),task(":north:release"),EdgeKind.FINALIZED_BY),"EDIT_SHARED_SOURCE");
            denied(before,new SemanticEditIntent.SetTaskOption(task(":north:owner"),"finalizedBy",new EditableValue.Sequence(List.of())),"EDIT_SHARED_SOURCE");
            var batch=new SemanticEditPlanner().planConfiguration(before,task(":north:owner"),Map.of(),Set.of(),Map.of("finalizedBy",new EditableValue.Literal(null)),EditStyle.defaults(),CancellationToken.NONE);assertTrue(batch.plan().isEmpty());assertEquals("EDIT_SHARED_SOURCE",batch.capability().reasons().getFirst().code());
            var add=allowed(before,new SemanticEditIntent.AddDependency(task(":north:local"),task(":north:release"),EdgeKind.FINALIZED_BY));assertEquals(Set.of(root.resolve("north/job.groovy").toUri()),add.changes().keySet());
            var remove=allowed(add.postimage(),new SemanticEditIntent.RemoveDependency(task(":north:local"),task(":north:release"),EdgeKind.FINALIZED_BY));assertEquals(add.changes().keySet(),remove.changes().keySet());
        }
    }
    @Test void taskAndParameterRenameUpdateEveryKnownOriginalFileLosslesslyWithoutIo()throws Exception{
        String entry="// entry 😀\r\napply from:'parts/source.groovy'\r\napply from:'parts/use.groovy'\r\ndefaults 'source','use'\r\n";
        String producer="parameter('p',description:'Path',type:'path',defaultValue:'in.txt')\r\ntask('source',Echo){ input=parameter('p') } // source retained\r\n";
        String consumer="// source is just a comment 😀\r\ntask('use',Echo,dependsOn:'source',after:'source'){ input=result('source').file }\r\ntask('paramUse',Echo){input=parameter('p')}\r\n";
        var job=write("job.groovy",entry);var source=write("parts/source.groovy",producer);var use=write("parts/use.groovy",consumer);
        try(var manager=manager(true)){
            var before=capture(manager);var rename=allowed(before,new SemanticEditIntent.RenameSymbol(task("source"),"renamed's 😀"));
            assertEquals(Set.of(job.toUri(),source.toUri(),use.toUri()),rename.changes().keySet());assertEquals(5,rename.changes().values().stream().mapToInt(List::size).sum());
            assertTrue(rename.postimages().get(use.toUri()).startsWith("// source is just a comment 😀\r\n"));assertTrue(rename.postimages().get(source.toUri()).endsWith(" // source retained\r\n"));
            assertEquals(entry,Files.readString(job));assertEquals(producer,Files.readString(source));assertEquals(consumer,Files.readString(use));
            assertTrue(rename.postimage().analysis().symbols().containsKey(task("renamed's 😀")));assertFalse(rename.postimage().analysis().symbols().containsKey(task("source")));
            var parameter=allowed(before,new SemanticEditIntent.RenameSymbol(new SymbolId(Namespace.PARAMETER,"p"),"newPath"));assertEquals(Set.of(source.toUri(),use.toUri()),parameter.changes().keySet());
            assertTrue(allowed(before,new SemanticEditIntent.RenameSymbol(task("source"),"source")).noOp());
        }
    }
    @Test void explicitFragmentInsertionDependencyResultAndFormPathsArePositive()throws Exception{
        write("job.groovy","apply from:'parts/source.groovy'\napply from:'parts/use.groovy'\ndefaults 'use'");
        var source=write("parts/source.groovy","task('source',Echo){input='input'}\n");var use=write("parts/use.groovy","task('use',Echo){input='old'}\n");
        try(var manager=manager(true)){
            var before=capture(manager);var dependency=allowed(before,new SemanticEditIntent.AddDependency(task("source"),task("use"),EdgeKind.DEPENDS_ON));assertEquals(Set.of(use.toUri()),dependency.changes().keySet());
            var bound=allowed(before,new SemanticEditIntent.BindResult(task("source"),"file",task("use"),"input"));assertEquals(Set.of(use.toUri()),bound.changes().keySet());
            assertTrue(bound.postimage().analysis().references().stream().anyMatch(r->r.kind()==ReferenceKind.RESULT&&r.target().orElseThrow().equals(task("source"))));
            allowed(dependency.postimage(),new SemanticEditIntent.RemoveDependency(task("source"),task("use"),EdgeKind.DEPENDS_ON));
            var form=new SemanticEditPlanner().planConfiguration(before,task("use"),Map.of("input",new EditableValue.ResultFieldRef(task("source"),"file")),Set.of(),Map.of("dependsOn",new EditableValue.Literal("source"),"description",new EditableValue.Literal("Changed")),EditStyle.defaults(),CancellationToken.NONE);assertTrue(form.plan().isPresent(),form.capability().toString());
            allowed(before,new SemanticEditIntent.AddTask(use.toUri(),Files.readString(use).length(),"added","test.echo",Map.of("input",new EditableValue.Literal("value"))));
            allowed(before,new SemanticEditIntent.AddAction(source.toUri(),Files.readString(source).length(),"actionAdded"));
            allowed(before,new SemanticEditIntent.AddParameter(use.toUri(),Files.readString(use).length(),new SemanticEditIntent.ParameterDeclaration("addedPath","Path","path",false,null)));
            denied(before,new SemanticEditIntent.SetDefaults(use.toUri(),List.of(task("source"))),"EDIT_DEFAULTS_SCOPE");
        }
    }
    @Test void entireWriteFootprintRejectsSharedUsesWhileIndependentAndSharedReadsRemainAllowed()throws Exception{
        write("settings.gretl","include 'north','south'");write("job.groovy","task('unique',Echo){input='root'}");
        for(String side:List.of("north","south"))write(side+"/job.groovy","apply from:'../shared/tasks.groovy'\ntask('local',Echo){input='old'}");
        var shared=write("shared/tasks.groovy","task('shared',Echo,dependsOn:':unique'){input=result(':unique').file}");
        try(var manager=manager(true)){
            var before=capture(manager);assertEquals(2,before.analysis().capture().ownership().contexts(shared.toUri()).size());
            denied(before,new SemanticEditIntent.RenameSymbol(task(":unique"),"renamed"),"EDIT_SHARED_SOURCE");
            denied(before,new SemanticEditIntent.SetField(task(":north:shared"),"input",new EditableValue.Literal("changed")),"EDIT_SHARED_SOURCE");
            denied(before,new SemanticEditIntent.SetTaskOption(task(":south:shared"),"description",new EditableValue.Literal("changed")),"EDIT_SHARED_SOURCE");
            allowed(before,new SemanticEditIntent.SetField(task(":unique"),"input",new EditableValue.Literal("independent")));
            allowed(before,new SemanticEditIntent.BindResult(task(":north:shared"),"file",task(":north:local"),"input"));
            var symbol=before.analysis().unique(task(":north:shared")).orElseThrow();var field=symbol.declaration().fields().getFirst().value();
            assertEquals("EDIT_SHARED_SOURCE",assertThrows(IllegalArgumentException.class,()->new EditVerifier().verify(before,Map.of(shared.toUri(),List.of(new SourceReplacement(field.span(),before.source(shared.toUri()).orElseThrow().slice(field.span()),"'bypass'"))),"Bypass",s->true,List.of(),CancellationToken.NONE)).getMessage());
        }
    }
    @Test void unopenedAndNewOwnersAndIncompleteInventoryPreventFalseExclusivity()throws Exception{
        write("job.groovy","apply from:'child/shared.groovy'");var shared=write("child/shared.groovy","task('shared',Echo){input='value'}");
        try(var manager=manager(true)){
            var before=capture(manager);var originalHash=before.source(shared.toUri()).orElseThrow().stamp().contentSha256();
            var old=allowed(before,new SemanticEditIntent.SetField(task("shared"),"input",new EditableValue.Literal("new")));
            write("child/job.groovy","apply from:'shared.groovy'");io.set(true);assertFalse(manager.isCurrent(before.analysis().capture(),CancellationToken.NONE));
            var current=capture(manager);assertEquals(originalHash,current.source(shared.toUri()).orElseThrow().stamp().contentSha256());assertFalse(old.isCurrent(current));
            assertEquals(2,current.analysis().capture().ownership().contexts(shared.toUri()).size());denied(current,new SemanticEditIntent.SetField(task("shared"),"input",new EditableValue.Literal("new")),"EDIT_SHARED_SOURCE");
        }
        try(var manager=manager(false)){denied(capture(manager),new SemanticEditIntent.SetField(task("shared"),"input",new EditableValue.Literal("new")),"EDIT_SHARED_SOURCE");}
    }
    @Test void samePhysicalSqlErrorCannotMoveBetweenApplicationsToCompensateANewError()throws Exception{
        write("settings.gretl","include 'north','south'");write("job.groovy","// root");
        var north=write("north/job.groovy","task('sql',DuckDbSqlExecutor){sqlFiles=[file('../shared/input.sql')];sqlParameters=[:]}");
        var south=write("south/job.groovy","task('sql',DuckDbSqlExecutor){sqlFiles=[file('../shared/input.sql')];sqlParameters=[p:'yes']}");write("shared/input.sql","select ${p}");
        try(var manager=manager(true)){
            var input=new InputDescriptor("sqlFiles","SQL",true,false,null,List.of(),"sqlFiles",ValueShape.fromMetadata("list"),null,false,Map.of());
            var parameters=new InputDescriptor("sqlParameters","Parameters",false,false,null,List.of(),"sqlParameters",ValueShape.fromMetadata("map"),null,false,Map.of());
            currentCatalog.mergeExplicitDescriptors(List.of(new OperationDescriptor("test.sql","DuckDbSqlExecutor","SQL","fixture",List.of(input,parameters),List.of(),"sql",true)));
            var before=capture(manager);var errors=before.analysis().diagnostics().stream().filter(d->d.severity()==Severity.ERROR).toList();assertEquals(1,errors.size(),errors.toString());assertEquals(":north",errors.getFirst().application().ownerScope());
            var changes=new LinkedHashMap<URI,List<SourceReplacement>>();
            for(var side:List.of("north","south")){
                var symbol=before.analysis().unique(task(":"+side+":sql")).orElseThrow();var value=symbol.declaration().fields().stream().filter(f->f.name().equals("sqlParameters")).findFirst().orElseThrow().value();
                changes.put(symbol.anchor().documentId(),List.of(new SourceReplacement(value.span(),before.source(symbol.anchor().documentId()).orElseThrow().slice(value.span()),side.equals("north")?"[p:'yes']":"[:]")));
            }
            assertEquals("EDIT_NEW_ERROR",assertThrows(IllegalArgumentException.class,()->new EditVerifier().verify(before,changes,"Swap errors",after->true,List.of(),CancellationToken.NONE)).getMessage());
        }
    }
    @Test void ownerAndContextPreconditionsAreIndependentOfUnchangedSourceHashes()throws Exception{
        write("job.groovy","apply from:'f.groovy'");write("f.groovy","task('shared',Echo){input='value'}");
        try(var manager=manager(true)){
            var plain=capture(manager);var application=plain.analysis().capture().project().includeClosure().applications().getFirst().id();
            var before=plain.inContext(new AnalysisSnapshot.ContextSelection(application,1,"token"));var edit=allowed(before,new SemanticEditIntent.SetField(task("shared"),"input",new EditableValue.Literal("new")));
            assertFalse(edit.isCurrent(before.inContext(new AnalysisSnapshot.ContextSelection(application,2,"token"))));
            var a=before.analysis();var c=a.capture();var changed=new WorkspaceManager.WorkspaceCapture(c.serverInstance(),c.generation(),c.rootConfigurationGeneration(),c.configurationDigest(),c.configuration(),c.catalog(),c.project(),c.readPolicy(),c.ownership().withGeneration(c.ownership().generation()+1));
            var current=new AnalysisSnapshot(before.id(),new Analysis(changed,a.symbols(),a.references(),a.paths(),a.diagnostics(),a.graph(),a.defaultTargets(),a.capabilities()),before.contextSelection());
            assertEquals(before.sources(),current.sources());assertFalse(edit.isCurrent(current));
        }
    }
    @Test void anIncompleteExplicitDefinitionInTheSameDirectoryPreventsEntryExclusivity()throws Exception{
        write("job.groovy","task('root',Echo){input='value'}");var other=write("other.groovy","task('broken',Echo){");
        try(var manager=manager(true)){
            manager.registerEntry(root.toUri(),other.toUri(),true);var before=capture(manager);
            assertTrue(before.analysis().capture().ownership().domainComplete());assertFalse(before.analysis().capture().ownership().complete());
            denied(before,new SemanticEditIntent.SetField(task("root"),"input",new EditableValue.Literal("new")),"EDIT_SHARED_SOURCE");
        }
    }
}
