package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.PlanSemantics;
import ch.so.agi.gretl.language.PlanSemantics.EdgeKind;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.syntax.EffectiveOptions;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.Channel;
import java.util.List;

/** Graph intents edit the declaring header: consumer for dependencies, owner for finalizers. */
final class DependencySourceEdits {
    void add(EditTransaction transaction, SymbolId producer, SymbolId consumer, EdgeKind kind) {
        checkTasks(transaction,producer,consumer);
        var declaring=kind==EdgeKind.FINALIZED_BY?producer:consumer;
        var referenced=kind==EdgeKind.FINALIZED_BY?consumer:producer;
        if (has(transaction.before.analysis().graph(),producer,consumer,kind)) return;
        var symbol=transaction.symbol(declaring);
        String option=option(kind);
        var entry=entry(transaction,declaring,option);
        String name=transaction.spelling(symbol.scope(),referenced);
        if(entry!=null&&entry.value() instanceof ListValue list) append(transaction,symbol,list,SourceValueWriter.string(name,QuoteKind.SINGLE));
        else if(entry==null||entry.value() instanceof LiteralValue literal&&literal.value()==null) {
            new HeaderSourceEdits().set(transaction,declaring,option,new EditableValue.Literal(name));
        } else {
            var previous=transaction.before.analysis().references().stream().filter(r->declaring.equals(r.owner())&&r.kind()==referenceKind(kind)).toList();
            if(previous.size()!=1||previous.getFirst().target().isEmpty())throw new IllegalArgumentException("EDIT_REFERENCE_COVERAGE");
            String old=transaction.source(symbol.anchor().documentId()).slice(entry.value().span());
            transaction.replace(symbol.anchor().documentId(),entry.value().span(),"["+old+", "+SourceValueWriter.string(name,QuoteKind.SINGLE)+"]");
        }
        transaction.expect(after->has(after.analysis().graph(),producer,consumer,kind));
    }

    void remove(EditTransaction transaction, SymbolId producer, SymbolId consumer, EdgeKind kind) {
        checkTasks(transaction,producer,consumer);
        var declaring=kind==EdgeKind.FINALIZED_BY?producer:consumer;
        var referenced=kind==EdgeKind.FINALIZED_BY?consumer:producer;
        var graph=transaction.before.analysis().graph();
        if(!has(graph,producer,consumer,kind))return;
        var remaining=graph.edges().stream().filter(e->!(e.fromId().equals(producer.qualifiedName())&&e.toId().equals(consumer.qualifiedName())&&e.kind()==kind)).toList();
        var afterGraph=new PlanSemantics.DeclaredGraph(graph.nodes(),remaining);
        if(kind==EdgeKind.DEPENDS_ON) {
            var owners=transaction.before.analysis().references().stream().filter(r->r.owner()!=null&&r.owner().namespace()==Namespace.TASK
                    &&(r.kind()==ReferenceKind.RESULT||r.kind()==ReferenceKind.RESULT_OPTIONAL)&&r.target().isPresent())
                    .collect(java.util.stream.Collectors.groupingBy(Reference::owner));
            for(var owner:owners.entrySet()) {
                var afterRights=new PlanSemantics().dataPredecessors(afterGraph,owner.getKey().qualifiedName());
                var lost=owner.getValue().stream().filter(r->!afterRights.contains(r.target().orElseThrow().qualifiedName())).toList();
                if(lost.isEmpty())continue;
                var beforeRights=new PlanSemantics().dataPredecessors(graph,owner.getKey().qualifiedName());
                if(lost.stream().anyMatch(r->beforeRights.contains(r.target().orElseThrow().qualifiedName())))throw new IllegalArgumentException("EDIT_RESULT_DEPENDENCY");
            }
        }
        var symbol=transaction.symbol(declaring);
        var entry=entry(transaction,declaring,option(kind));
        if(entry==null)throw new IllegalArgumentException("EDIT_REFERENCE_COVERAGE");
        var references=transaction.before.analysis().references().stream().filter(r->declaring.equals(r.owner())&&r.kind()==referenceKind(kind)
                &&r.target().filter(referenced::equals).isPresent()).toList();
        if(references.size()!=1)throw new IllegalArgumentException("EDIT_MULTIPLE_LINKS");
        if(entry.value() instanceof ListValue list) {
            var values=list.items().stream().filter(v->v.span().contains(references.getFirst().anchor().span())).toList();
            if(values.size()!=1)throw new IllegalArgumentException("EDIT_REFERENCE_COVERAGE");
            var value=values.getFirst();
            var document=transaction.before.document(symbol.anchor().documentId()).orElseThrow();
            if(document.tokens().within(value.span()).stream().anyMatch(t->t.channel()==Channel.COMMENT))throw new IllegalArgumentException("EDIT_COMMENT_OWNERSHIP");
            var commas=document.tokens().within(list.span()).stream().filter(t->t.channel()==Channel.CODE&&t.text().equals(",")
                    &&list.items().stream().noneMatch(v->v.span().contains(t.span()))).toList();
            var comma=commas.stream().filter(t->t.span().startUtf16()>=value.span().endUtf16()).findFirst()
                    .or(()->commas.stream().filter(t->t.span().endUtf16()<=value.span().startUtf16()).reduce((first,last)->last));
            transaction.edits(symbol.anchor().documentId()).deleteOwnedRegion(value.span());
            comma.ifPresent(t->transaction.edits(symbol.anchor().documentId()).deleteOwnedRegion(t.span()));
        } else new HeaderSourceEdits().set(transaction,declaring,option(kind),new EditableValue.Literal(null));
        transaction.expect(after->!has(after.analysis().graph(),producer,consumer,kind));
    }

