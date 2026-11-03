package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.source.TextSpan;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceEditBuilderTest {
    @Test void actualRoundTripKeepsBomCrLfEmojiCommentsAndOpaqueCode() {
        String before = "\uFEFF// 😀 untouched\r\n"
                + "task('a', Echo) {\r\n  input = 'old' // keep\r\n  custom { println 'opaque' }\r\n}\r\n";
        var source = SourceText.job(before);
        int value = before.indexOf("'old'");
        var builder = new SourceEditBuilder(source)
                .replace(new TextSpan(value, value + 5), "'old'", "'new'")
                .insert(before.length(), "defaults 'a'\r\n");
        String after = builder.apply();
        assertEquals("\uFEFF// 😀 untouched\r\ntask('a', Echo) {\r\n  input = 'new' // keep\r\n"
                + "  custom { println 'opaque' }\r\n}\r\ndefaults 'a'\r\n", after);
        assertTrue(SourceEditBuilder.verifyPreservation(source, after, builder.build()));
        assertFalse(SourceEditBuilder.verifyPreservation(source, after.replace("// keep", "// lost"), builder.build()));
        assertFalse(SourceEditBuilder.verifyPreservation(source, after.replace("\r\n", "\n"), builder.build()));
        assertFalse(SourceEditBuilder.verifyPreservation(source, after + "extra", builder.build()));
    }

    @Test void mismatchedPreimagesAndOverlapsAreRejectedBeforeApplication() {
        var source = SourceText.job("abcdef");
        assertThrows(IllegalArgumentException.class, () -> new SourceEditBuilder(source)
                .replace(new TextSpan(1, 3), "bc", "X").replace(new TextSpan(2, 4), "cd", "Y").build());
        assertThrows(IllegalArgumentException.class, () -> new SourceEditBuilder(source)
                .insert(2, "x").insert(2, "y").build());
        assertThrows(IllegalArgumentException.class, () -> new SourceEditBuilder(source)
                .replace(new TextSpan(1, 3), "wrong", "x"));
        assertThrows(IllegalArgumentException.class, () -> SourceEditBuilder.apply(source,
                List.of(new SourceReplacement(new TextSpan(1, 3), "zz", "x"))));
        assertThrows(IllegalArgumentException.class, () -> new SourceEditBuilder(source).insert(7, "x"));
        assertEquals("abcdef", source.text());
    }

    @Test void adjacentEditsUseOriginalCoordinatesAndNoOpProducesNoChanges() {
        var source = SourceText.job("abc\uD83D");
        var noOp = new SourceEditBuilder(source).replace(new TextSpan(0, 2), "ab", "ab").insert(2, "");
        assertTrue(noOp.build().isEmpty());
        assertEquals(source.text(), noOp.apply());
        var changes = new SourceEditBuilder(source).replace(new TextSpan(0, 1), "a", "long")
                .deleteOwnedRegion(new TextSpan(1, 2)).insert(2, "X");
        assertEquals("longXc\uD83D", changes.apply());
        assertTrue(SourceEditBuilder.verifyPreservation(source, changes.apply(), changes.build()));
    }
}
