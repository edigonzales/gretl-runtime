package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import java.util.List;
import java.util.Objects;

/** Changes one proven assignment; surrounding opaque statements and trivia are never regenerated. */
final class FieldSourceEdits {
    void set(EditTransaction transaction, SymbolId target, String field, EditableValue value) {
        var symbol = transaction.symbol(target);
        var declaration = symbol.declaration();
        var matches = declaration.fields().stream().filter(f -> f.name().equals(field)).toList();
        if (matches.size() > 1) throw new IllegalArgumentException("EDIT_MULTIPLE_ASSIGNMENTS");
        if (matches.size() == 1 && same(matches.getFirst().value(), value, transaction, symbol.scope())) {
            transaction.expect(after -> matching(after, target, field, value, transaction, symbol.scope()));
            return;
        }
        String replacement = transaction.writer(symbol).write(value);
        if (matches.size() == 1) {
            var previous = matches.getFirst().value();
            if (previous instanceof LiteralValue literal && literal.literal() != null && value instanceof EditableValue.Literal desired
                    && desired.value() instanceof String text) replacement = SourceValueWriter.string(text, literal.literal().quotes());
            rejectComments(transaction, symbol.anchor().documentId(), previous.span());
            transaction.replace(symbol.anchor().documentId(), previous.span(), replacement);
        } else {
            if (!field.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("EDIT_FIELD_SYNTAX");
            if (declaration.locals().stream().anyMatch(l -> l.name().equals(field))) throw new IllegalArgumentException("EDIT_SHADOWED_FIELD");
            var source = transaction.source(symbol.anchor().documentId());
            int close = declaration.body().endUtf16() - 1;
            if (source.text().charAt(close) != '}') throw new IllegalArgumentException("EDIT_UNSAFE_REGION");
            String eol = source.preferredEol();
            int lineStart = source.lines().contentSpan(source.lines().positionAt(close).line()).startUtf16();
            String closingIndent = source.text().substring(lineStart, close);
            boolean ownClosingLine = closingIndent.chars().allMatch(c -> c == ' ' || c == '\t');
            int insertion = ownClosingLine ? lineStart : close;
            String prefix = ownClosingLine ? "" : eol;
            String declarationLine = source.lines().lineText(source.lines().positionAt(declaration.span().startUtf16()).line());
            int begin = declarationLine.startsWith("\uFEFF") ? 1 : 0, end = begin;
            while (end < declarationLine.length() && (declarationLine.charAt(end) == ' ' || declarationLine.charAt(end) == '\t')) end++;
            String baseIndent = declarationLine.substring(begin, end);
            String indentation = baseIndent + transaction.style.indentation();
            if (!declaration.fields().isEmpty()) {
                var first = declaration.fields().getFirst();
                int line = source.lines().positionAt(first.span().startUtf16()).line();
                String leading = source.text().substring(source.lines().contentSpan(line).startUtf16(), first.span().startUtf16());
                if (!leading.isEmpty() && leading.chars().allMatch(c -> c == ' ' || c == '\t')) indentation = leading;
            }
            transaction.edits(symbol.anchor().documentId()).insert(insertion, prefix + indentation + field + " = " + replacement + eol + (ownClosingLine ? "" : baseIndent));
        }
        transaction.expect(after -> matching(after, target, field, value, transaction, symbol.scope()));
    }

    void remove(EditTransaction transaction, SymbolId target, String field) {
        var symbol = transaction.symbol(target);
        var matches = symbol.declaration().fields().stream().filter(f -> f.name().equals(field)).toList();
        if (matches.size() > 1) throw new IllegalArgumentException("EDIT_MULTIPLE_ASSIGNMENTS");
        if (!matches.isEmpty()) {
            if (symbol.operation().inputs().stream().anyMatch(i -> i.name().equals(field) && i.required() && !i.hasDefault())) {
                throw new IllegalArgumentException("EDIT_REQUIRED_FIELD");
            }
            rejectComments(transaction, symbol.anchor().documentId(), matches.getFirst().span());
            transaction.edits(symbol.anchor().documentId()).deleteOwnedRegion(matches.getFirst().span());
        }
        transaction.expect(after -> after.analysis().unique(target).map(s -> s.declaration().fields().stream()
                .noneMatch(f -> f.name().equals(field))).orElse(false));
    }

    private boolean matching(AnalysisSnapshot after, SymbolId target, String field, EditableValue desired, EditTransaction transaction, String scope) {
        return after.analysis().unique(target).map(symbol -> {
            var fields = symbol.declaration().fields().stream().filter(f -> f.name().equals(field)).toList();
            return fields.size() == 1 && same(fields.getFirst().value(), desired, transaction, scope);
        }).orElse(false);
    }

    boolean same(ValueExpression existing, EditableValue desired, EditTransaction transaction, String scope) {
        return switch (desired) {
            case EditableValue.Literal literal -> existing instanceof LiteralValue value && Objects.equals(value.value(), literal.value());
            case EditableValue.Sequence sequence -> existing instanceof ListValue list && list.items().size() == sequence.items().size()
                    && java.util.stream.IntStream.range(0, list.items().size()).allMatch(i -> same(list.items().get(i), sequence.items().get(i), transaction, scope));
            case EditableValue.Mapping mapping -> existing instanceof MapValue map && map.entries().size() == mapping.entries().size()
                    && map.entries().stream().map(e -> e.key() instanceof LiteralValue key ? key.value() : null).distinct().count() == map.entries().size()
                    && map.entries().stream().allMatch(e -> e.key() instanceof LiteralValue key && key.value() instanceof String name
                    && mapping.entries().containsKey(name) && same(e.value(), mapping.entries().get(name), transaction, scope));
            case EditableValue.ParameterRef reference -> symbolCall(existing, "parameter", reference.parameter(), transaction, scope);
            case EditableValue.JobFileRef reference -> call(existing, "file", reference.path());
            case EditableValue.WorkPathRef reference -> call(existing, "workPath", reference.path());
            case EditableValue.SecretAliasRef reference -> call(existing, "secret", reference.alias());
            case EditableValue.ResultFieldRef reference -> existing instanceof PropertyValue property
                    && property.property().equals(reference.outputField()) && symbolCall(property.receiver(), "result", reference.producer(), transaction, scope);
        };
    }

    private boolean symbolCall(ValueExpression value, String name, SymbolId target, EditTransaction transaction, String scope) {
        if (!(value instanceof CallValue call) || !call.name().equals(name) || call.arguments().size() != 1
                || !(call.arguments().getFirst() instanceof LiteralValue literal) || !(literal.value() instanceof String text)) return false;
        try {
            String qualified = transaction.before.analysis().capture().project().projectMode()
                    ? ch.so.agi.gretl.language.ProjectNameRules.parseQualified(scope).resolveReference(text) : text;
            return qualified.equals(target.qualifiedName());
        } catch (IllegalArgumentException invalidReference) { return false; }
    }

    private boolean call(ValueExpression value, String name, String argument) {
        return value instanceof CallValue call && call.name().equals(name) && call.arguments().size() == 1
                && call.arguments().getFirst() instanceof LiteralValue literal && argument.equals(literal.value());
    }

    private void rejectComments(EditTransaction transaction, java.net.URI id, ch.so.agi.gretl.tooling.source.TextSpan span) {
        var document = transaction.before.document(id).orElseThrow();
        if (document.tokens().within(span).stream().anyMatch(t -> t.channel() == ch.so.agi.gretl.tooling.syntax.TokenIndex.Channel.COMMENT)) {
            throw new IllegalArgumentException("EDIT_COMMENT_OWNERSHIP");
        }
    }
}
