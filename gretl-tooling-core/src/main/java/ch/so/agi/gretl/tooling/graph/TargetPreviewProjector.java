package ch.so.agi.gretl.tooling.graph;

import ch.so.agi.gretl.language.PlanSemantics;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.Severity;
import java.util.List;
import java.util.Objects;

/** Static target selection through the same pure rules as the runner; never a runtime readiness claim. */
public final class TargetPreviewProjector {
    public record Preview(boolean available,String reason,List<String> targets,List<String> orderedSymbols) {
        public Preview {targets=List.copyOf(targets);orderedSymbols=List.copyOf(orderedSymbols);}
    }
    public Preview project(AnalysisSnapshot snapshot,List<String> targets,CancellationToken cancellation) {
        cancellation.check();Objects.requireNonNull(targets);
        if(targets.size()>1024||targets.stream().anyMatch(t->t==null||t.length()>65_536))throw new IllegalArgumentException("GRAPH_TARGET_LIMIT");
        var analysis=snapshot.analysis();var complete=analysis.capabilities();
        if(!complete.bounded()||!complete.declarationsComplete()||!complete.referencesComplete()||!complete.graphComplete())
            return new Preview(false,"GRAPH_INCOMPLETE",targets,List.of());
        if(analysis.diagnostics().stream().anyMatch(d->d.severity()==Severity.ERROR))
            return new Preview(false,"GRAPH_INVALID",targets,List.of());
        try {
            var selected=new PlanSemantics().plan(analysis.graph(),targets,analysis.defaultTargets());
            cancellation.check();
            return new Preview(true,null,selected.requestedTargets(),selected.orderedIds());
        } catch(PlanSemantics.GraphException invalid) {
            return new Preview(false,invalid.code(),targets,List.of());
        }
    }
}
