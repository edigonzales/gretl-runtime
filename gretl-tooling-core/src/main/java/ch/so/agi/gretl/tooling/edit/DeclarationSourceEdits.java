package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.DslVocabulary;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.Channel;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;
import java.net.URI;
import java.util.List;

/** Inserts complete statements only at verified top-level boundaries. */
final class DeclarationSourceEdits {
    void addTask(EditTransaction transaction, SemanticEditIntent.AddTask intent) {
        var document = DocumentStore.identity(intent.document());
        var scope = scope(transaction, document);
        var id = newId(transaction, scope, intent.localName(), Namespace.TASK);
        var operations = transaction.before.analysis().capture().catalog().result().catalog().orElseThrow(() -> new IllegalArgumentException("EDIT_UNKNOWN_SCHEMA"))
                .operations().stream().filter(o -> o.operationId().equals(intent.operationId())).toList();
        if (operations.size() != 1) throw new IllegalArgumentException("EDIT_UNKNOWN_SCHEMA");
        var operation = operations.getFirst();
        identifier(operation.symbol());
        for (var field : intent.initialFields().keySet()) {
            identifier(field);
            if (DslVocabulary.reservedConfigurationFields().contains(field)
                    || operation.inputs().stream().noneMatch(i -> i.name().equals(field))) throw new IllegalArgumentException("EDIT_UNKNOWN_FIELD");
        }
        if (operation.inputs().stream().anyMatch(i -> i.required() && !i.hasDefault() && !intent.initialFields().containsKey(i.name()))) {
            throw new IllegalArgumentException("EDIT_REQUIRED_VALUES");
        }
        var source = transaction.source(document);
        String eol = source.preferredEol();
        var writer = new SourceValueWriter(target -> transaction.spelling(scope, target));
        var rendered=new java.util.LinkedHashMap<String,String>();
        var parents=new java.util.LinkedHashSet<SymbolId>();
        int fieldLength=0;
        for(var input:operation.inputs())if(intent.initialFields().containsKey(input.name())) {
            var value=intent.initialFields().get(input.name());
            String text=writer.write(value);
            fieldLength+=text.length()+input.name().length()+transaction.style.indentation().length()+eol.length()+5;
            if(fieldLength>1_048_576)throw new IllegalArgumentException("EDIT_SIZE_LIMIT");
            rendered.put(input.name(),text);
            parents.addAll(new PortCompatibility().dependencies(transaction.before,value,input.shape()));
        }
        var text = new StringBuilder("task(").append(SourceValueWriter.string(intent.localName(), QuoteKind.SINGLE))
                .append(", ").append(operation.symbol());
        if(!parents.isEmpty()) {
            var names=parents.stream().map(parent->(EditableValue)new EditableValue.Literal(transaction.spelling(scope,parent))).toList();
            text.append(", dependsOn: ").append(writer.write(names.size()==1?names.getFirst():new EditableValue.Sequence(names)));
        }
        text.append(") {").append(eol);
        for (var input : operation.inputs()) if (intent.initialFields().containsKey(input.name())) {
            String value = rendered.get(input.name());
            if ((long) text.length() + input.name().length() + value.length() + transaction.style.indentation().length() + eol.length() + 5 > 1_048_576) {
                throw new IllegalArgumentException("EDIT_SIZE_LIMIT");
            }
            text.append(transaction.style.indentation()).append(input.name()).append(" = ")
                    .append(value).append(eol);
        }
        text.append('}');
        insert(transaction, document, intent.insertionOffset(), text.toString());
        transaction.expect(after -> after.analysis().unique(id).filter(s -> s.operation() != null
                && s.operation().operationId().equals(operation.operationId()) && s.declaration().kind() == DeclarationKind.TASK).isPresent());
    }

    void addAction(EditTransaction transaction, SemanticEditIntent.AddAction intent) {
        var document = DocumentStore.identity(intent.document());
        var id = newId(transaction, scope(transaction, document), intent.localName(), Namespace.TASK);
        String eol = transaction.source(document).preferredEol();
        String text = "action(" + SourceValueWriter.string(intent.localName(), QuoteKind.SINGLE) + ") {" + eol
                + transaction.style.indentation() + "println " + SourceValueWriter.string(intent.localName(), QuoteKind.SINGLE) + eol + "}";
        insert(transaction, document, intent.insertionOffset(), text);
        transaction.expect(after -> after.analysis().unique(id).filter(s -> s.declaration().kind() == DeclarationKind.ACTION).isPresent());
    }

