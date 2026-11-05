package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.edit.*;
import ch.so.agi.gretl.tooling.query.*;
import ch.so.agi.gretl.tooling.query.QueryResults;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.source.TextPosition;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.*;
import org.eclipse.lsp4j.services.TextDocumentService;

final class GretlTextDocumentService implements TextDocumentService {
    private final GretlLanguageServer server;
    private final LanguageQueries queries=new LanguageQueries();
    GretlTextDocumentService(GretlLanguageServer server){this.server=server;}
    private QueryContext context(AnalysisSnapshot snapshot,String uri,Position position){URI id=URI.create(uri);return new QueryContext(snapshot,id,new LspQueryMapper(snapshot).offset(id,position),server.workspace().includeTooling&&!server.workspace().sourceContexts.contextsFor(snapshot.analysis().capture(),id).contexts().isEmpty()?server.workspace().sourceContexts.resolveForQuery(snapshot.analysis().capture(),id,null).application().id():new QueryContext(snapshot,id,0).application());}
    private QueryContext physicalContext(AnalysisSnapshot snapshot,String uri){
        var id=URI.create(uri);var capture=snapshot.analysis().capture();
        if(server.workspace().includeTooling&&server.workspace().sourceContexts.contextsFor(capture,id).selectedToken()!=null)
            return new QueryContext(snapshot,id,0,server.workspace().sourceContexts.resolveForQuery(capture,id,null).application().id());
        return new QueryContext(snapshot,id,0);
    }
    public void didOpen(DidOpenTextDocumentParams params){server.notification(()-> {
        var document=params.getTextDocument();var id=server.workspace().document(document.getUri());
        if(!server.workspace().knownMember(id)&&!Set.of("gretl-runtime","gretl","sql").contains(document.getLanguageId())&&!id.getPath().endsWith("/settings.gretl")&&!id.getPath().endsWith("/job.groovy"))throw new IllegalArgumentException("DOCUMENT_NOT_SELECTED");
        server.workspace().documents.open(id,document.getLanguageId(),document.getVersion(),document.getText(),LspHostSourceRepository.writable(id,server.workspace().rootFor(id)));server.workspace().changed();
    });}
    public void didChange(DidChangeTextDocumentParams params){server.notification(()-> {
        if(params.getContentChanges().size()>256)throw new IllegalArgumentException("CHANGE_LIMIT");
        var changes=params.getContentChanges().stream().map(change->change.getRange()==null?DocumentStore.TextChange.full(change.getText()):
                new DocumentStore.TextChange(new TextPosition(change.getRange().getStart().getLine(),change.getRange().getStart().getCharacter()),new TextPosition(change.getRange().getEnd().getLine(),change.getRange().getEnd().getCharacter()),change.getText())).toList();
        server.workspace().documents.applyChanges(server.workspace().document(params.getTextDocument().getUri()),params.getTextDocument().getVersion(),changes);server.workspace().changed();
    });}
    public void didClose(DidCloseTextDocumentParams params){server.notification(()-> {
        var id=server.workspace().document(params.getTextDocument().getUri());server.workspace().documents.close(id);server.workspace().changed();
    });}
    public void didSave(DidSaveTextDocumentParams params){server.notification(()->server.workspace().sourcesChanged(Set.of(server.workspace().document(params.getTextDocument().getUri()))));}
    public CompletableFuture<Either<List<CompletionItem>,CompletionList>> completion(CompletionParams params){return server.query(params.getTextDocument().getUri(),(snapshot,cancel)-> {
        var context=context(snapshot,params.getTextDocument().getUri(),params.getPosition());var mapper=new LspQueryMapper(snapshot);
        var items=queries.complete(context).stream().map(p->mapper.completion(context.documentId(),p)).toList();
        return queries.completionIncomplete(context)?Either.forRight(new CompletionList(true,items)):Either.forLeft(items);
    });}
    public CompletableFuture<Hover> hover(HoverParams params){return server.query(params.getTextDocument().getUri(),(snapshot,cancel)->queries.hover(context(snapshot,params.getTextDocument().getUri(),params.getPosition())).map(info->new LspQueryMapper(snapshot).hover(info,server.markdown())).orElse(null));}
    public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params){return server.query(params.getTextDocument().getUri(),(snapshot,cancel)-> {
        var info=queries.signatureHelp(context(snapshot,params.getTextDocument().getUri(),params.getPosition()));if(info.isEmpty())return null;
        var value=info.orElseThrow();var signature=new SignatureInformation();signature.setLabel(value.label());signature.setDocumentation(value.documentation());
        signature.setParameters(value.parameters().stream().map(p->{var parameter=new ParameterInformation();parameter.setLabel(p);return parameter;}).toList());
        var result=new SignatureHelp();result.setSignatures(List.of(signature));result.setActiveSignature(0);result.setActiveParameter(value.activeParameter());return result;
    });}
    public CompletableFuture<Either<List<? extends Location>,List<? extends LocationLink>>> definition(DefinitionParams params){return server.query(params.getTextDocument().getUri(),(snapshot,cancel)->Either.forLeft(queries.definitions(context(snapshot,params.getTextDocument().getUri(),params.getPosition())).stream().map(new LspQueryMapper(snapshot)::location).flatMap(Optional::stream).toList()));}
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params){return server.query(params.getTextDocument().getUri(),(snapshot,cancel)->queries.references(context(snapshot,params.getTextDocument().getUri(),params.getPosition()),params.getContext()!=null&&params.getContext().isIncludeDeclaration()).locations().stream().map(new LspQueryMapper(snapshot)::location).flatMap(Optional::stream).toList());}
    public CompletableFuture<List<Either<SymbolInformation,DocumentSymbol>>> documentSymbol(DocumentSymbolParams params){return server.physicalQuery(params.getTextDocument().getUri(),(snapshot,cancel)-> {
        var mapper=new LspQueryMapper(snapshot);var result=new ArrayList<Either<SymbolInformation,DocumentSymbol>>();
        for(var node:queries.documentSymbols(new QueryContext(snapshot,URI.create(params.getTextDocument().getUri()),0))) {
            cancel.check();if(mapper.range(node.range()).isEmpty()||mapper.range(node.selection()).isEmpty())continue;
            if(server.hierarchicalSymbols())result.add(Either.forRight(mapper.symbol(node)));else flatSymbol(node,null,mapper,result);
        }
        return List.copyOf(result);
    });}
    private void flatSymbol(QueryResults.OutlineNode node,String container,LspQueryMapper mapper,List<Either<SymbolInformation,DocumentSymbol>> result) {
        if(mapper.range(node.range()).isEmpty()||mapper.location(node.selection()).isEmpty())return;
        var symbol=new SymbolInformation();symbol.setName(node.name());symbol.setKind(mapper.symbol(node).getKind());symbol.setLocation(mapper.location(node.selection()).orElseThrow());symbol.setContainerName(container);result.add(Either.forLeft(symbol));
        for(var child:node.children())flatSymbol(child,node.name(),mapper,result);
    }
    public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params){return server.physicalQuery(params.getTextDocument().getUri(),(snapshot,cancel)-> {
        var mapper=new LspQueryMapper(snapshot);return queries.documentLinks(physicalContext(snapshot,params.getTextDocument().getUri())).stream().filter(link->mapper.range(link.anchor()).isPresent()).map(link->{var result=new DocumentLink();result.setRange(mapper.range(link.anchor()).orElseThrow());result.setTarget(link.target().toString());return result;}).toList();
    });}
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params){return server.physicalQuery(params.getTextDocument().getUri(),(snapshot,cancel)-> {
        var mapper=new LspQueryMapper(snapshot);return queries.foldingRanges(new QueryContext(snapshot,URI.create(params.getTextDocument().getUri()),0)).stream().filter(f->mapper.range(f.anchor()).isPresent()).map(f->{var range=mapper.range(f.anchor()).orElseThrow();var result=new FoldingRange();result.setStartLine(range.getStart().getLine());result.setEndLine(range.getEnd().getLine());if(!server.lineFoldingOnly()){result.setStartCharacter(range.getStart().getCharacter());result.setEndCharacter(range.getEnd().getCharacter());}result.setKind(f.kind());return result;}).toList();
    });}
    public CompletableFuture<Either3<Range,PrepareRenameResult,PrepareRenameDefaultBehavior>> prepareRename(PrepareRenameParams params){return server.query(params.getTextDocument().getUri(),(snapshot,cancel)-> {
        server.ensureEdits();server.workspace().requireMembershipReady(snapshot);var preparation=queries.prepareRename(context(snapshot,params.getTextDocument().getUri(),params.getPosition())).orElseThrow(()->new IllegalArgumentException("EDIT_RENAME_POSITION"));
        if(preparation.capability().state()!=EditCapability.State.ALLOWED)throw new IllegalArgumentException(preparation.capability().reasons().getFirst().code());
        var result=new PrepareRenameResult();result.setRange(new LspQueryMapper(snapshot).range(preparation.anchor()).orElseThrow());result.setPlaceholder(preparation.placeholder());return Either3.forSecond(result);
    });}
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params){return server.query(params.getTextDocument().getUri(),(snapshot,cancel)-> {
        server.ensureEdits();server.workspace().requireMembershipReady(snapshot);var preparation=queries.prepareRename(context(snapshot,params.getTextDocument().getUri(),params.getPosition())).orElseThrow(()->new IllegalArgumentException("EDIT_RENAME_POSITION"));
        var result=new SemanticEditPlanner().plan(snapshot,new SemanticEditIntent.RenameSymbol(preparation.target(),params.getNewName()),EditStyle.defaults(),cancel);
        var plan=result.plan().orElseThrow(()->new IllegalArgumentException(result.capability().reasons().getFirst().code()));server.workspace().requireWritableTargets(plan);return new LspQueryMapper(snapshot).edit(plan);
    });}
    public CompletableFuture<List<Either<Command,CodeAction>>> codeAction(CodeActionParams params){return server.query(params.getTextDocument().getUri(),(snapshot,cancel)-> {
        server.ensureEdits();server.workspace().requireMembershipReady(snapshot);if(!server.codeActionLiterals())return List.of();var mapper=new LspQueryMapper(snapshot);var context=context(snapshot,params.getTextDocument().getUri(),params.getRange().getStart());
        var diagnostics=snapshot.analysis().diagnostics().stream().filter(d->d.anchor().documentId().equals(context.documentId())&&mapper.range(d.anchor()).filter(range->intersects(params.getRange(),range)).isPresent()).map(d->new QueryResults.DiagnosticId(d.code(),d.anchor())).toList();
        return queries.codeActions(context,diagnostics,cancel).stream().map(proposal->{cancel.check();var planned=queries.applyQuickFix(context,proposal,cancel).plan().orElseThrow();server.workspace().requireWritableTargets(planned);var action=new CodeAction();action.setTitle(proposal.title());action.setKind(CodeActionKind.QuickFix);action.setEdit(mapper.edit(planned));return Either.<Command,CodeAction>forRight(action);}).toList();
    });}
    private boolean intersects(Range left,Range right){return compare(left.getStart(),right.getEnd())<=0&&compare(right.getStart(),left.getEnd())<=0;}
    private int compare(Position left,Position right){int line=Integer.compare(left.getLine(),right.getLine());return line==0?Integer.compare(left.getCharacter(),right.getCharacter()):line;}
}
