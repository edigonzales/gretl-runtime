package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.source.TextSpan;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Collects local source changes; it never formats a file or assigns ownership to adjacent trivia. */
public final class SourceEditBuilder {
    public static final int MAXIMUM_EDITS = 16_000;
    private final SourceText source;
    private final List<SourceReplacement> edits = new ArrayList<>();

    public SourceEditBuilder(SourceText source) {
        this.source = Objects.requireNonNull(source);
    }

    public SourceEditBuilder replace(TextSpan span, String expected, String replacement) {
        if (span.endUtf16() > source.text().length() || !source.slice(span).equals(expected)) {
            throw new IllegalArgumentException("EDIT_PREIMAGE_MISMATCH");
        }
        if (!expected.equals(replacement)) {
            if (edits.size() >= MAXIMUM_EDITS) throw new IllegalArgumentException("EDIT_LIMIT");
            edits.add(new SourceReplacement(span, expected, replacement));
        }
        return this;
    }

    public SourceEditBuilder insert(int offset, String text) {
        return replace(new TextSpan(offset, offset), "", text);
    }

    /** Only the caller's proven region is deleted; surrounding comments are retained. */
    public SourceEditBuilder deleteOwnedRegion(TextSpan span) {
        return replace(span, source.slice(span), "");
    }

    public List<SourceReplacement> build() {
        var sorted = edits.stream().sorted(Comparator.comparingInt((SourceReplacement e) -> e.span().startUtf16())
                .thenComparingInt(e -> e.span().endUtf16())).toList();
        SourceReplacement previous = null;
        for (var edit : sorted) {
            if (previous != null && (previous.span().endUtf16() > edit.span().startUtf16()
                    || previous.span().startUtf16() == edit.span().startUtf16())) {
                throw new IllegalArgumentException("EDIT_OVERLAP");
            }
            previous = edit;
        }
        return List.copyOf(sorted);
    }

    public String apply() {
        return apply(source, build());
    }

    public static String apply(SourceText source, List<SourceReplacement> changes) {
        var checked = new SourceEditBuilder(source);
        for (var change : changes) checked.replace(change.span(), change.expectedText(), change.replacement());
        var ordered = checked.build();
        long length = source.text().length();
        for (var edit : ordered) length += (long) edit.replacement().length() - edit.span().length();
        if (length > 4_194_304) throw new IllegalArgumentException("EDIT_SIZE_LIMIT");
        // Descending original offsets, without repeatedly shifting the already constructed suffix.
        var pieces = new ArrayDeque<String>();
        int end = source.text().length();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            var edit = ordered.get(i);
            pieces.addFirst(source.text().substring(edit.span().endUtf16(), end));
            pieces.addFirst(edit.replacement());
            end = edit.span().startUtf16();
        }
        pieces.addFirst(source.text().substring(0, end));
        var text = new StringBuilder((int) length);
        pieces.forEach(text::append);
        return text.toString();
    }

    /** Verifies each unchanged segment against the actual postimage, rather than a normalized copy. */
    public static boolean verifyPreservation(SourceText source, String after, List<SourceReplacement> changes) {
        var checked = new SourceEditBuilder(source);
        for (var edit : changes) checked.replace(edit.span(), edit.expectedText(), edit.replacement());
        int beforeOffset = 0;
        int afterOffset = 0;
        for (var edit : checked.build()) {
            String untouched = source.text().substring(beforeOffset, edit.span().startUtf16());
            if (!after.startsWith(untouched, afterOffset)) return false;
            afterOffset += untouched.length();
            if (!after.startsWith(edit.replacement(), afterOffset)) return false;
            afterOffset += edit.replacement().length();
            beforeOffset = edit.span().endUtf16();
        }
        return after.substring(Math.min(afterOffset, after.length())).equals(source.text().substring(beforeOffset))
                && afterOffset <= after.length();
    }
}
