package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectId;
import java.util.*;
import java.util.function.BiPredicate;
import org.eclipse.lsp4j.*;

/** Retains bounded diagnostic DTOs, never analysis snapshots or editor buffers. */
final class DiagnosticPublicationState {
    record Batch(List<PublishDiagnosticsParams> changes,boolean limited) { }
    private record Project(long generation,List<PublishDiagnosticsParams> diagnostics) { }
    private final int limit;
    private final LinkedHashMap<ProjectId,Project> projects=new LinkedHashMap<>();
    private Map<String,PublishDiagnosticsParams> published=new LinkedHashMap<>();
    DiagnosticPublicationState(int limit){if(limit<1)throw new IllegalArgumentException();this.limit=limit;}
    synchronized Batch replace(ProjectId id,long generation,List<PublishDiagnosticsParams> diagnostics) {
        projects.remove(id);
        var errors=diagnostics.stream().filter(params->!params.getDiagnostics().isEmpty()).toList();
        if(!errors.isEmpty())projects.put(id,new Project(generation,errors));
        boolean limited=false;
        while(projects.size()>limit){projects.remove(projects.keySet().iterator().next());limited=true;}
        return recompute(limited);
    }
    synchronized Batch invalidate(Set<ProjectId> ids,BiPredicate<ProjectId,Long> current) {
        projects.entrySet().removeIf(entry->ids.contains(entry.getKey())&&!current.test(entry.getKey(),entry.getValue().generation()));
        return recompute(false);
    }
    private Batch recompute(boolean limited) {
        var combined=new LinkedHashMap<String,PublishDiagnosticsParams>();
        var unique=new LinkedHashMap<String,LinkedHashSet<Diagnostic>>();
        for(var project:projects.values())for(var contribution:project.diagnostics()) {
            var target=combined.computeIfAbsent(contribution.getUri(),uri->{var value=new PublishDiagnosticsParams(uri,new ArrayList<>());value.setVersion(contribution.getVersion());return value;});
            if(!Objects.equals(target.getVersion(),contribution.getVersion()))target.setVersion(null);
            unique.computeIfAbsent(contribution.getUri(),uri->new LinkedHashSet<>()).addAll(contribution.getDiagnostics());
        }
        combined.forEach((uri,params)->params.setDiagnostics(List.copyOf(unique.get(uri))));
        var affected=new LinkedHashSet<>(published.keySet());affected.addAll(combined.keySet());
        var changes=new ArrayList<PublishDiagnosticsParams>();
        for(var uri:affected) {
            var current=combined.get(uri);
            if(current==null)changes.add(new PublishDiagnosticsParams(uri,List.of()));
            else if(!current.equals(published.get(uri)))changes.add(current);
        }
        published=combined;
        return new Batch(List.copyOf(changes),limited);
    }
}
