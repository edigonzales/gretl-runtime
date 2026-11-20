package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.DslVocabulary;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.Symbol;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.syntax.EffectiveOptions;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.Channel;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.LexToken;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** AST-owned option values with token-ledger delimiters; explicit maps never become extra map arguments. */
final class HeaderSourceEdits {
    void set(EditTransaction transaction, SymbolId target, String option, EditableValue desired) {
        set(transaction, target, Map.of(option, desired), CancellationToken.NONE);
    }

    void set(EditTransaction transaction, SymbolId target, Map<String, EditableValue> options, CancellationToken cancellation) {
        if (options.isEmpty()) return;
        var symbol = transaction.symbol(target);
        boolean complete = EffectiveOptions.of(symbol.declaration()).complete();
        var additions = new ArrayList<String>();
        for (var entry : options.entrySet()) {
            cancellation.check();
            validate(entry.getKey(), entry.getValue());
            if (!complete) throw new IllegalArgumentException("EDIT_OPTION_COVERAGE");
            set(transaction, symbol, entry.getKey(), entry.getValue(), additions);
        }
        if (!additions.isEmpty()) insert(transaction, symbol, additions);
    }

    private void set(EditTransaction transaction, Symbol symbol, String option, EditableValue desired, List<String> additions) {
        var declaration = symbol.declaration();
        var entries = declaration.options().stream().filter(e -> e.key() instanceof LiteralValue l && option.equals(l.value())).toList();
        if (entries.size() > 1) throw new IllegalArgumentException("EDIT_MULTIPLE_OPTIONS");
        var matcher = new FieldSourceEdits();
        if (entries.isEmpty() && desired instanceof EditableValue.Literal literal && literal.value() == null) return;
        if ((option.equals("dependsOn") || option.equals("after") || option.equals("finalizedBy")) && emptyLinks(desired)
                && (entries.isEmpty() || entries.getFirst().value() instanceof ListValue list && list.items().isEmpty()
                || entries.getFirst().value() instanceof LiteralValue literal && literal.value() == null)) return;
        if (!entries.isEmpty() && matcher.same(entries.getFirst().value(),desired,transaction,symbol.scope())) return;
        String text = transaction.writer(symbol).write(desired);
        if (!entries.isEmpty()) {
            var document = transaction.before.document(symbol.anchor().documentId()).orElseThrow();
            var previous = entries.getFirst().value();
            if (previous instanceof OpaqueExpression) throw new IllegalArgumentException("EDIT_UNSAFE_REGION");
            if (document.tokens().within(previous.span()).stream().anyMatch(t -> t.channel() == Channel.COMMENT)) {
                throw new IllegalArgumentException("EDIT_COMMENT_OWNERSHIP");
            }
            if (previous instanceof LiteralValue literal && literal.literal() != null && desired instanceof EditableValue.Literal next && next.value() instanceof String value) {
                text = SourceValueWriter.string(value,literal.literal().quotes());
            }
            transaction.replace(symbol.anchor().documentId(),previous.span(),text);
        } else additions.add(option + ": " + text);
        transaction.expect(after -> after.analysis().unique(symbol.id()).map(s -> {
            var entry = EffectiveOptions.of(s.declaration()).entries().get(option);
            return entry != null && matcher.same(entry.value(),desired,transaction,symbol.scope());
        }).orElse(false));
    }

    private void insert(EditTransaction transaction, Symbol symbol, List<String> additions) {
        var declaration = symbol.declaration();
        var id = symbol.anchor().documentId();
        var source = transaction.source(id);
        var tokens = transaction.before.document(id).orElseThrow().tokens().significant().stream()
                .filter(t -> declaration.header().contains(t.span())).toList();
        var map = optionMap(declaration, tokens);
        if (map == null) {
            if (tokens.size() < 2) throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
            var last = tokens.getLast();
            String text = String.join(", ", additions);
            if (tokens.get(1).text().equals("(") && last.text().equals(")")) {
                // Insert after trailing trivia so comments remain with the preceding argument.
                boolean comma = tokens.get(tokens.size() - 2).text().equals(",");
                transaction.edits(id).insert(last.span().startUtf16(), (comma ? "" : ", ") + text);
            } else {
                if (tokens.get(1).text().equals("(") && !last.text().equals(",")) throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
                int anchor = last.text().equals(",") ? last.span().startUtf16() : last.span().endUtf16();
                transaction.edits(id).insert(anchor, ", " + text);
            }
            return;
        }
        var within = tokens.stream().filter(t -> map.contains(t.span())).toList();
        LexToken close = within.getLast();
        LexToken last = within.get(within.size() - 2);
        boolean empty = declaration.options().isEmpty();
        boolean comma = last.text().equals(",");
        int closeLine = source.lines().positionAt(close.span().startUtf16()).line();
        if (source.lines().positionAt(map.startUtf16()).line() == closeLine
                || empty && (additions.size() == 1 || source.lines().positionAt(last.span().startUtf16()).line() == closeLine)) {
            String text = String.join(", ", additions);
            if (empty) transaction.replace(id, last.span(), text); // optionMap proved the empty map's colon
            else transaction.edits(id).insert(close.span().startUtf16(), (comma ? " " : ", ") + text);
            return;
        }
        int lineStart = source.lines().contentSpan(closeLine).startUtf16();
        String indent = source.text().substring(lineStart, close.span().startUtf16());
        if (!indent.chars().allMatch(c -> c == ' ' || c == '\t')) throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
        if (empty) {
            // Keep trivia on the colon's line with the first new entry.
            transaction.replace(id, last.span(), additions.getFirst() + ",");
            additions = additions.subList(1, additions.size());
        } else if (!comma) transaction.edits(id).insert(last.span().endUtf16(), ",");
        String entryIndent = indent + transaction.style.indentation();
        var keys = empty ? List.of(last.span()) : declaration.options().stream().map(entry -> entry.key().span()).toList();
        for (var key : keys) {
            int entryLine = source.lines().positionAt(key.startUtf16()).line();
            String leading = source.text().substring(source.lines().contentSpan(entryLine).startUtf16(), key.startUtf16());
            if (!leading.isEmpty() && leading.chars().allMatch(c -> c == ' ' || c == '\t')) {
                entryIndent = leading;
                break;
            }
        }
        String eol = source.preferredEol();
        transaction.edits(id).insert(lineStart, entryIndent + String.join("," + eol + entryIndent, additions) + eol);
    }

