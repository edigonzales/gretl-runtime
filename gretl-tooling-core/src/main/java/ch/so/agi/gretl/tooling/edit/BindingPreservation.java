package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.TextSpan;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks every bound or unresolved use after a rename/delete, not only a total reference count. */
final class BindingPreservation {
    private record Key(ReferenceKind kind,SymbolId owner,URI document,TextSpan span,ch.so.agi.gretl.tooling.workspace.IncludeModel.ApplicationId application) { }
    boolean verify(AnalysisSnapshot before,AnalysisSnapshot after,Map<URI,List<SourceReplacement>> changes,
                   Map<SymbolId,SymbolId> renamed,Set<SymbolId> removed,CancellationToken cancellation) {
        var available=new LinkedHashMap<Key,List<Reference>>();
        for(var reference:after.analysis().references())available.computeIfAbsent(key(reference),ignored->new ArrayList<>()).add(reference);
        for(var reference:before.analysis().references()) {
            cancellation.check();
            if(reference.owner()!=null&&removed.contains(reference.owner()))continue;
            var span=map(reference.anchor().span(),changes.getOrDefault(reference.anchor().documentId(),List.of()));
            if(span==null)return false;
            SymbolId owner=reference.owner()==null?null:renamed.getOrDefault(reference.owner(),reference.owner());
            var candidates=reference.candidates().stream().map(id->renamed.getOrDefault(id,id)).toList();
            var key=new Key(reference.kind(),owner,reference.anchor().documentId(),span,reference.application());
            var found=available.getOrDefault(key,List.of());
            Reference matching=null;
            for(var candidate:found)if(candidate.resolution()==reference.resolution()&&candidate.candidates().equals(candidates)){matching=candidate;break;}
            if(matching==null)return false;
            found.remove(matching);
        }
        if(available.values().stream().anyMatch(list->!list.isEmpty()))return false;
        for(var entry:before.analysis().symbols().entrySet()) {
            cancellation.check();
            if(removed.contains(entry.getKey()))continue;
            var target=renamed.getOrDefault(entry.getKey(),entry.getKey());
            if(after.analysis().symbols().getOrDefault(target,List.of()).size()!=entry.getValue().size())return false;
        }
        return after.analysis().symbols().size()==before.analysis().symbols().size()-removed.size();
    }
    private Key key(Reference reference){return new Key(reference.kind(),reference.owner(),reference.anchor().documentId(),reference.anchor().span(),reference.application());}
    private TextSpan map(TextSpan original,List<SourceReplacement> edits) {
        if(original==null)return null;
        for(var edit:edits)if(edit.span().contains(original)&&edit.span().length()>0) {
            String value=edit.replacement();
            if(value.length()<2||(value.charAt(0)!='\''&&value.charAt(0)!='"'))return null;
            int start=offset(edit.span().startUtf16(),edits);
            return new TextSpan(start+1,start+value.length()-1);
        }
        if(edits.stream().anyMatch(e->e.span().intersects(original)))return null;
        return new TextSpan(offset(original.startUtf16(),edits),offset(original.endUtf16(),edits));
    }
    private int offset(int original,List<SourceReplacement> edits) {
        int result=original;
        for(var edit:edits)if(edit.span().endUtf16()<=original)result+=edit.replacement().length()-edit.span().length();
        return result;
    }
}
