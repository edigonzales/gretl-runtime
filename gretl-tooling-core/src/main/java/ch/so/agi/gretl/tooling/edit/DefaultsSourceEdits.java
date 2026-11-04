package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.syntax.TokenIndex;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.Channel;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Replaces the complete ordered default selection, retaining all unrelated source and comment tokens. */
final class DefaultsSourceEdits {
    void set(EditTransaction transaction,SemanticEditIntent.SetDefaults intent) {
        var analysis=transaction.before.analysis();
        var project=analysis.capture().project();
        if(!analysis.capabilities().graphComplete()||!analysis.capabilities().referencesComplete())throw new IllegalArgumentException("EDIT_REFERENCE_COVERAGE");
        if(intent.targets().stream().distinct().count()!=intent.targets().size())throw new IllegalArgumentException("EDIT_DUPLICATE_TARGET");
        for(var target:intent.targets()) {
            if(target.namespace()!=Namespace.TASK)throw new IllegalArgumentException("EDIT_TARGET_KIND");
            transaction.symbol(target);
        }
        URI document=DocumentStore.identity(intent.scopeDocument());
        if(project.projectMode()&&!document.equals(project.settingsId())||!project.projectMode()&&project.jobs().stream().noneMatch(j->j.documentId().equals(document))) {
            throw new IllegalArgumentException("EDIT_DEFAULTS_SCOPE");
        }
        var references=analysis.references().stream().filter(r->r.kind()==ReferenceKind.DEFAULT).toList();
        if(references.size()==intent.targets().size()&&java.util.stream.IntStream.range(0,references.size())
                .allMatch(i->references.get(i).target().filter(intent.targets().get(i)::equals).isPresent()))return;
        String targets=intent.targets().stream().map(target->SourceValueWriter.string(target.qualifiedName(),QuoteKind.SINGLE)).collect(Collectors.joining(", "));
        if(project.projectMode()) {
            var settings=project.settings();
            if(settings==null||!settings.complete())throw new IllegalArgumentException("EDIT_UNSAFE_REGION");
            var span=settings.directives().get("defaults");
            if(span==null) { if(!intent.targets().isEmpty())append(transaction,document,"defaults "+targets); }
            else if(intent.targets().isEmpty())removeCode(transaction,document,span,settings.tokens());
            else replaceArguments(transaction,document,span,settings.defaults().stream().map(n->n.literal().token()).toList(),settings.tokens(),targets);
        } else {
            var parsed=transaction.before.document(document).orElseThrow();
            var declarations=parsed.declarations().stream().filter(d->d.kind()==DeclarationKind.DEFAULTS).toList();
            if(declarations.stream().anyMatch(d->d.body()!=null||!d.options().isEmpty()||d.bindingName()!=null))throw new IllegalArgumentException("EDIT_UNSAFE_REGION");
            if(declarations.isEmpty()) { if(!intent.targets().isEmpty())append(transaction,document,"defaults "+targets); }
            else {
                for(int i=0;i<declarations.size();i++) {
                    var declaration=declarations.get(i);
                    if(i==0&&!intent.targets().isEmpty())replaceArguments(transaction,document,declaration.span(),declaration.targets().stream().map(ValueExpression::span).toList(),parsed.tokens(),targets);
                    else removeCode(transaction,document,declaration.statementSpan(),parsed.tokens());
                }
            }
        }
        var selected=intent.targets().stream().map(SymbolId::qualifiedName).toList();
        transaction.expect(after->after.analysis().defaultTargets().equals(selected));
    }

    private void replaceArguments(EditTransaction transaction,URI document,TextSpan call,List<TextSpan> values,TokenIndex tokens,String targets) {
        if(values.isEmpty()) {
            var significant=tokens.significant().stream().filter(t->call.contains(t.span())).toList();
            if(significant.isEmpty())throw new IllegalArgumentException("EDIT_UNSAFE_REGION");
            transaction.replace(document,significant.getFirst().span(),"defaults "+targets);
            for(var token:significant.subList(1,significant.size()))transaction.edits(document).deleteOwnedRegion(token.span());
            return;
        }
        for(var value:values)if(tokens.within(value).stream().anyMatch(t->t.channel()==Channel.COMMENT))throw new IllegalArgumentException("EDIT_COMMENT_OWNERSHIP");
        transaction.replace(document,values.getFirst(),targets);
        for(var value:values.subList(1,values.size()))transaction.edits(document).deleteOwnedRegion(value);
        for(var token:tokens.within(call))if(token.channel()==Channel.CODE&&token.text().equals(",")
                &&values.stream().noneMatch(v->v.contains(token.span())))transaction.edits(document).deleteOwnedRegion(token.span());
    }

    private void removeCode(EditTransaction transaction,URI document,TextSpan span,TokenIndex tokens) {
        for(var token:tokens.significant())if(span.contains(token.span()))transaction.edits(document).deleteOwnedRegion(token.span());
    }

    private void append(EditTransaction transaction,URI document,String text) {
        var source=transaction.source(document);
        String eol=source.preferredEol();
        boolean lineStart=source.text().isEmpty()||source.hasBom()&&source.text().length()==1||source.text().endsWith("\n")||source.text().endsWith("\r");
        transaction.edits(document).insert(source.text().length(),(lineStart?"":eol)+text+eol);
    }
}
