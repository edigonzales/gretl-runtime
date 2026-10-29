package ch.so.agi.gretl.runtime.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class FailureInfoTest {

    @Test
    void preservesCauseAndSuppressedGraph() {
        IOException root = new IOException("root");
        IllegalStateException outer = new IllegalStateException("outer", root);
        outer.addSuppressed(new IllegalArgumentException("suppressed"));

        FailureInfo info = FailureInfo.from(outer);

        assertEquals(IllegalStateException.class.getName(), info.type());
        assertEquals("outer", info.message());
        assertEquals(IOException.class.getName(), info.causes().get(0).type());
        assertEquals("root", info.causes().get(0).message());
        assertEquals(IllegalArgumentException.class.getName(), info.suppressed().get(0).type());
        assertEquals("suppressed", info.suppressed().get(0).message());
        assertEquals(info.id(), info.causes().get(0).id());
        assertFalse(info.truncated());
    }

    @Test
    void cyclicThrowableGraphTerminatesWithTruncation() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        FailureInfo info = FailureInfo.from(first);
        assertTrue(info.truncated());
        assertTrue(info.causes().get(0).causes().get(0).message()
                .contains("cyclic"));
    }

    @Test
    void longMessagesAreBounded() {
        String message = "x".repeat(3000);
        FailureInfo info = FailureInfo.from(new IllegalStateException(message));
        assertTrue(info.message().length() < 3000);
        assertTrue(info.message().endsWith("[truncated]"));
    }

    @Test
    void nullThrowableIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FailureInfo.from(null));
    }

    @Test
    void lexicalSourceAliasesMapEveryGraphNodeWithoutChangingTheThrowableAndRestoreOnExit() {
        var raw = new IOException("original");
        raw.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("Library", "unknown", null, -1),
                new StackTraceElement("Script", "run", "Compiled.groovy", 17) });
        var outer = new IllegalStateException("outer", raw);
        outer.setStackTrace(raw.getStackTrace());
        var suppressed = new IllegalArgumentException("suppressed");
        suppressed.setStackTrace(raw.getStackTrace());
        outer.addSuppressed(suppressed);
        var original = java.nio.file.Path.of("import/job.groovy");
        FailureInfo mapped = FailureInfo.withSourceFiles(java.util.Map.of("Compiled.groovy", original), () -> {
            var info = FailureInfo.from(outer);
            assertEquals("Compiled.groovy", FailureInfo.withSourceFiles(java.util.Map.of(),
                    () -> FailureInfo.from(raw)).source().orElseThrow().file());
            assertEquals(original.toString(), FailureInfo.from(raw).source().orElseThrow().file());
            // The proxy bridge constructs immutable frames directly rather than calling from(Throwable).
            var proxy = new FailureInfo("proxy", "Type", "message", java.util.List.of(), java.util.List.of(),
                    false, java.util.List.of(new FailureInfo.Frame("Script", "run", "Compiled.groovy", 17)),
                    false, java.util.Optional.of(new SourceLocation("Compiled.groovy", 17, 4)));
            assertEquals(original.toString(), proxy.frames().getFirst().fileName());
            assertEquals(4, proxy.source().orElseThrow().column());
            return info;
        });
        for (var info : java.util.List.of(mapped, mapped.causes().getFirst(), mapped.suppressed().getFirst())) {
            assertEquals(original.toString(), info.source().orElseThrow().file());
            assertEquals(17, info.source().orElseThrow().line());
            assertEquals("", info.frames().getFirst().fileName());
            assertEquals(original.toString(), info.frames().get(1).fileName());
            assertEquals("Script", info.frames().get(1).className());
        }
        assertEquals("Compiled.groovy", raw.getStackTrace()[1].getFileName());
        assertEquals(IOException.class.getName(), mapped.causes().getFirst().type());
        assertThrows(IllegalArgumentException.class, () -> FailureInfo.withSourceFiles(
                java.util.Map.of("Compiled.groovy", original), () -> { throw new IllegalArgumentException("exit"); }));
        assertEquals("Compiled.groovy", FailureInfo.from(raw).source().orElseThrow().file());
    }
}
