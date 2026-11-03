package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.ProjectAnalyzer;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.Severity;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver;
import ch.so.agi.gretl.tooling.workspace.SourceRepository;
import ch.so.agi.gretl.tooling.workspace.WorkspaceManager.WorkspaceCapture;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Applies, reparses and binds virtual postimages using captured data only; never reacquires IO. */
public final class EditVerifier {
    public PlannedEdit verify(AnalysisSnapshot before, Map<URI, List<SourceReplacement>> requested,
            String summary, Predicate<AnalysisSnapshot> expectedDelta, List<String> notices,
            CancellationToken cancellation) {
        return verify(before,requested,summary,expectedDelta,notices,cancellation,false,false);
    }

    PlannedEdit verify(AnalysisSnapshot before, Map<URI, List<SourceReplacement>> requested,
            String summary, Predicate<AnalysisSnapshot> expectedDelta, List<String> notices,
            CancellationToken cancellation, boolean literalRename,boolean intermediateConfiguration) {
        cancellation.check();
        if (!before.analysis().capabilities().bounded() || before.analysis().capture().project().sources().limited()) throw new IllegalArgumentException("EDIT_ANALYSIS_LIMIT");
        if (requested.size() > 512 || requested.values().stream().mapToInt(List::size).sum() > SourceEditBuilder.MAXIMUM_EDITS) {
            throw new IllegalArgumentException("EDIT_LIMIT");
        }
        var changes = new LinkedHashMap<URI, List<SourceReplacement>>();
        var postimages = new LinkedHashMap<URI, String>();
        for (var entry : requested.entrySet()) {
            cancellation.check();
            URI id = DocumentStore.identity(entry.getKey());
            if (changes.containsKey(id)) throw new IllegalArgumentException("EDIT_DUPLICATE_DOCUMENT");
            var source = before.source(id).orElseThrow(() -> new IllegalArgumentException("EDIT_SOURCE_NOT_CAPTURED"));
            if (!source.stamp().editable()) throw new IllegalArgumentException("EDIT_READONLY");
            var builder = new SourceEditBuilder(source);
            for (var edit : entry.getValue()) builder.replace(edit.span(), edit.expectedText(), edit.replacement());
            var edits = builder.build();
            if (edits.isEmpty()) continue;
            String text = builder.apply();
            if (text.length() > 1_048_576) throw new IllegalArgumentException("EDIT_SIZE_LIMIT");
            if (!SourceEditBuilder.verifyPreservation(source, text, edits)) throw new IllegalArgumentException("EDIT_PRESERVATION");
            changes.put(id, edits);
            postimages.put(id, text);
        }
        var after = changes.isEmpty() ? before : reanalyze(before, postimages, cancellation);
        if (!after.analysis().capabilities().bounded() || after.analysis().capture().project().sources().limited()) {
            throw new IllegalArgumentException("EDIT_ANALYSIS_LIMIT");
        }
        for (var entry : after.analysis().capture().project().sources().versions().entrySet()) {
            if (!before.analysis().capture().project().sources().versions().containsKey(entry.getKey())
                    && "SOURCE_NOT_CAPTURED".equals(entry.getValue().unavailableCode())) {
                throw new IllegalArgumentException("EDIT_SOURCE_NOT_CAPTURED");
            }
        }
        var remaining = errorCounts(before,changes,literalRename);
        for (var entry : errorCounts(after,Map.of(),false).entrySet()) {
            if (!intermediateConfiguration&&entry.getValue() > remaining.getOrDefault(entry.getKey(), 0)) throw new IllegalArgumentException("EDIT_NEW_ERROR");
        }
        if (!expectedDelta.test(after)) throw new IllegalArgumentException("EDIT_POSTCONDITION");
        return new PlannedEdit(UUID.randomUUID(), EditPreconditions.capture(before), summary, changes, postimages, after, notices);
    }

    private AnalysisSnapshot reanalyze(AnalysisSnapshot before, Map<URI, String> postimages, CancellationToken cancellation) {
        var original = before.analysis().capture();
        var frozen = original.project().sources();
        SourceRepository virtual = new SourceRepository() {
            @Override public SourceRead read(URI id, ReadPolicy policy, CancellationToken token) {
                token.check();
                id = DocumentStore.identity(id);
                if (!policy.permits(id)) return SourceRead.unavailable("SOURCE_OUTSIDE_POLICY", id);
                if (!postimages.containsKey(id)) return frozen.read(id, policy, token);
                var source = before.source(id).orElseThrow();
                return SourceRead.found(new SourceText(id, postimages.get(id), source.stamp().bufferVersion(),
                        "POSTIMAGE", source.stamp().editable()));
            }
            @Override public SourceListing listProjectEntries(URI root, ScanBudget budget, CancellationToken token) {
                return frozen.listProjectEntries(root, budget, token);
            }
        };
        var id = original.project().id();
        var request = new ProjectResolver.ProjectRequest(id.workspaceRoot(), id.anchor(), id.singleJob(),
                original.readPolicy().excludedRoots());
        var resolved = new ProjectResolver().resolve(request, virtual, new DocumentStore.BufferCapture(0, Map.of()), cancellation);
        var capture = new WorkspaceCapture(original.serverInstance(), original.generation(),
                original.rootConfigurationGeneration(), original.configurationDigest(), original.configuration(),
                original.catalog(), resolved, original.readPolicy());
        return AnalysisSnapshot.of(new ProjectAnalyzer().analyze(capture, cancellation));
    }

    private record ErrorKey(String code,URI document,ch.so.agi.gretl.tooling.source.TextSpan span) { }

    private Map<ErrorKey, Integer> errorCounts(AnalysisSnapshot snapshot,Map<URI,List<SourceReplacement>> changes,boolean literalRename) {
        var counts = new LinkedHashMap<ErrorKey, Integer>();
        for (var diagnostic : snapshot.analysis().diagnostics()) {
            if (diagnostic.severity() != Severity.ERROR) continue;
            var anchor = diagnostic.anchor();
            var edits=changes.getOrDefault(anchor.documentId(),List.of());
            var mapped=mapDiagnostic(anchor.span(),edits,literalRename);
            if(anchor.span()!=null&&mapped==null)continue; // Changed erroneous expressions do not excuse a new error.
            var key = new ErrorKey(diagnostic.code(),anchor.documentId(),mapped);
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    private ch.so.agi.gretl.tooling.source.TextSpan mapDiagnostic(ch.so.agi.gretl.tooling.source.TextSpan span,List<SourceReplacement> edits,boolean literalRename) {
        if(span==null)return null;
        int shift=0;
        for(var edit:edits) {
            if(edit.span().endUtf16()<=span.startUtf16())shift+=edit.replacement().length()-edit.span().length();
            else if(edit.span().intersects(span)) {
                // Only the rename handler can request this, and it also proves preservation of every binding.
                if(!literalRename||!edit.span().contains(span)||edit.replacement().length()<2)return null;
                int start=edit.span().startUtf16()+shift;
                if(edit.span().equals(span))return new ch.so.agi.gretl.tooling.source.TextSpan(start,start+edit.replacement().length());
                return new ch.so.agi.gretl.tooling.source.TextSpan(start+1,start+edit.replacement().length()-1);
            }
        }
        return new ch.so.agi.gretl.tooling.source.TextSpan(span.startUtf16()+shift,span.endUtf16()+shift);
    }
}
