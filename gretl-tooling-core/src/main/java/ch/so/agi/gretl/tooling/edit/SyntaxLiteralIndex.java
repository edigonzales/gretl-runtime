package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Exact AST literal envelopes for bound content anchors, including settings and options closures. */
final class SyntaxLiteralIndex {
    private final Map<URI,Map<TextSpan,StringLiteralSpan>> literals=new HashMap<>();
    private final CancellationToken cancellation;
    SyntaxLiteralIndex(AnalysisSnapshot snapshot,CancellationToken cancellation) {
        this.cancellation=cancellation;
        snapshot.analysis().capture().project().documents().forEach((id,document)->{
            cancellation.check();
            for(var d:document.declarations()) {
                add(id,d.nameLiteral());walk(id,d.operation());
                d.options().forEach(e->{walk(id,e.key());walk(id,e.value());});
                d.targets().forEach(v->walk(id,v));d.fields().forEach(f->walk(id,f.value()));
                d.references().forEach(v->walk(id,v));d.properties().forEach(v->walk(id,v));
                d.invocations().forEach(inner->{walk(id,inner.operation());inner.fields().forEach(f->walk(id,f.value()));});
            }
        });
        var settings=snapshot.analysis().capture().project().settings();
        if(settings!=null){var id=settings.source().stamp().documentId();settings.includes().forEach(n->add(id,n.literal()));settings.defaults().forEach(n->add(id,n.literal()));}
    }
    Optional<StringLiteralSpan> at(URI id,TextSpan content){return Optional.ofNullable(literals.getOrDefault(id,Map.of()).get(content));}
    private void add(URI id,StringLiteralSpan literal){if(literal!=null)literals.computeIfAbsent(id,key->new HashMap<>()).put(literal.content(),literal);}
    private void walk(URI id,ValueExpression value) {
        cancellation.check();
        if(value==null)return;
        switch(value) {
            case LiteralValue literal -> add(id,literal.literal());
            case ListValue list -> list.items().forEach(v->walk(id,v));
            case MapValue map -> map.entries().forEach(e->{walk(id,e.key());walk(id,e.value());});
            case CallValue call -> call.arguments().forEach(v->walk(id,v));
            case PropertyValue property -> walk(id,property.receiver());
            default -> { }
        }
    }
}
