package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.QuoteKind;
import java.util.function.Function;

/** The sole data-value to Groovy-literal boundary; never accepts a raw expression. */
public final class SourceValueWriter {
    private static final int MAXIMUM_VALUE_LENGTH = 65_536;
    private final Function<SymbolId, String> spelling;

    public SourceValueWriter(Function<SymbolId, String> spelling) {
        this.spelling = java.util.Objects.requireNonNull(spelling);
    }

    public String write(EditableValue value) {
        var out = new StringBuilder();
        write(value, 0, out);
        return out.toString();
    }

    private void append(StringBuilder out, String text) {
        if ((long) out.length() + text.length() > MAXIMUM_VALUE_LENGTH) throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
        out.append(text);
    }

    private void write(EditableValue value, int depth, StringBuilder out) {
        if (depth > 16) throw new IllegalArgumentException("EDIT_VALUE_DEPTH");
        switch (value) {
            case EditableValue.Literal literal -> append(out, literal.value() == null ? "null"
                    : literal.value() instanceof String text ? string(text, QuoteKind.SINGLE) : numberOrBoolean(literal.value()));
            case EditableValue.Sequence sequence -> {
                if (sequence.items().size() > 1024) throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
                append(out, "[");
                for (int i = 0; i < sequence.items().size(); i++) {
                    if (i > 0) append(out, ", ");
                    write(sequence.items().get(i), depth + 1, out);
                }
                append(out, "]");
            }
            case EditableValue.Mapping mapping -> {
                if (mapping.entries().size() > 1024) throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
                append(out, mapping.entries().isEmpty() ? "[:" : "[");
                int index = 0;
                for (var entry : mapping.entries().entrySet()) {
                    if (index++ > 0) append(out, ", ");
                    append(out, string(entry.getKey(), QuoteKind.SINGLE) + ": ");
                    write(entry.getValue(), depth + 1, out);
                }
                append(out, "]");
            }
            case EditableValue.ParameterRef reference -> append(out, "parameter(" + string(spelling.apply(reference.parameter()), QuoteKind.SINGLE) + ")");
            case EditableValue.ResultFieldRef reference -> append(out, "result(" + string(spelling.apply(reference.producer()), QuoteKind.SINGLE)
                    + ")." + property(reference.outputField()));
            case EditableValue.JobFileRef reference -> append(out, "file(" + string(reference.path(), QuoteKind.SINGLE) + ")");
            case EditableValue.WorkPathRef reference -> append(out, "workPath(" + string(reference.path(), QuoteKind.SINGLE) + ")");
            case EditableValue.SecretAliasRef reference -> append(out, "secret(" + string(reference.alias(), QuoteKind.SINGLE) + ")");
        }
    }

    private String numberOrBoolean(Object value) {
        if (value instanceof Long number) return number + "L";
        if (value instanceof java.math.BigInteger number) return number + "G";
        if (value instanceof java.math.BigDecimal number) {
            String text = number.toString();
            return (text.contains(".") || text.contains("E") || text.contains("e") ? text : text + "E+0") + "G";
        }
        return value.toString();
    }

    public static String property(String name) {
        return name.matches("[A-Za-z_][A-Za-z0-9_]*") ? name : string(name, QuoteKind.SINGLE);
    }

    /** Replaces a complete token exactly once; interpolation is escaped for double-quoted tokens. */
    public static String string(String value, QuoteKind preferred) {
        if (value.length() > MAXIMUM_VALUE_LENGTH) throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
        char quote = preferred == QuoteKind.DOUBLE || preferred == QuoteKind.TRIPLE_DOUBLE ? '"' : '\'';
        var out = new StringBuilder().append(quote);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == quote || c == '$' && quote == '"') out.append('\\').append(c);
            else switch (c) {
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 32 || Character.isSurrogate(c)) {
                        if (Character.isHighSurrogate(c) && i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                            out.append(c).append(value.charAt(++i));
                        } else out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else out.append(c);
                }
            }
        }
        return out.append(quote).toString();
    }
}
