package ch.so.agi.gretl.tooling.query;

import ch.so.agi.gretl.tooling.query.QueryResults.*;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.Channel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Queries over one captured syntax/binding authority. No reparsing, acquisition or workspace evaluation. */
public final class LanguageQueries {
    public List<CompletionProposal> complete(QueryContext context) {
        return new CompletionQueries().complete(context);
    }

    public boolean completionIncomplete(QueryContext context){return new IncludePathQueries().complete(context).incomplete();}

    public Optional<SignatureInfo> signatureHelp(QueryContext context) {
        return new SignatureQueries().signatureHelp(context);
    }

    public Optional<RenamePreparation> prepareRename(QueryContext context) {
        var targets=context.snapshot().references().targetsAt(context.documentId(),context.offsetUtf16(),context.application());
        if(targets.size()!=1)return Optional.empty();
        var symbol=context.snapshot().analysis().unique(targets.iterator().next());
        if(symbol.isEmpty())return Optional.empty();
        var s=symbol.orElseThrow();
        var references=context.snapshot().references().referencesAt(context.documentId(),context.offsetUtf16(),context.application());
        var anchor=references.isEmpty()?s.anchor():references.getFirst().anchor();
        // A TaskRef variable is a different Groovy binding, not the task's literal DSL name.
        if(references.isEmpty()&&s.declaration().bindingSpan()!=null&&s.declaration().bindingSpan().contains(context.offsetUtf16()))return Optional.empty();
        if(!references.isEmpty()) {
            var token=context.tokens().flatMap(t->t.at(context.offsetUtf16()));
            if(token.isEmpty()||token.get().channel()!=Channel.STRING)return Optional.empty();
        }
        var capability=new ch.so.agi.gretl.tooling.edit.EditabilityAnalyzer().assess(context.snapshot(),
                new ch.so.agi.gretl.tooling.edit.SemanticEditIntent.RenameSymbol(s.id(),s.localName()),ch.so.agi.gretl.tooling.edit.EditStyle.defaults());
        return Optional.of(new RenamePreparation(anchor,s.localName(),s.id(),capability));
    }

    public ch.so.agi.gretl.tooling.edit.EditPlanningResult rename(QueryContext context,String newName) {
        var preparation=prepareRename(context).orElseThrow(()->new IllegalArgumentException("EDIT_RENAME_POSITION"));
        return new ch.so.agi.gretl.tooling.edit.SemanticEditPlanner().plan(context.snapshot(),
                new ch.so.agi.gretl.tooling.edit.SemanticEditIntent.RenameSymbol(preparation.target(),newName),
                ch.so.agi.gretl.tooling.edit.EditStyle.defaults(),ch.so.agi.gretl.tooling.CancellationToken.NONE);
    }

    public List<CodeFixProposal> codeActions(QueryContext context,List<DiagnosticId> ids) {
        return codeActions(context,ids,ch.so.agi.gretl.tooling.CancellationToken.NONE);
    }

