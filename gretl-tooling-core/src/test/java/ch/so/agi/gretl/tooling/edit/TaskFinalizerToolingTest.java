package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.PlanSemantics;
import ch.so.agi.gretl.language.PlanSemantics.EdgeKind;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.graph.*;
import ch.so.agi.gretl.tooling.query.*;
import ch.so.agi.gretl.tooling.semantic.*;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.edit.EditVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskFinalizerToolingTest {
    final SemanticEditPlanner planner=new SemanticEditPlanner();
    SymbolId id(String name){return new SymbolId(Namespace.TASK,name);}
    PlannedEdit allowed(AnalysisSnapshot before,SemanticEditIntent intent){var result=planner.plan(before,intent,EditStyle.defaults(),CancellationToken.NONE);assertEquals(EditCapability.State.ALLOWED,result.capability().state(),result.toString());return result.plan().orElseThrow();}
    void denied(AnalysisSnapshot before,SemanticEditIntent intent,String code){var result=planner.plan(before,intent,EditStyle.defaults(),CancellationToken.NONE);assertTrue(result.plan().isEmpty());assertEquals(code,result.capability().reasons().getFirst().code());}
    @Test void staticIncludesHaveOwnEdgeReferencesAndNominalPreviewWithoutResultRights(){
        try(var f=new Fixture()){
            URI fragment=URI.create("file:/work/parts/f.groovy");
            String body="action('prepare'){}\naction('release',dependsOn:'prepare'){}\n";
            f.files.put(fragment,body);String job="parameter('p',description:'P')\naction('owner',finalizedBy:'release'){}\naction('unselected',finalizedBy:'release'){}\napply from:'parts/f.groovy'\ndefaults 'owner'\n";f.files.put(JOB,job);
            var before=f.capture();var a=before.analysis();assertTrue(a.diagnostics().isEmpty(),a.diagnostics().toString());
            var graph=new JobGraphProjector().project(before,JobGraphProjector.GraphOptions.defaults(),CancellationToken.NONE);assertEquals(JobGraph.Status.COMPLETE,graph.status());
            var owner=graph.nodes().stream().filter(n->n.title().equals("owner")).findFirst().orElseThrow();var release=graph.nodes().stream().filter(n->n.title().equals("release")).findFirst().orElseThrow();
            var edge=graph.edges().stream().filter(e->e.kind()==JobGraph.EdgeKind.FINALIZED_BY&&e.source().equals(owner.id())).findFirst().orElseThrow();assertEquals(release.id(),edge.target());assertFalse(edge.schemaKnown());assertNull(edge.inputField());assertNull(edge.outputField());
            assertEquals("release",before.source(JOB).orElseThrow().slice(edge.anchor().span()));
            var preview=new TargetPreviewProjector().project(before,List.of(),CancellationToken.NONE);assertTrue(preview.available());assertEquals(List.of("owner","prepare","release"),preview.orderedSymbols());assertEquals(List.of("owner"),preview.normalSymbols());assertEquals(List.of("prepare","release"),preview.conditionalSymbols());
            assertEquals(new PlanSemantics().plan(a.graph(),List.of(),a.defaultTargets()).orderedIds(),preview.orderedSymbols());
            assertTrue(new PlanSemantics().dataPredecessors(a.graph(),"release").equals(Set.of("prepare")));
            var q=new LanguageQueries();var context=new QueryContext(before,JOB,job.indexOf("'release'")+2);
            assertEquals(fragment,q.definitions(context).getFirst().documentId());assertTrue(q.hover(context).isPresent());
            var completions=q.complete(context);assertTrue(completions.stream().anyMatch(p->p.label().equals("release")));assertFalse(completions.stream().anyMatch(p->p.label().equals("p")));
            assertEquals(2,before.references().referencesTo(id("release")).uses().size());
            denied(before,new SemanticEditIntent.DeleteTask(id("release")),"EDIT_REFERENCED_TARGET");
        }
    }
    @Test void graphIntentsMutateOnlyTheOwnerAndPreserveCrlfCommentsUnicodeAndIncludes(){
        try(var f=new Fixture()){
            URI fragment=URI.create("file:/work/f.groovy");String finalizer="// release 😀\r\naction('release'){}\r\n";f.files.put(fragment,finalizer);
            String source="// keep 😀\r\napply from:'f.groovy'\r\naction('owner'){} // keep 'release'\r\ndefaults 'owner'\r\n";f.files.put(JOB,source);var before=f.capture();
            var add=allowed(before,new SemanticEditIntent.AddDependency(id("owner"),id("release"),EdgeKind.FINALIZED_BY));assertEquals(Set.of(JOB),add.changes().keySet());assertEquals(source.replace("action('owner')","action('owner', finalizedBy: 'release')"),add.postimages().get(JOB));assertEquals(finalizer,f.files.get(fragment));
            var remove=allowed(add.postimage(),new SemanticEditIntent.RemoveDependency(id("owner"),id("release"),EdgeKind.FINALIZED_BY));assertTrue(remove.postimage().analysis().graph().edges().isEmpty());assertTrue(remove.postimages().get(JOB).contains("finalizedBy: null"));
            var rename=allowed(add.postimage(),new SemanticEditIntent.RenameSymbol(id("release"),"released"));assertEquals(Set.of(JOB,fragment),rename.changes().keySet());assertTrue(rename.postimages().get(JOB).contains("finalizedBy: 'released'"));assertTrue(rename.postimages().get(JOB).contains("// keep 'release'"));
        }
    }
    @Test void aliasesListsAndQualifiedProjectFinalizersBindLikeOtherTaskReferences(){
        try(var f=new Fixture()){
            f.files.put(SETTINGS,"include 'part'\ndefaults ':part:owner'\n");f.files.put(JOB,"// root\n");URI part=URI.create("file:/work/part/job.groovy");
            f.files.put(part,"def end=action('release'){}\naction('owner',finalizedBy:[end,':other']){}\n");f.files.put(JOB,"action('other'){}\n");
            var before=f.capture();assertTrue(before.analysis().diagnostics().isEmpty(),before.analysis().diagnostics().toString());assertEquals(2,before.analysis().graph().edges().size());
            var rename=allowed(before,new SemanticEditIntent.RenameSymbol(id(":part:release"),"closed"));assertTrue(rename.postimages().get(part).contains("finalizedBy:[end,':other']"));assertTrue(rename.postimages().get(part).contains("action('closed')"));
            var graph=new JobGraphProjector().project(before,JobGraphProjector.GraphOptions.defaults(),CancellationToken.NONE);var forms=new GraphFormProjector().project(before,graph,CancellationToken.NONE);var owner=graph.nodes().stream().filter(n->n.title().equals(":part:owner")).findFirst().orElseThrow();assertNotNull(forms.nodes().get(owner.id()).options().get("finalizedBy").value());
            var removal=allowed(before,new SemanticEditIntent.RemoveDependency(id(":part:owner"),id(":part:release"),EdgeKind.FINALIZED_BY));assertTrue(removal.postimages().get(part).contains("finalizedBy:[':other']"));
        }
    }
    @Test void invalidLinksAndActivationDeadlockUseTheActualOptionRange(){
        for(var item:Map.of("missing","UNKNOWN_TASK_REFERENCE","owner","SELF_FINALIZER").entrySet())try(var f=new Fixture()){
            String source="action('owner',finalizedBy:'"+item.getKey()+"'){}\ndefaults 'owner'";f.files.put(JOB,source);var before=f.capture();var d=before.analysis().diagnostics().stream().filter(v->v.code().equals(item.getValue())).findFirst().orElseThrow();assertEquals(item.getKey(),before.source(JOB).orElseThrow().slice(d.anchor().span()));
        }
        for(String relation:List.of("dependsOn:'owner'","after:'owner'"))try(var f=new Fixture()){
            // The after case is a legal ordinary finalizer; dependsOn owner is legal too.
            f.files.put(JOB,"action('owner',finalizedBy:'release'){}\naction('release',"+relation+"){}\ndefaults 'owner'");assertTrue(new TargetPreviewProjector().project(f.capture(),List.of(),CancellationToken.NONE).available());
        }
        try(var f=new Fixture()){
            f.files.put(JOB,"action('owner',finalizedBy:'release',after:'prepare'){}\naction('prepare'){}\naction('release',dependsOn:'prepare'){}\ndefaults 'owner'");var before=f.capture();var d=before.analysis().diagnostics().stream().filter(v->v.code().equals("FINALIZER_ACTIVATION_DEADLOCK")).findFirst().orElseThrow();assertEquals("release",before.source(JOB).orElseThrow().slice(d.anchor().span()));assertFalse(new TargetPreviewProjector().project(before,List.of(),CancellationToken.NONE).available());
        }
    }
    @Test void genericSetterAndBatchCannotBypassPartialCoverageEvenToClearLinks(){
        try(var f=new Fixture()){
            f.files.put(JOB,"action('owner',finalizedBy:[]){}\ndef dynamic=System.getProperty('task'); action(dynamic){}\n");var before=f.capture();assertFalse(before.analysis().capabilities().referencesComplete());
            for(var value:List.<EditableValue>of(new EditableValue.Literal(null),new EditableValue.Sequence(List.of()),new EditableValue.Literal("owner"))) {
                denied(before,new SemanticEditIntent.SetTaskOption(id("owner"),"finalizedBy",value),"EDIT_REFERENCE_COVERAGE");
                var batch=planner.planConfiguration(before,id("owner"),Map.of(),Set.of(),Map.of("finalizedBy",value),EditStyle.defaults(),CancellationToken.NONE);assertTrue(batch.plan().isEmpty());assertEquals("EDIT_REFERENCE_COVERAGE",batch.capability().reasons().getFirst().code());
            }
        }
    }
    @Test void cyclesUnknownTargetsAndResultRightsAreVerifiedBeforeEditsArePublished(){
        try(var f=new Fixture()){
            f.files.put(JOB,"action('owner'){}\naction('release',finalizedBy:'owner'){}\ndefaults 'owner'\n");var before=f.capture();
            denied(before,new SemanticEditIntent.SetTaskOption(id("owner"),"finalizedBy",new EditableValue.Literal("release")),"EDIT_NEW_ERROR");
            denied(before,new SemanticEditIntent.SetTaskOption(id("owner"),"finalizedBy",new EditableValue.Literal("missing")),"EDIT_NEW_ERROR");
        }
        try(var f=new Fixture()){
            f.files.put(JOB,"action('owner',finalizedBy:'release'){}\naction('release'){resultOptional('owner')}\ndefaults 'owner'\n");assertTrue(f.capture().analysis().diagnostics().stream().anyMatch(d->d.code().equals("RESULT_DEPENDENCY_REQUIRED")));
        }
    }
    @Test void selfFinalizerWithResultReadsProducesDiagnosticsInsteadOfAbortingAnalysis(){
        try(var f=new Fixture()){
            f.files.put(JOB,"action('owner',finalizedBy:'owner'){resultOptional('owner')}\ndefaults 'owner'\n");var before=f.capture();assertTrue(before.analysis().diagnostics().stream().anyMatch(d->d.code().equals("SELF_FINALIZER")));assertEquals(JobGraph.Status.INVALID,new JobGraphProjector().project(before,JobGraphProjector.GraphOptions.defaults(),CancellationToken.NONE).status());
        }
    }
    @Test void headerAndFragmentChangesInvalidatePreviouslyPlannedFinalizerEdits(){
        try(var f=new Fixture()){
            URI fragment=URI.create("file:/work/f.groovy");f.files.put(fragment,"action('release'){}\n");f.files.put(JOB,"apply from:'f.groovy'\naction('owner'){}\ndefaults 'owner'\n");var before=f.capture();var add=allowed(before,new SemanticEditIntent.AddDependency(id("owner"),id("release"),EdgeKind.FINALIZED_BY));
            f.files.put(fragment,"action('release',description:'changed'){}\n");f.manager.onSourcesChanged(Set.of(fragment));assertFalse(add.isCurrent(f.capture()));
            f.files.put(JOB,"apply from:'f.groovy'\naction('owner',finalizedBy:'release'){}\ndefaults 'owner'\n");f.manager.onSourcesChanged(Set.of(JOB));assertFalse(add.isCurrent(f.capture()));
        }
    }
}
