package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.ProjectNameRules;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.TextSpan;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class ReferenceSourceEdits {
    void rename(EditTransaction transaction,SemanticEditIntent.RenameSymbol intent,CancellationToken cancellation) {
        var original=transaction.symbol(intent.target());
        var replacement=SymbolNames.local(transaction.before,original.scope(),intent.newLocalName(),intent.target().namespace());
        if(replacement.equals(intent.target()))return;
        transaction.literalRename=true;
        if(transaction.before.analysis().symbols().containsKey(replacement))throw new IllegalArgumentException("EDIT_NAME_COLLISION");
        var references=transaction.before.references().referencesTo(intent.target());
        if(!transaction.before.analysis().capabilities().declarationsComplete()||!references.complete())throw new IllegalArgumentException("EDIT_REFERENCE_COVERAGE");
        new AliasUseGuard().check(transaction.before,original,false);
        if(original.declaration().nameLiteral()==null||original.declaration().recovered())throw new IllegalArgumentException("EDIT_UNSAFE_REGION");
        var literals=new SyntaxLiteralIndex(transaction.before,cancellation);
        record Location(URI document,TextSpan span) { }
        var replaced=new java.util.LinkedHashMap<Location,String>();
        var declaration=original.declaration().nameLiteral();
        transaction.replace(original.anchor().documentId(),declaration.token(),SourceValueWriter.string(intent.newLocalName(),declaration.quotes()));
        replaced.put(new Location(original.anchor().documentId(),declaration.token()),SourceValueWriter.string(intent.newLocalName(),declaration.quotes()));
        for(var reference:references.uses()) {
            cancellation.check();
            var literal=literals.at(reference.anchor().documentId(),reference.anchor().span());
            if(literal.isEmpty())continue; // A proven TaskRef variable keeps its binding name.
            String name=replacement.qualifiedName();
            if(transaction.before.analysis().capture().project().projectMode()&&!reference.spelling().startsWith(":")) {
                String scope=reference.application()!=null?reference.application().ownerScope():reference.owner()==null?":":transaction.symbol(reference.owner()).scope();
                name=ProjectNameRules.parseQualified(scope).renderReference(replacement.qualifiedName());
            }
            var envelope=literal.orElseThrow();
            String text=SourceValueWriter.string(name,envelope.quotes());var location=new Location(reference.anchor().documentId(),envelope.token());
            String previous=replaced.putIfAbsent(location,text);if(previous!=null&&!previous.equals(text))throw new IllegalArgumentException("EDIT_CONFLICTING_REPLACEMENTS");
            if(previous==null)transaction.replace(location.document(),location.span(),text);
        }
        var changes=transaction.changes();
        transaction.expect(after->after.analysis().unique(replacement).isPresent()&&!after.analysis().symbols().containsKey(intent.target())
                &&new BindingPreservation().verify(transaction.before,after,changes,Map.of(intent.target(),replacement),Set.of(),cancellation));
        if(intent.target().namespace()==Namespace.PARAMETER)transaction.notice(transaction.before.analysis().capture().configuration().language().startsWith("de")
                ?"Externe CLI-Aufrufer dieses Parameters werden nicht automatisch aktualisiert.":"External CLI callers of this parameter are not updated automatically.");
    }

    void delete(EditTransaction transaction,SemanticEditIntent.DeleteTask intent,CancellationToken cancellation) {
        if(intent.target().namespace()!=Namespace.TASK)throw new IllegalArgumentException("EDIT_TARGET_KIND");
        var symbol=transaction.symbol(intent.target());
        var references=transaction.before.references().referencesTo(intent.target());
        if(!transaction.before.analysis().capabilities().declarationsComplete()||!references.complete())throw new IllegalArgumentException("EDIT_REFERENCE_COVERAGE");
        if(!references.uses().isEmpty())throw new IllegalArgumentException("EDIT_REFERENCED_TARGET");
        new AliasUseGuard().check(transaction.before,symbol,true);
        if(symbol.declaration().recovered())throw new IllegalArgumentException("EDIT_UNSAFE_REGION");
        transaction.edits(symbol.anchor().documentId()).deleteOwnedRegion(symbol.declaration().statementSpan());
        var changes=transaction.changes();
        transaction.expect(after->!after.analysis().symbols().containsKey(intent.target())
                &&new BindingPreservation().verify(transaction.before,after,changes,Map.of(),Set.of(intent.target()),cancellation));
    }
}