    public List<CodeFixProposal> codeActions(QueryContext context,List<DiagnosticId> ids,ch.so.agi.gretl.tooling.CancellationToken cancellation) {
        var actions=new ArrayList<CodeFixProposal>();
        var analysis=context.snapshot().analysis();
        var requested=new java.util.HashSet<>(ids);
        for(var diagnostic:analysis.diagnostics()) {
            cancellation.check();if(actions.size()>=32)break;
            var id=new DiagnosticId(diagnostic.code(),diagnostic.anchor());
            if(!context.accepts(diagnostic.application())||!requested.contains(id)||!diagnostic.anchor().documentId().equals(context.documentId())||!diagnostic.code().equals("RESULT_DEPENDENCY_REQUIRED"))continue;
            for(var reference:analysis.references())if(context.accepts(reference.application())&&reference.anchor().documentId().equals(diagnostic.anchor().documentId())
                    &&diagnostic.anchor().span()!=null&&diagnostic.anchor().span().contains(reference.anchor().span())
                    &&(reference.kind()==ReferenceKind.RESULT||reference.kind()==ReferenceKind.RESULT_OPTIONAL)&&reference.target().isPresent()&&reference.owner()!=null) {
                var intent=new ch.so.agi.gretl.tooling.edit.SemanticEditIntent.AddDependency(reference.target().orElseThrow(),reference.owner(),ch.so.agi.gretl.language.PlanSemantics.EdgeKind.DEPENDS_ON);
                var result=new ch.so.agi.gretl.tooling.edit.SemanticEditPlanner().plan(context.snapshot(),intent,
                        ch.so.agi.gretl.tooling.edit.EditStyle.defaults(),cancellation);
                if(result.plan().isPresent())actions.add(new CodeFixProposal(analysis.capture().configuration().language().startsWith("de")?"Datenabhängigkeit ergänzen":"Add data dependency",id,intent));
            }
        }
        return actions.stream().distinct().toList();
    }

    public ch.so.agi.gretl.tooling.edit.EditPlanningResult applyQuickFix(QueryContext context,CodeFixProposal proposal) {
        return applyQuickFix(context,proposal,ch.so.agi.gretl.tooling.CancellationToken.NONE);
    }

    public ch.so.agi.gretl.tooling.edit.EditPlanningResult applyQuickFix(QueryContext context,CodeFixProposal proposal,ch.so.agi.gretl.tooling.CancellationToken cancellation) {
        if(!codeActions(context,List.of(proposal.diagnostic()),cancellation).contains(proposal))throw new IllegalArgumentException("EDIT_STALE_QUICK_FIX");
        return new ch.so.agi.gretl.tooling.edit.SemanticEditPlanner().plan(context.snapshot(),proposal.intent(),
                ch.so.agi.gretl.tooling.edit.EditStyle.defaults(),cancellation);
    }

    public List<Anchor> definitions(QueryContext context) {
        var includes=includeLinks(context).stream().filter(l->l.anchor().span().contains(context.offsetUtf16())).map(l->new Anchor(l.target(),new ch.so.agi.gretl.tooling.source.TextSpan(0,0))).toList();
        return includes.isEmpty()?context.snapshot().references().definitionsAt(context.documentId(), context.offsetUtf16(),context.application()):includes;
    }

    public ReferenceResult references(QueryContext context, boolean includeDeclaration) {
        var index = context.snapshot().references();
        var locations = new LinkedHashSet<Anchor>();
        boolean complete = context.snapshot().analysis().capabilities().referencesComplete();
        for (var target : index.targetsAt(context.documentId(), context.offsetUtf16(),context.application())) {
            if (includeDeclaration) context.snapshot().analysis().symbols().getOrDefault(target, List.of())
                    .forEach(s -> locations.add(s.anchor()));
            var references = index.referencesTo(target);
            complete &= references.complete();
            references.uses().forEach(r -> locations.add(r.anchor()));
        }
        return new ReferenceResult(locations.stream().sorted(Comparator.comparing((Anchor a) -> a.documentId().toString())
                .thenComparingInt(a -> a.span() == null ? -1 : a.span().startUtf16())).toList(),
                complete);
    }

