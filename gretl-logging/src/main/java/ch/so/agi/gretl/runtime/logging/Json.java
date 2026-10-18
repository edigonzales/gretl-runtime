package ch.so.agi.gretl.runtime.logging;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small strict JSON reader/writer used for runtime configuration, parameter
 * files, event lines and run reports. It rejects duplicate object keys and
 * trailing content instead of silently accepting ambiguous input.
 */
public final class Json {
    private static final int MAX_DEPTH = 128;

    private Json() {
    }

    public static Object parse(String text) {
        java.util.Objects.requireNonNull(text, "text");
        Parser parser = new Parser(text);
        Object value = parser.value(0);
        parser.whitespace();
        if (!parser.done()) {
            throw parser.error("Unerwarteter Inhalt nach dem JSON-Wert");
        }
        return value;
    }

    public static Object parseFile(Path file, long maxBytes) throws IOException {
        java.util.Objects.requireNonNull(file, "file");
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        long size = Files.size(file);
        if (size > maxBytes) {
            throw new IOException("JSON file exceeds limit of " + maxBytes + " bytes: " + file);
        }
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out, 0);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder out, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("JSON nesting exceeds " + MAX_DEPTH);
        }
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            string(text, out);
        } else if (value instanceof Character character) {
            string(character.toString(), out);
        } else if (value instanceof Boolean || value instanceof Integer
                || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigDecimal) {
            out.append(value);
        } else if (value instanceof Number number) {
            if (number instanceof Double doubleValue
                    && (doubleValue.isNaN() || doubleValue.isInfinite())) {
                throw new IllegalArgumentException("JSON numbers must be finite");
            }
            if (number instanceof Float floatValue
                    && (floatValue.isNaN() || floatValue.isInfinite())) {
                throw new IllegalArgumentException("JSON numbers must be finite");
            }
            out.append(number);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                if (!first) {
                    out.append(',');
                }
                first = false;
                string(key, out);
                out.append(':');
                writeValue(entry.getValue(), out, depth + 1);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object element : iterable) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(element, out, depth + 1);
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                if (index > 0) {
                    out.append(',');
                }
                writeValue(java.lang.reflect.Array.get(value, index), out, depth + 1);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: "
                    + value.getClass().getName());
        }
    }

    private static void string(String value, StringBuilder out) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        boolean done() {
            return index >= text.length();
        }

        void whitespace() {
            while (!done()) {
                char character = text.charAt(index);
                if (character == ' ' || character == '\t'
                        || character == '\n' || character == '\r') {
                    index++;
                } else {
                    return;
                }
            }
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("JSON nesting exceeds " + MAX_DEPTH);
            }
            whitespace();
            if (done()) {
                throw error("Leeres JSON");
            }
            return switch (text.charAt(index)) {
                case '{' -> object(depth + 1);
                case '[' -> array(depth + 1);
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object(int depth) {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            while (true) {
                whitespace();
                if (!peek('"')) {
                    throw error("JSON object key must be a string");
                }
                String key = string();
                whitespace();
                expect(':');
                Object value = value(depth);
                if (result.containsKey(key)) {
                    throw error("Duplicate JSON key: " + key);
                }
                result.put(key, value);
                whitespace();
                if (peek('}')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> array(int depth) {
            expect('[');
            List<Object> result = new ArrayList<>();
            whitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            while (true) {
                result.add(value(depth));
                whitespace();
                if (peek(']')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!done()) {
                char character = text.charAt(index++);
                if (character == '"') {
                    return result.toString();
                }
                if (character == '\\') {
                    if (done()) {
                        throw error("Incomplete JSON escape sequence");
                    }
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                        case '"' -> result.append('"');
                        case '\\' -> result.append('\\');
                        case '/' -> result.append('/');
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> {
                            if (index + 4 > text.length()) {
                                throw error("Incomplete Unicode escape sequence");
                            }
                            String hex = text.substring(index, index + 4);
                            if (!hex.matches("[0-9A-Fa-f]{4}")) {
                                throw error("Invalid Unicode escape sequence: " + hex);
                            }
                            result.append((char) Integer.parseInt(hex, 16));
                            index += 4;
                        }
                        default -> throw error("Invalid JSON escape sequence: \\" + escaped);
                    }
                } else if (character < 0x20) {
                    throw error("Unkontrolliertes Steuerzeichen im JSON-String");
                } else {
                    result.append(character);
                }
            }
            throw error("Nicht abgeschlossener JSON-String");
        }

        private Object number() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            if (done()) {
                throw error("Incomplete JSON number");
            }
            if (peek('0')) {
                index++;
            } else if (isDigit(text.charAt(index))) {
                while (!done() && isDigit(text.charAt(index))) {
                    index++;
                }
            } else {
                throw error("Invalid JSON value");
            }
            boolean decimal = false;
            if (!done() && peek('.')) {
                decimal = true;
                index++;
                int digits = index;
                while (!done() && isDigit(text.charAt(index))) {
                    index++;
                }
                if (index == digits) {
                    throw error("Invalid JSON decimal number");
                }
            }
            if (!done() && (peek('e') || peek('E'))) {
                decimal = true;
                index++;
                if (!done() && (peek('+') || peek('-'))) {
                    index++;
                }
                int digits = index;
                while (!done() && isDigit(text.charAt(index))) {
                    index++;
                }
                if (index == digits) {
                    throw error("Invalid JSON exponent");
                }
            }
            String raw = text.substring(start, index);
            try {
                if (!decimal) {
                    return Long.valueOf(raw);
                }
                return new BigDecimal(raw);
            } catch (NumberFormatException exception) {
                throw error("JSON-Zahl ausserhalb des Wertebereichs: " + raw);
            }
        }

        private Object literal(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw error("Invalid JSON value");
            }
            index += literal.length();
            return value;
        }

        private void expect(char expected) {
            whitespace();
            if (done() || text.charAt(index) != expected) {
                throw error("Erwartet '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char character) {
            return !done() && text.charAt(index) == character;
        }

        private static boolean isDigit(char character) {
            return character >= '0' && character <= '9';
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " (Position " + index + ")");
        }
    }
}