    void addParameter(EditTransaction transaction, SemanticEditIntent.AddParameter intent) {
        var document = DocumentStore.identity(intent.document());
        var value = intent.declaration();
        var id = newId(transaction, scope(transaction, document), value.localName(), Namespace.PARAMETER);
        if (value.description() == null || value.description().isBlank() || value.type() == null || !DslVocabulary.parameterTypes().contains(value.type())) {
            throw new IllegalArgumentException("EDIT_PARAMETER_DECLARATION");
        }
        if (value.defaultValue() != null && !(value.defaultValue() instanceof EditableValue.Literal)) {
            throw new IllegalArgumentException("EDIT_PARAMETER_DEFAULT");
        }
        if (value.required() && value.defaultValue() instanceof EditableValue.Literal literal && literal.value() != null) {
            throw new IllegalArgumentException("EDIT_PARAMETER_DEFAULT");
        }
        var writer = new SourceValueWriter(target -> transaction.spelling(scope(transaction, document), target));
        String text = "parameter(" + SourceValueWriter.string(value.localName(), QuoteKind.SINGLE)
                + ", description: " + SourceValueWriter.string(value.description(), QuoteKind.SINGLE)
                + ", type: " + SourceValueWriter.string(value.type(), QuoteKind.SINGLE) + ", required: " + value.required()
                + (value.defaultValue() == null ? "" : ", defaultValue: " + writer.write(value.defaultValue())) + ")";
        insert(transaction, document, intent.insertionOffset(), text);
        transaction.expect(after -> after.analysis().unique(id).filter(s -> s.declaration().kind() == DeclarationKind.PARAMETER).isPresent());
    }

    private String scope(EditTransaction transaction, URI document) {
        return transaction.before.analysis().capture().project().jobs().stream().filter(j -> j.documentId().equals(document))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("EDIT_JOB_DOCUMENT_REQUIRED")).scope().canonical();
    }

    private SymbolId newId(EditTransaction transaction, String scope, String name, Namespace namespace) {
        var id = SymbolNames.local(transaction.before,scope,name,namespace);
        if (transaction.before.analysis().symbols().containsKey(id)) throw new IllegalArgumentException("EDIT_NAME_COLLISION");
        return id;
    }

    private void identifier(String value) {
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("EDIT_IDENTIFIER_SYNTAX");
    }

    private void insert(EditTransaction transaction, URI id, int offset, String text) {
        var source = transaction.source(id);
        if (offset < (source.hasBom() ? 1 : 0) || offset > source.text().length()) throw new IllegalArgumentException("EDIT_INSERTION_BOUNDARY");
        if (offset > 0 && offset < source.text().length() && source.text().charAt(offset - 1) == '\r' && source.text().charAt(offset) == '\n') {
            throw new IllegalArgumentException("EDIT_INSERTION_BOUNDARY");
        }
        var document = transaction.before.document(id).orElseThrow(() -> new IllegalArgumentException("EDIT_JOB_DOCUMENT_REQUIRED"));
        if (document.declarations().stream().anyMatch(d -> d.statementSpan().startUtf16() < offset && offset < d.statementSpan().endUtf16())
                || document.opaque().stream().anyMatch(o -> o.span().startUtf16() < offset && offset < o.span().endUtf16())) {
            throw new IllegalArgumentException("EDIT_INSERTION_BOUNDARY");
        }
        var token = document.tokens().at(offset);
        if (token.isPresent() && token.get().channel() != Channel.WHITESPACE && token.get().span().startUtf16() != offset) {
            throw new IllegalArgumentException("EDIT_INSERTION_BOUNDARY");
        }
        int line = source.lines().positionAt(offset).line();
        int endOfLine = source.lines().contentSpan(line).endUtf16();
        if (document.tokens().all().stream().anyMatch(t -> t.channel() == Channel.COMMENT
                && t.span().startUtf16() >= offset && t.span().startUtf16() < endOfLine)) {
            throw new IllegalArgumentException("EDIT_INSERTION_BOUNDARY");
        }
        String eol = source.preferredEol();
        boolean startsLine = offset == 0 || source.hasBom() && offset == 1 || source.text().charAt(offset - 1) == '\n' || source.text().charAt(offset - 1) == '\r';
        String prefix = startsLine ? "" : eol;
        transaction.edits(id).insert(offset, prefix + text + eol);
    }
}
