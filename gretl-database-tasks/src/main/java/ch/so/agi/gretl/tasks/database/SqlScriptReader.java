package ch.so.agi.gretl.tasks.database;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds SQL statement boundaries in the source before applying textual parameters. */
final class SqlScriptReader {
    private static final Pattern DOLLAR_QUOTE = Pattern.compile("\\$(?:[a-zA-Z_][a-zA-Z_0-9]*)?\\$");
    private static final Pattern PARAMETER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_.-]*)}");

    private final String source;
    private int offset;

    SqlScriptReader(String source) {
        this.source = source.startsWith("\uFEFF") ? source.substring(1) : source;
    }

    String next() {
        int start = offset;
        boolean hasSql = false;
        while (offset < source.length()) {
            char current = source.charAt(offset);
            if (current == ';') {
                String statement = source.substring(start, offset++);
                if (hasSql) {
                    return statement;
                }
                start = offset;
            } else if (source.startsWith("--", offset)) {
                while (offset < source.length() && source.charAt(offset) != '\n'
                        && source.charAt(offset) != '\r') {
                    offset++;
                }
            } else if (source.startsWith("/*", offset)) {
                hasSql |= !skipBlockComment();
            } else if (current == '\'' || current == '"') {
                hasSql = true;
                skipQuoted(current);
            } else if (current == '$' && skipDollarQuote()) {
                hasSql = true;
            } else {
                hasSql |= !Character.isWhitespace(current);
                offset++;
            }
        }
        return hasSql ? source.substring(start) : null;
    }

    private boolean skipBlockComment() {
        int depth = 1;
        offset += 2;
        while (offset < source.length() && depth != 0) {
            if (source.startsWith("/*", offset)) {
                depth++;
                offset += 2;
            } else if (source.startsWith("*/", offset)) {
                depth--;
                offset += 2;
            } else {
                offset++;
            }
        }
        return depth == 0;
    }

    private void skipQuoted(char quote) {
        boolean escapes = quote == '\'' && offset > 0
                && (source.charAt(offset - 1) == 'E' || source.charAt(offset - 1) == 'e')
                && (offset == 1 || !identifierCharacter(source.charAt(offset - 2)));
        offset++;
        while (offset < source.length()) {
            char current = source.charAt(offset++);
            if (escapes && current == '\\' && offset < source.length()) {
                offset++;
            } else if (current == quote) {
                if (offset == source.length() || source.charAt(offset) != quote) {
                    return;
                }
                offset++;
            }
        }
    }

    private boolean skipDollarQuote() {
        if (offset > 0 && identifierCharacter(source.charAt(offset - 1))) {
            return false;
        }
        Matcher matcher = DOLLAR_QUOTE.matcher(source).region(offset, source.length());
        if (!matcher.lookingAt()) {
            return false;
        }
        String delimiter = matcher.group();
        int end = source.indexOf(delimiter, matcher.end());
        offset = end < 0 ? source.length() : end + delimiter.length();
        return true;
    }

    private static boolean identifierCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    static String substitute(String statement, Map<String, String> parameters,
                             java.util.function.Function<String, RuntimeException> missing) {
        Matcher matcher = PARAMETER.matcher(statement);
        StringBuilder sql = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = parameters.get(name);
            if (replacement == null) {
                throw missing.apply(name);
            }
            matcher.appendReplacement(sql, Matcher.quoteReplacement(replacement));
        }
        return matcher.appendTail(sql).toString();
    }
}
