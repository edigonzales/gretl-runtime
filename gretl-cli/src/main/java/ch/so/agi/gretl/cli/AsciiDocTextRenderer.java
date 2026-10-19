package ch.so.agi.gretl.cli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds the small set of semantic AsciiDoc roles used by the reference pages.
 *
 * <p>This is a plain text renderer, not a Javadoc doclet. It protects existing
 * inline markup and never interprets the description as an AsciiDoc document.</p>
 */
final class AsciiDocTextRenderer {
    private static final Pattern PROTECTED = Pattern.compile(
            "\\[\\.[A-Za-z0-9_.-]+\\](?:##[^#\\n]*##|#[^#\\n]*#)"
                    + "|(?:https?://|link:|xref:)[^\\s\\[]+\\[[^\\]\\n]*\\]"
                    + "|`[^`\\n]*`");
    private static final Pattern METHOD = Pattern.compile(
            "(?<![A-Za-z0-9_$])([A-Za-z_$][A-Za-z0-9_$]*\\([^()\\n]*\\))");
    private static final Pattern COMPLETE_METHOD = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*\\([^()\\n]*\\)");
    private static final Pattern ACRONYM = Pattern.compile(
            "(?<![A-Za-z0-9-])"
                    + "(INTERLIS|HTTPS|JDBC|ASCII|BINARY|XLSX|GeoTIFF|GZIP|JSON|XSLT|"
                    + "HTTP|UUID|WKT|XML|CSV|GML|ITF|XTF|DXF|SHP|CRS|EPSG|FTP|SQL|"
                    + "URL|API|LSP|DSL|JVM|OID|S3|DB)"
                    + "(?![A-Za-z0-9])");

    String render(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        StringBuilder rendered = new StringBuilder(text.length());
        Matcher protectedMatcher = PROTECTED.matcher(text);
        int end = 0;
        while (protectedMatcher.find()) {
            rendered.append(renderPlain(text.substring(end, protectedMatcher.start())));
            rendered.append(renderProtected(protectedMatcher.group()));
            end = protectedMatcher.end();
        }
        rendered.append(renderPlain(text.substring(end)));
        return rendered.toString();
    }

    private String renderProtected(String value) {
        if (value.startsWith("`") && value.endsWith("`")) {
            String content = value.substring(1, value.length() - 1);
            if (COMPLETE_METHOD.matcher(content).matches()) {
                return "[.dsl-method]#`" + escapeEllipsis(content) + "`#";
            }
        }
        return value;
    }

    private String renderPlain(String value) {
        StringBuilder rendered = new StringBuilder(value.length());
        Matcher methodMatcher = METHOD.matcher(value);
        int end = 0;
        while (methodMatcher.find()) {
            rendered.append(renderAcronyms(value.substring(end, methodMatcher.start())));
            rendered.append("[.dsl-method]#`")
                    .append(escapeEllipsis(methodMatcher.group(1)))
                    .append("`#");
            end = methodMatcher.end();
        }
        rendered.append(renderAcronyms(value.substring(end)));
        return rendered.toString();
    }

    private String renderAcronyms(String value) {
        return ACRONYM.matcher(value).replaceAll(match ->
                "[.acronym]#" + Matcher.quoteReplacement(match.group(1)) + "#");
    }

    private static String escapeEllipsis(String value) {
        return value.replace("...", "\\...");
    }
}
