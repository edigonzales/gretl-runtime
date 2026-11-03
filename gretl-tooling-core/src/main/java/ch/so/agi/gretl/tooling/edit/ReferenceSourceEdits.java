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
        var replaced=new HashSet<Location>();
        var declaration=original.declaration().nameLiteral();
        transaction.replace(original.anchor().documentId(),declaration.token(),SourceValueWriter.string(intent.newLocalName(),declaration.quotes()));
        replaced.add(new Location(original.anchor().documentId(),declaration.token()));
        for(var reference:references.uses()) {
            cancellation.check();
            var literal=literals.at(reference.anchor().documentId(),reference.anchor().span());
            if(literal.isEmpty())continue; // A proven TaskRef variable keeps its binding name.
            String name=replacement.qualifiedName();
            if(transaction.before.analysis().capture().project().projectMode()&&!reference.spelling().startsWith(":")) {
                String scope=reference.owner()==null?transaction.before.analysis().capture().project().jobs().stream()
                        .filter(j->j.documentId().equals(reference.anchor().documentId())).map(j->j.scope().canonical()).findFirst().orElse(":")
                        :transaction.symbol(reference.owner()).scope();
                name=ProjectNameRules.parseQualified(scope).renderReference(replacement.qualifiedName());
            }
            var envelope=literal.orElseThrow();
            if(replaced.add(new Location(reference.anchor().documentId(),envelope.token()))) {
                transaction.replace(reference.anchor().documentId(),envelope.token(),SourceValueWriter.string(name,envelope.quotes()));
            }
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
