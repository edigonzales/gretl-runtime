package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import java.net.URI;
import java.util.*;

/** Read contexts never authorize writes: every physical source in the actual footprint must be exclusive. */
public final class EditImpactAnalyzer {
    public record EditImpact(boolean allowed,List<Anchor> sources) {public EditImpact{sources=List.copyOf(sources);}}
    public EditImpact assess(AnalysisSnapshot snapshot,SemanticEditIntent intent,Map<URI,List<SourceReplacement>> changes){
        return assessSources(snapshot,changes.entrySet().stream().filter(e->!e.getValue().isEmpty()).map(Map.Entry::getKey).toList());
    }
    public EditImpact assessSources(AnalysisSnapshot snapshot,Collection<URI> documents){
        var ownership=snapshot.analysis().capture().ownership();
        var denied=documents.stream().map(ch.so.agi.gretl.tooling.workspace.DocumentStore::identity).distinct()
                .filter(id->!ownership.exclusive(id)).sorted().map(id->new Anchor(id,null)).toList();
        return new EditImpact(denied.isEmpty(),denied);
    }
}
