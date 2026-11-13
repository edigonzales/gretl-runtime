package ch.so.agi.gretl.tasks.io.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Include/exclude globs over slash-separated relative paths. */
public final class RelativePathSelection {
    private final List<List<Pattern>> includes;
    private final List<List<Pattern>> excludes;

    private RelativePathSelection(List<List<Pattern>> includes, List<List<Pattern>> excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    public static RelativePathSelection compile(List<String> includes, List<String> excludes,
                                                boolean caseSensitive) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return new RelativePathSelection(includes.stream().map(glob -> compile(glob, flags)).toList(),
                excludes.stream().map(glob -> compile(glob, flags)).toList());
    }

    public boolean matches(String relativePath) {
        String[] segments = validate(relativePath).split("/");
        return (includes.isEmpty() || includes.stream().anyMatch(glob -> matches(glob, segments)))
                && excludes.stream().noneMatch(glob -> matches(glob, segments));
    }

    public static List<String> patterns(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Glob patterns must be a list of text values");
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof CharSequence text)) {
                throw new IllegalArgumentException("Glob patterns must be text");
            }
            result.add(text.toString());
        }
        return List.copyOf(result);
    }

    public static String validate(String path) {
        if (path.isBlank() || path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0
                || path.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Expected a slash-separated relative path");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Expected a slash-separated relative path");
            }
        }
        return path;
    }

    private static List<Pattern> compile(String glob, int flags) {
        List<Pattern> patterns = new ArrayList<>();
        for (String segment : validate(glob).split("/")) {
            if (segment.equals("**")) {
                patterns.add(null);
                continue;
            }
            StringBuilder regex = new StringBuilder();
            boolean previousStar = false;
            for (int index = 0; index < segment.length(); index++) {
                char value = segment.charAt(index);
                switch (value) {
                    case '*' -> {
                        if (!previousStar) regex.append(".*");
                    }
                    case '?' -> regex.append('.');
                    default -> regex.append(Pattern.quote(String.valueOf(value)));
                }
                previousStar = value == '*';
            }
            patterns.add(Pattern.compile(regex.toString(), flags | Pattern.DOTALL));
        }
        return java.util.Collections.unmodifiableList(patterns);
    }

    private static boolean matches(List<Pattern> glob, String[] segments) {
        boolean[] matched = new boolean[segments.length + 1];
        matched[0] = true;
        for (Pattern pattern : glob) {
            if (pattern == null) {
                // A ** segment consumes zero or more path segments.
                for (int index = 1; index < matched.length; index++) {
                    matched[index] |= matched[index - 1];
                }
            } else {
                for (int index = segments.length; index > 0; index--) {
                    matched[index] = matched[index - 1] && pattern.matcher(segments[index - 1]).matches();
                }
                matched[0] = false;
            }
        }
        return matched[segments.length];
    }
}