    public List<OutlineNode> documentSymbols(QueryContext context) {
        return context.document().map(document -> document.declarations().stream()
                .filter(d -> d.kind() != DeclarationKind.DEFAULTS && d.kind() != DeclarationKind.INCLUDE)
                .map(d -> {
                    var children = d.invocations().stream().map(inner -> new OutlineNode(
                            inner.operation() instanceof NameValue name ? name.name() : "dynamic invocation", "INNER_INVOCATION",
                            new Anchor(context.documentId(), inner.span()), new Anchor(context.documentId(), inner.operation().span()), List.of())).toList();
                    return new OutlineNode(d.name() == null ? "dynamic " + d.kind().name().toLowerCase(java.util.Locale.ROOT) : d.name(),
                            d.kind().name(), new Anchor(context.documentId(), d.statementSpan()),
                            new Anchor(context.documentId(), d.nameLiteral() == null ? d.header() : d.nameLiteral().content()), children);
                }).toList()).orElseGet(() -> {
                    var settings = context.snapshot().analysis().capture().project().settings();
                    if (settings == null || !settings.source().stamp().documentId().equals(context.documentId())) return List.of();
                    return settings.directives().entrySet().stream().sorted(Comparator.comparingInt(e -> e.getValue().startUtf16()))
                            .map(e -> new OutlineNode(e.getKey(), "SETTING", new Anchor(context.documentId(), e.getValue()),
                                    new Anchor(context.documentId(), e.getValue()), List.of())).toList();
                });
    }

    public List<FileLink> documentLinks(QueryContext context) {
        var capture = context.snapshot().analysis().capture();
        return java.util.stream.Stream.concat(includeLinks(context).stream(),context.snapshot().analysis().paths().stream()
                .filter(p -> p.anchor().documentId().equals(context.documentId()) && p.kind() == PathKind.JOB_RESOURCE
                        && p.resourceId() != null && capture.readPolicy().permits(p.resourceId())&&context.accepts(p.application()))
                .map(p -> new FileLink(p.anchor(), p.resourceId()))).distinct().toList();
    }

    private List<FileLink> includeLinks(QueryContext context){
        var project=context.snapshot().analysis().capture().project();var document=context.document().orElse(null);if(document==null)return List.of();
        var links=new ArrayList<FileLink>();
        for(var directive:document.includes().directives()){
            var matches=project.includeClosure().links().stream().filter(l->l.site().sourceDocument().equals(context.documentId())&&l.site().startUtf16()==directive.start()
                    &&l.resolution()==ch.so.agi.gretl.tooling.workspace.IncludeModel.IncludeResolution.RESOLVED).map(l->l.targetDocument()).distinct().toList();
            if(matches.size()==1)links.add(new FileLink(new Anchor(context.documentId(),new ch.so.agi.gretl.tooling.source.TextSpan(directive.literalStart(),directive.literalEnd())),matches.getFirst()));
        }
        return List.copyOf(links);
    }

    public List<FoldingRegion> foldingRanges(QueryContext context) {
        var regions = new ArrayList<FoldingRegion>();
        context.document().ifPresent(document -> {
            for (var declaration : document.declarations()) {
                if (declaration.body() != null) regions.add(new FoldingRegion(new Anchor(context.documentId(), declaration.body()), "region"));
                for (var inner : declaration.invocations()) if (inner.body() != null) regions.add(new FoldingRegion(new Anchor(context.documentId(), inner.body()), "region"));
            }
        });
        context.tokens().ifPresent(tokens -> tokens.all().stream().filter(t -> t.channel() == Channel.COMMENT)
                .forEach(t -> regions.add(new FoldingRegion(new Anchor(context.documentId(), t.span()), "comment"))));
        return regions.stream().filter(r -> r.anchor().span() != null
                && context.source().lines().positionAt(r.anchor().span().endUtf16()).line()
                > context.source().lines().positionAt(r.anchor().span().startUtf16()).line()).distinct()
                .sorted(Comparator.comparingInt(r -> r.anchor().span().startUtf16())).toList();
    }

