package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.DslVocabulary;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.syntax.EffectiveOptions;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.Channel;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.LexToken;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;

/** AST-owned option values with token-ledger delimiters; explicit maps never become extra map arguments. */
final class HeaderSourceEdits {
    void set(EditTransaction transaction, SymbolId target, String option, EditableValue desired) {
        validate(option,desired);
        var symbol = transaction.symbol(target);
        var declaration = symbol.declaration();
        var effective = EffectiveOptions.of(declaration);
        if (!effective.complete()) throw new IllegalArgumentException("EDIT_OPTION_COVERAGE");
        var entries = declaration.options().stream().filter(e -> e.key() instanceof LiteralValue l && option.equals(l.value())).toList();
        if (entries.size() > 1) throw new IllegalArgumentException("EDIT_MULTIPLE_OPTIONS");
        var matcher = new FieldSourceEdits();
        if (entries.isEmpty() && desired instanceof EditableValue.Literal literal && literal.value() == null) return;
        if ((option.equals("dependsOn") || option.equals("after") || option.equals("finalizedBy")) && emptyLinks(desired)
                && (entries.isEmpty() || entries.getFirst().value() instanceof ListValue list && list.items().isEmpty()
                || entries.getFirst().value() instanceof LiteralValue literal && literal.value() == null)) return;
        if (!entries.isEmpty() && matcher.same(entries.getFirst().value(),desired,transaction,symbol.scope())) return;
        String text = transaction.writer(symbol).write(desired);
        var source = transaction.source(symbol.anchor().documentId());
        var document = transaction.before.document(symbol.anchor().documentId()).orElseThrow();
        if (!entries.isEmpty()) {
            var previous = entries.getFirst().value();
            if (previous instanceof OpaqueExpression) throw new IllegalArgumentException("EDIT_UNSAFE_REGION");
            if (document.tokens().within(previous.span()).stream().anyMatch(t -> t.channel() == Channel.COMMENT)) {
                throw new IllegalArgumentException("EDIT_COMMENT_OWNERSHIP");
            }
            if (previous instanceof LiteralValue literal && literal.literal() != null && desired instanceof EditableValue.Literal next && next.value() instanceof String value) {
                text = SourceValueWriter.string(value,literal.literal().quotes());
            }
            transaction.replace(symbol.anchor().documentId(),previous.span(),text);
        } else {
            var tokens = document.tokens().significant().stream().filter(t -> declaration.header().contains(t.span())).toList();
            var map = optionMap(declaration,tokens);
            if (map != null) {
                var within = tokens.stream().filter(t -> map.contains(t.span())).toList();
                LexToken close = within.getLast();
                boolean empty = declaration.options().isEmpty();
                if (empty) {
                    var colon = within.stream().filter(t -> t.text().equals(":")).findFirst().orElseThrow(() -> new IllegalArgumentException("EDIT_UNSAFE_HEADER"));
                    transaction.replace(symbol.anchor().documentId(),colon.span(),option+": "+text);
                } else {
                boolean comma = within.size() > 1 && within.get(within.size()-2).text().equals(",");
                String prefix = empty || comma ? "" : ", ";
                int startLine = source.lines().positionAt(map.startUtf16()).line();
                int closeLine = source.lines().positionAt(close.span().startUtf16()).line();
                if (startLine != closeLine) {
                    int lineStart = source.lines().contentSpan(closeLine).startUtf16();
                    String indent = source.text().substring(lineStart,close.span().startUtf16());
                    if (!indent.chars().allMatch(c -> c == ' ' || c == '\t')) throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
                    if (!empty && !comma) transaction.edits(symbol.anchor().documentId()).insert(within.get(within.size()-2).span().endUtf16(),",");
                    String entryIndent=indent+transaction.style.indentation();
                    for(var entry:declaration.options()) {
                        int entryLine=source.lines().positionAt(entry.key().span().startUtf16()).line();
                        String leading=source.text().substring(source.lines().contentSpan(entryLine).startUtf16(),entry.key().span().startUtf16());
                        if(!leading.isEmpty()&&leading.chars().allMatch(c->c==' '||c=='\t')){entryIndent=leading;break;}
                    }
                    transaction.edits(symbol.anchor().documentId()).insert(lineStart,entryIndent+option+": "+text+source.preferredEol());
                } else transaction.edits(symbol.anchor().documentId()).insert(close.span().startUtf16(),prefix+option+": "+text);
                }
            } else {
                int close = callClose(tokens);
                if (close >= 0) transaction.edits(symbol.anchor().documentId()).insert(close,", "+option+": "+text);
                else {
                    if (tokens.isEmpty()) throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
                    var last = tokens.getLast();
                    int anchor = last.text().equals(",") ? last.span().startUtf16() : last.span().endUtf16();
                    transaction.edits(symbol.anchor().documentId()).insert(anchor,", "+option+": "+text);
                }
            }
        }
        transaction.expect(after -> after.analysis().unique(target).map(s -> {
            var entry = EffectiveOptions.of(s.declaration()).entries().get(option);
            return entry != null && matcher.same(entry.value(),desired,transaction,symbol.scope());
        }).orElse(false));
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
                    var inner=tokens.stream().filter(t->map.contains(t.span())&&!t.text().equals("[")&&!t.text().equals("]")).toList();
                    if (inner.size()!=1 || !inner.getFirst().text().equals(":")) throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
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

    private int callClose(List<LexToken> tokens) {
        if(tokens.size()<2||!tokens.get(1).text().equals("("))return -1;
        int depth=0;
        for(int i=1;i<tokens.size();i++) {
            var token=tokens.get(i);
            if(token.channel()!=Channel.CODE)continue;
            if(token.text().equals("("))depth++;
            else if(token.text().equals(")")&&--depth==0)return token.span().startUtf16();
        }
        if(!tokens.isEmpty()&&tokens.getLast().text().equals(","))return -1;
        throw new IllegalArgumentException("EDIT_UNSAFE_HEADER");
    }
}