    void bind(EditTransaction transaction, SemanticEditIntent.BindResult intent) {
        checkTasks(transaction,intent.producer(),intent.consumer());
        var producer=transaction.symbol(intent.producer());
        var consumer=transaction.symbol(intent.consumer());
        if(producer.operation()==null||consumer.operation()==null||List.of("class","metaClass").contains(intent.outputField()))throw new IllegalArgumentException("EDIT_UNKNOWN_SCHEMA");
        var input=consumer.operation().inputs().stream().filter(f->f.name().equals(intent.inputField())).findFirst()
                .orElseThrow(()->new IllegalArgumentException("EDIT_UNKNOWN_FIELD"));
        new PortCompatibility().require(transaction.before,intent.producer(),intent.outputField(),input.shape());
        if(!new PlanSemantics().dataPredecessors(transaction.before.analysis().graph(),intent.consumer().qualifiedName()).contains(intent.producer().qualifiedName())) {
            add(transaction,intent.producer(),intent.consumer(),EdgeKind.DEPENDS_ON);
        }
        new FieldSourceEdits().set(transaction,intent.consumer(),intent.inputField(),new EditableValue.ResultFieldRef(intent.producer(),intent.outputField()));
    }

    private void append(EditTransaction transaction,Symbol symbol,ListValue list,String text) {
        var source=transaction.source(symbol.anchor().documentId());
        var tokens=transaction.before.document(symbol.anchor().documentId()).orElseThrow().tokens().significant().stream().filter(t->list.span().contains(t.span())).toList();
        if(tokens.isEmpty()||!tokens.getFirst().text().equals("[")||!tokens.getLast().text().equals("]"))throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
        var close=tokens.getLast();
        boolean comma=tokens.size()>1&&tokens.get(tokens.size()-2).text().equals(",");
        int line=source.lines().positionAt(close.span().startUtf16()).line();
        if(line!=source.lines().positionAt(list.span().startUtf16()).line()) {
            int start=source.lines().contentSpan(line).startUtf16();
            String indent=source.text().substring(start,close.span().startUtf16());
            if(!indent.chars().allMatch(c->c==' '||c=='\t'))throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
            if(!list.items().isEmpty()&&!comma)transaction.edits(symbol.anchor().documentId()).insert(tokens.get(tokens.size()-2).span().endUtf16(),",");
            String itemIndent=indent+transaction.style.indentation();
            for(var item:list.items()) {
                int itemLine=source.lines().positionAt(item.span().startUtf16()).line();
                String leading=source.text().substring(source.lines().contentSpan(itemLine).startUtf16(),item.span().startUtf16());
                if(!leading.isEmpty()&&leading.chars().allMatch(c->c==' '||c=='\t')){itemIndent=leading;break;}
            }
            transaction.edits(symbol.anchor().documentId()).insert(start,itemIndent+text+source.preferredEol());
        } else transaction.edits(symbol.anchor().documentId()).insert(close.span().startUtf16(),(list.items().isEmpty()||comma?"":", ")+text);
    }

    private MapEntry entry(EditTransaction transaction,SymbolId consumer,String option) {
        var declaration=transaction.symbol(consumer).declaration();
        var options=EffectiveOptions.of(declaration);
        if(!options.complete())throw new IllegalArgumentException("EDIT_OPTION_COVERAGE");
        if(declaration.options().stream().filter(e->e.key() instanceof LiteralValue l&&option.equals(l.value())).count()>1)throw new IllegalArgumentException("EDIT_MULTIPLE_OPTIONS");
        return options.entries().get(option);
    }
    private boolean has(PlanSemantics.DeclaredGraph graph,SymbolId producer,SymbolId consumer,EdgeKind kind) {
        return graph.edges().stream().anyMatch(e->e.fromId().equals(producer.qualifiedName())&&e.toId().equals(consumer.qualifiedName())&&e.kind()==kind);
    }
    private String option(EdgeKind kind){return switch(kind){case DEPENDS_ON->"dependsOn";case AFTER->"after";case FINALIZED_BY->"finalizedBy";};}
    private ReferenceKind referenceKind(EdgeKind kind){return switch(kind){case DEPENDS_ON->ReferenceKind.DEPENDS_ON;case AFTER->ReferenceKind.AFTER;case FINALIZED_BY->ReferenceKind.FINALIZED_BY;};}
    private void checkTasks(EditTransaction transaction,SymbolId producer,SymbolId consumer) {
        if(producer.namespace()!=Namespace.TASK||consumer.namespace()!=Namespace.TASK)throw new IllegalArgumentException("EDIT_TARGET_KIND");
        transaction.symbol(producer);transaction.symbol(consumer);
        if(!transaction.before.analysis().capabilities().graphComplete()||!transaction.before.analysis().capabilities().referencesComplete())throw new IllegalArgumentException("EDIT_REFERENCE_COVERAGE");
    }
}
