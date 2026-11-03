package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.Symbol;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.Channel;

/** Refuses opaque alias escapes. It never infers or rewrites references by matching text. */
final class AliasUseGuard {
    void check(AnalysisSnapshot snapshot,Symbol symbol,boolean deleting) {
        if(symbol.id().namespace()!=ch.so.agi.gretl.tooling.semantic.SemanticModel.Namespace.TASK)return;
        String alias=symbol.declaration().bindingName();
        if(alias==null)return;
        var document=snapshot.document(symbol.anchor().documentId()).orElseThrow();
        var references=snapshot.analysis().references().stream()
                .filter(r->r.anchor().documentId().equals(symbol.anchor().documentId())).toList();
        var spans=new java.util.TreeMap<Integer,java.util.List<ch.so.agi.gretl.tooling.source.TextSpan>>();
        for(var reference:references)if(reference.anchor().span()!=null)spans.computeIfAbsent(reference.anchor().span().startUtf16(),key->new java.util.ArrayList<>()).add(reference.anchor().span());
        var exact=references.stream().map(r->r.anchor().span()).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var properties=snapshot.analysis().symbols().values().stream().flatMap(java.util.Collection::stream)
                .filter(s->s.anchor().documentId().equals(symbol.anchor().documentId())).flatMap(s->s.declaration().properties().stream()).toList();
        var mention=java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}_])"+java.util.regex.Pattern.quote(alias)+"(?![\\p{L}\\p{N}_$])");
        for(var token:document.tokens().all()) {
            if(deleting&&symbol.declaration().statementSpan().contains(token.span()))continue;
            if(symbol.declaration().bindingSpan()!=null&&symbol.declaration().bindingSpan().contains(token.span()))continue;
            boolean identifier=token.channel()==Channel.CODE&&(token.text().equals(alias)||token.text().contains("\\u"));
            if(!identifier&&!token.kind().equals("GString"))continue;
            if(exact.contains(token.span()))continue;
            var enclosing=spans.floorEntry(token.span().startUtf16());
            if(enclosing!=null&&enclosing.getValue().stream().anyMatch(span->span.contains(token.span())))continue;
            String opaque=token.text();
            if(token.kind().equals("GString")) {
                var remaining=opaque.toCharArray();
                for(var group:spans.subMap(token.span().startUtf16(),true,token.span().endUtf16(),false).values())for(var span:group)if(token.span().contains(span)) {
                    java.util.Arrays.fill(remaining,span.startUtf16()-token.span().startUtf16(),span.endUtf16()-token.span().startUtf16(),' ');
                }
                for(var property:properties)if(token.span().contains(property.propertySpan())) {
                        java.util.Arrays.fill(remaining,property.propertySpan().startUtf16()-token.span().startUtf16(),property.propertySpan().endUtf16()-token.span().startUtf16(),' ');
                }
                opaque=new String(remaining);
            }
            boolean interpolated=token.kind().equals("GString")&&(mention.matcher(opaque).find()||opaque.contains("\\u"));
            if(identifier||interpolated)throw new IllegalArgumentException("EDIT_ALIAS_ESCAPE");
        }
    }
}
