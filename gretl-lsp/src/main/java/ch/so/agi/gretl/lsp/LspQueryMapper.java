package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.edit.PlannedEdit;
import ch.so.agi.gretl.tooling.query.QueryResults.*;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.Anchor;
import ch.so.agi.gretl.tooling.source.*;
import java.net.URI;
import java.util.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/** UTF-16 protocol mapping only. Missing anchors are omitted, never invented at line one. */
final class LspQueryMapper {
    final AnalysisSnapshot snapshot;
    LspQueryMapper(AnalysisSnapshot snapshot){this.snapshot=snapshot;}
    int offset(URI document,Position position) {
        if(position==null||position.getLine()<0||position.getCharacter()<0)throw new IllegalArgumentException("QUERY_POSITION");
        var source=snapshot.source(document).orElseThrow(()->new IllegalArgumentException("SOURCE_NOT_CAPTURED"));
        var p=new TextPosition(position.getLine(),position.getCharacter()+bomShift(source,position.getLine()));
        return source.lines().offsetAt(p);
    }
    Optional<Range> range(Anchor anchor) {
        if(anchor==null||anchor.span()==null)return Optional.empty();
        return snapshot.source(anchor.documentId()).filter(s->anchor.span().endUtf16()<=s.text().length()).map(s->new Range(position(s,anchor.span().startUtf16()),position(s,anchor.span().endUtf16())));
    }
    Range range(URI id,TextSpan span){return range(new Anchor(id,span)).orElseThrow(()->new IllegalArgumentException("SOURCE_RANGE_UNAVAILABLE"));}
    private Position position(SourceText source,int offset) {
        var position=source.lines().positionAt(offset);return new Position(position.line(),Math.max(0,position.character()-bomShift(source,position.line())));
    }
    private int bomShift(SourceText source,int line){return line==0&&source.hasBom()&&source.stamp().bufferVersion()==null?1:0;}
    Optional<Location> location(Anchor anchor){return range(anchor).map(r->new Location(anchor.documentId().toASCIIString(),r));}
    CompletionItem completion(URI document,CompletionProposal proposal) {
        var item=new CompletionItem(proposal.label());item.setDetail(proposal.detail());
        item.setKind(switch(proposal.kind()){case "CLASS"->CompletionItemKind.Class;case "FIELD"->CompletionItemKind.Field;case "PROPERTY"->CompletionItemKind.Property;case "ENUM"->CompletionItemKind.EnumMember;case "FILE"->CompletionItemKind.File;case "KEYWORD"->CompletionItemKind.Keyword;default->CompletionItemKind.Value;});
        item.setFilterText(ch.so.agi.gretl.tooling.edit.SourceValueWriter.completionFilter(proposal.label(),snapshot.source(document).orElseThrow().slice(proposal.replacement())));
        item.setInsertTextFormat(InsertTextFormat.PlainText);item.setTextEdit(Either.forLeft(new TextEdit(range(document,proposal.replacement()),proposal.newText())));return item;
    }
    org.eclipse.lsp4j.Hover hover(HoverInfo hover,boolean markdown) {
        var content=new MarkupContent();content.setKind(markdown?MarkupKind.MARKDOWN:MarkupKind.PLAINTEXT);
        content.setValue(hover.title()+"\n\n"+Objects.requireNonNullElse(hover.documentation(),""));
        var result=new org.eclipse.lsp4j.Hover(content);range(hover.anchor()).ifPresent(result::setRange);return result;
    }
    DocumentSymbol symbol(OutlineNode node) {
        var result=new DocumentSymbol();result.setName(node.name());result.setKind(switch(node.kind()){case "PARAMETER"->SymbolKind.Variable;case "SETTING"->SymbolKind.Property;default->SymbolKind.Function;});
        result.setRange(range(node.range()).orElseThrow());result.setSelectionRange(range(node.selection()).orElseThrow());
        result.setChildren(node.children().stream().filter(n->range(n.range()).isPresent()&&range(n.selection()).isPresent()).map(this::symbol).toList());return result;
    }
    WorkspaceEdit edit(PlannedEdit plan) {
        var changes=new ArrayList<Either<TextDocumentEdit,ResourceOperation>>();
        for(var entry:plan.changes().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var source=snapshot.source(entry.getKey()).orElseThrow();
            var document=new VersionedTextDocumentIdentifier();document.setUri(entry.getKey().toASCIIString());document.setVersion(source.stamp().bufferVersion());
            var edit=new TextDocumentEdit();edit.setTextDocument(document);
            edit.setEdits(entry.getValue().stream().map(e->Either.<TextEdit,SnippetTextEdit>forLeft(new TextEdit(range(entry.getKey(),e.span()),e.replacement()))).toList());
            changes.add(Either.forLeft(edit));
        }
        var result=new WorkspaceEdit();result.setDocumentChanges(changes);return result;
    }
}