    public Optional<HoverInfo> hover(QueryContext context) {
        var analysis = context.snapshot().analysis();
        var document=context.document().orElse(null);
        if(document!=null)for(var declaration:document.declarations())for(var property:declaration.properties()) {
            if(!property.propertySpan().contains(context.offsetUtf16())
                    ||!(property.receiver() instanceof CallValue call)||!call.name().equals("result")
                    ||call.arguments().size()!=1||!(call.arguments().getFirst() instanceof LiteralValue literal)
                    ||!(literal.value() instanceof String))continue;
            var references=analysis.references().stream().filter(reference->reference.kind()==ReferenceKind.RESULT
                    &&context.accepts(reference.application())&&reference.anchor().documentId().equals(context.documentId())
                    &&call.span().contains(reference.anchor().span())).toList();
            if(references.size()!=1)return Optional.empty();
            return references.getFirst().target().flatMap(analysis::unique).filter(producer->producer.operation()!=null)
                    .flatMap(producer->producer.operation().outputs().stream().filter(output->output.name().equals(property.property())).findFirst())
                    .map(output->new HoverInfo(new Anchor(context.documentId(),property.propertySpan()),
                            output.name()+" : "+output.shape().runtimeType()+(output.nullable()?"?":""),output.description()));
        }
        var targets = context.snapshot().references().targetsAt(context.documentId(), context.offsetUtf16(),context.application());
        if (targets.size() == 1) {
            var symbol = analysis.unique(targets.iterator().next());
            if (symbol.isPresent()) {
                var s = symbol.get();
                String documentation = s.operation() == null ? s.declaration().kind().name() : s.operation().description();
                var reference = context.snapshot().references().referencesAt(context.documentId(), context.offsetUtf16(),context.application());
                var anchor = reference.isEmpty() ? s.anchor() : reference.getFirst().anchor();
                if (reference.isEmpty() && s.declaration().bindingSpan() != null && s.declaration().bindingSpan().contains(context.offsetUtf16())) {
                    anchor = new Anchor(context.documentId(), s.declaration().bindingSpan());
                }
                return Optional.of(new HoverInfo(anchor, s.id().qualifiedName(), documentation+(s.application()==null?"":"\nContext: "+s.scope()+" · "+s.application().id().entryDocument()+"\nSource: "+s.anchor().documentId())));
            }
        }
        for (var symbols : analysis.symbols().values()) for (var symbol : symbols) {
            if(!context.accepts(symbol))continue;
            if(symbol.anchor().documentId().equals(context.documentId()))for(var inner:symbol.declaration().invocations()) {
                if(inner.operation() instanceof NameValue name) {
                    var operation=analysis.capture().catalog().result().catalog().stream().flatMap(c->c.operations().stream()).filter(o->o.symbol().equals(name.name())).findFirst();
                    if(operation.isPresent()&&inner.operation().span().contains(context.offsetUtf16()))return Optional.of(new HoverInfo(new Anchor(context.documentId(),inner.operation().span()),name.name(),operation.get().description()));
                    if(operation.isPresent())for(var field:inner.fields())if(field.nameSpan().contains(context.offsetUtf16())) {
                        var input=operation.get().inputs().stream().filter(i->i.name().equals(field.name())).findFirst();
                        if(input.isPresent())return Optional.of(new HoverInfo(new Anchor(context.documentId(),field.nameSpan()),input.get().name()+" : "+input.get().shape().runtimeType(),input.get().description()));
                    }
                }
            }
            if (!symbol.anchor().documentId().equals(context.documentId()) || symbol.operation() == null) continue;
            var operation = symbol.operation();
            var syntax = symbol.declaration().operation();
            if (syntax != null && syntax.span().contains(context.offsetUtf16())) {
                return Optional.of(new HoverInfo(new Anchor(context.documentId(), syntax.span()), operation.symbol(), operation.description()));
            }
            for (var field : symbol.declaration().fields()) if (field.nameSpan().contains(context.offsetUtf16())) {
                var descriptor = operation.inputs().stream().filter(i -> i.name().equals(field.name())).findFirst();
                if (descriptor.isPresent()) {
                    var d = descriptor.get();
                    return Optional.of(new HoverInfo(new Anchor(context.documentId(), field.nameSpan()), d.name() + " : " + d.shape().runtimeType(), d.description()));
                }
            }
        }
        return Optional.empty();
    }
}
