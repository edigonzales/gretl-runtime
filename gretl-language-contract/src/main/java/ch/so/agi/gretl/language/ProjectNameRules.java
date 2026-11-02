package ch.so.agi.gretl.language;

import java.util.List;
import java.util.regex.Pattern;

/** Immutable logical project/name path. Pure logical names, without filesystem access. */
public final class ProjectNameRules {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
    private static final ProjectNameRules ROOT = new ProjectNameRules(List.of());
    private final List<String> segments;
    private final String canonical;

    private ProjectNameRules(List<String> segments) {
        this.segments = List.copyOf(segments);
        this.canonical = ":" + String.join(":", segments);
    }

    public static ProjectNameRules root() {
        return ROOT;
    }

    /** Includes accept an optional leading colon, but never the implicit root project. */
    public static ProjectNameRules parseInclude(String text) {
        ProjectNameRules value = parse(text, false);
        if (value.isRoot()) throw invalid(text);
        return value;
    }

    public static ProjectNameRules parseQualified(String text) {
        return parse(text, true);
    }

    private static ProjectNameRules parse(String text, boolean qualified) {
        if (text == null || text.isEmpty() || (qualified && !text.startsWith(":"))) {
            throw invalid(text);
        }
        if (text.equals(":")) return root();
        String body = text.startsWith(":") ? text.substring(1) : text;
        List<String> parts = List.of(body.split(":", -1));
        for (String part : parts) requireLocal(part);
        return new ProjectNameRules(parts);
    }

    public java.util.List<String> segments() { return segments; }

    public String renderReference(String qualified) {
        String target = parseQualified(qualified).canonical();
        if (target.equals(":")) throw invalid(target);
        String prefix = isRoot() ? ":" : canonical + ":";
        String local = target.startsWith(prefix) ? target.substring(prefix.length()) : "";
        return !local.isEmpty() && !local.contains(":") ? local : target;
    }

    public String canonical() {
        return canonical;
    }


    public String qualifyLocal(String name) {
        requireLocal(name);
        return (isRoot() ? ":" : canonical + ":") + name;
    }

    /** A reference must name a task/parameter/cleanup, rather than the root alone. */
    public String resolveReference(String name) {
        if (name != null && name.startsWith(":")) {
            ProjectNameRules value = parseQualified(name);
            if (value.isRoot()) throw invalid(name);
            return value.canonical();
        }
        return qualifyLocal(name);
    }

    public boolean isRoot() {
        return segments.isEmpty();
    }

    private static void requireLocal(String value) {
        if (value == null || !SEGMENT.matcher(value).matches()) throw invalid(value);
    }

    private static IllegalArgumentException invalid(String value) {
        return new IllegalArgumentException("Invalid project/name path: " + value);
    }

    @Override public boolean equals(Object other) {
        return other instanceof ProjectNameRules path && canonical.equals(path.canonical);
    }

    @Override public int hashCode() {
        return canonical.hashCode();
    }

    @Override public String toString() {
        return canonical;
    }
}
