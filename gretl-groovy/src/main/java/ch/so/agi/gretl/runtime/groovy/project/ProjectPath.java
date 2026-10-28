package ch.so.agi.gretl.runtime.groovy.project;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Immutable logical project/name path. Only relativeDirectory uses filesystem syntax. */
public final class ProjectPath {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
    private static final ProjectPath ROOT = new ProjectPath(List.of());
    private final List<String> segments;
    private final String canonical;

    private ProjectPath(List<String> segments) {
        this.segments = List.copyOf(segments);
        this.canonical = ":" + String.join(":", segments);
    }

    public static ProjectPath root() {
        return ROOT;
    }

    /** Includes accept an optional leading colon, but never the implicit root project. */
    public static ProjectPath parseInclude(String text) {
        ProjectPath value = parse(text, false);
        if (value.isRoot()) throw invalid(text);
        return value;
    }

    public static ProjectPath parseQualified(String text) {
        return parse(text, true);
    }

    private static ProjectPath parse(String text, boolean qualified) {
        if (text == null || text.isEmpty() || (qualified && !text.startsWith(":"))) {
            throw invalid(text);
        }
        if (text.equals(":")) return root();
        String body = text.startsWith(":") ? text.substring(1) : text;
        List<String> parts = List.of(body.split(":", -1));
        for (String part : parts) requireLocal(part);
        return new ProjectPath(parts);
    }

    public String canonical() {
        return canonical;
    }

    public Path relativeDirectory() {
        Path directory = Path.of("");
        for (String segment : segments) directory = directory.resolve(segment);
        return directory;
    }

    public String qualifyLocal(String name) {
        requireLocal(name);
        return (isRoot() ? ":" : canonical + ":") + name;
    }

    /** A reference must name a task/parameter/cleanup, rather than the root alone. */
    public String resolveReference(String name) {
        if (name != null && name.startsWith(":")) {
            ProjectPath value = parseQualified(name);
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
        return other instanceof ProjectPath path && canonical.equals(path.canonical);
    }

    @Override public int hashCode() {
        return canonical.hashCode();
    }

    @Override public String toString() {
        return canonical;
    }
}
