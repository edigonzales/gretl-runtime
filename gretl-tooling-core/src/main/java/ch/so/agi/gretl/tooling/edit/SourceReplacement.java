package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.source.TextSpan;
import java.util.Objects;

/** A verified preimage and replacement in original UTF-16 coordinates. */
public record SourceReplacement(TextSpan span, String expectedText, String replacement) {
    public SourceReplacement {
        Objects.requireNonNull(span);
        Objects.requireNonNull(expectedText);
        Objects.requireNonNull(replacement);
        if (span.length() != expectedText.length()) throw new IllegalArgumentException("EDIT_PREIMAGE_LENGTH");
    }
}