    private boolean emptyLinks(EditableValue value) {
        return value instanceof EditableValue.Literal literal && literal.value() == null
                || value instanceof EditableValue.Sequence list && list.items().isEmpty();
    }

    private void validate(String option, EditableValue value) {
        if (!DslVocabulary.taskOptions().contains(option)) throw new IllegalArgumentException("EDIT_UNKNOWN_OPTION");
        if (value instanceof EditableValue.Literal literal && literal.value() == null) return;
        switch (option) {
            case "description" -> {
                if (!(value instanceof EditableValue.Literal literal && literal.value() instanceof String text && !text.isBlank())) throw new IllegalArgumentException("EDIT_OPTION_TYPE");
            }
            case "timeout" -> {
                if (!(value instanceof EditableValue.Literal literal && literal.value() instanceof String text)) throw new IllegalArgumentException("EDIT_OPTION_TYPE");
                try { if (Duration.parse(text).isNegative() || Duration.parse(text).isZero()) throw new IllegalArgumentException("EDIT_TIMEOUT"); }
                catch (java.time.format.DateTimeParseException invalid) { throw new IllegalArgumentException("EDIT_TIMEOUT"); }
            }
            case "when" -> throw new IllegalArgumentException("EDIT_OPAQUE_CONDITION");
            case "dependsOn", "after", "finalizedBy" -> {
                List<EditableValue> values = value instanceof EditableValue.Sequence list ? list.items() : List.of(value);
                if (values.stream().anyMatch(v -> !(v instanceof EditableValue.Literal l && l.value() instanceof String))) throw new IllegalArgumentException("EDIT_OPTION_TYPE");
            }
            default -> throw new IllegalArgumentException("EDIT_UNKNOWN_OPTION");
        }
    }

    private TextSpan optionMap(Declaration declaration,List<LexToken> tokens) {
        TextSpan found = null;
        boolean assigned=false;
        for (var entry : declaration.options()) {
            var stack = new ArrayDeque<Integer>();
            for (int i=0;i<tokens.size() && tokens.get(i).span().startUtf16()<entry.key().span().startUtf16();i++) {
                if (tokens.get(i).channel()!=Channel.CODE) continue;
                if (tokens.get(i).text().equals("[")) stack.push(i);
                else if (tokens.get(i).text().equals("]") && !stack.isEmpty()) stack.pop();
            }
            TextSpan map = stack.isEmpty() ? null : matchingSquare(tokens,stack.peek());
            if (map != null && declaration.nameLiteral()!=null && map.startUtf16()>declaration.nameLiteral().token().startUtf16()) throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
            if (assigned && !java.util.Objects.equals(found,map)) throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
            assigned=true;
            found = map;
        }
        if (declaration.options().isEmpty()) {
            for (int i=0;i<tokens.size();i++) {
                var token=tokens.get(i);
                if (declaration.nameLiteral()!=null && token.span().startUtf16()>=declaration.nameLiteral().token().startUtf16()) break;
                if (token.channel()==Channel.CODE && token.text().equals("[")) {
                    var map=matchingSquare(tokens,i);
                    if (i + 2 >= tokens.size() || !tokens.get(i + 1).text().equals(":")
                            || tokens.get(i + 2).span().endUtf16() != map.endUtf16()) throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
                    return map;
                }
            }
        }
        return found;
    }

    private TextSpan matchingSquare(List<LexToken> tokens,int open) {
        int depth=0;
        for(int i=open;i<tokens.size();i++) {
            var token=tokens.get(i);
            if(token.channel()!=Channel.CODE)continue;
            if(token.text().equals("["))depth++;
            else if(token.text().equals("]")&&--depth==0)return new TextSpan(tokens.get(open).span().startUtf16(),token.span().endUtf16());
        }
        throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
    }
}
