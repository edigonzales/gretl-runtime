package ch.so.agi.gretl.internal.shapefile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ShapefileFieldNames {
    private static final int MAX_DBF_FIELD_NAME_BYTES = 10;

    private final Map<String, String> usedByLowerName = new LinkedHashMap<>();

    public String map(String sourceName) {
        String base = asciiIdentifier(sourceName);
        String candidate = truncateAscii(base, MAX_DBF_FIELD_NAME_BYTES);
        String lower = candidate.toLowerCase(Locale.ROOT);
        int suffix = 1;
        while (usedByLowerName.containsKey(lower)) {
            String suffixText = "_" + suffix++;
            candidate = truncateAscii(base, MAX_DBF_FIELD_NAME_BYTES - suffixText.length()) + suffixText;
            lower = candidate.toLowerCase(Locale.ROOT);
        }
        usedByLowerName.put(lower, sourceName);
        return candidate;
    }

    private String asciiIdentifier(String value) {
        String source = value == null || value.isBlank() ? "field" : value.trim();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c < 128 && (Character.isLetterOrDigit(c) || c == '_')) {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        if (out.length() == 0) {
            out.append("field");
        }
        if (Character.isDigit(out.charAt(0))) {
            out.insert(0, 'f');
        }
        return out.toString();
    }

    private String truncateAscii(String value, int maxBytes) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            String next = out.toString() + value.charAt(i);
            if (next.getBytes(StandardCharsets.US_ASCII).length > maxBytes) {
                break;
            }
            out.append(value.charAt(i));
        }
        if (out.length() == 0) {
            return "f";
        }
        return out.toString();
    }
}
