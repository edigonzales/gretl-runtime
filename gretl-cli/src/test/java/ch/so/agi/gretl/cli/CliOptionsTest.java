package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliOptionsTest {
    @Test
    void parsesRepeatableParametersAndFirstEquals() {
        CliOptions options = CliOptions.parse(new String[]{
                "run", "job.groovy", "--target", "a", "--target", "b",
                "--param", "u=https://x/?a=b", "--param", "empty=",
                "--timeout", "PT2S", "--log-format", "json", "--keep-work", "always"
        }, Path.of("/tmp"));
        assertEquals(CliOptions.Command.RUN, options.command());
        assertEquals(List.of("a", "b"), options.targets());
        assertEquals("https://x/?a=b", options.parameters().get("u"));
        assertEquals("", options.parameters().get("empty"));
        assertEquals("json", options.logFormat().name().toLowerCase());
        assertEquals("always", options.keepWork().name().toLowerCase().replace('_', '-'));
    }

    @Test
    void rejectsUnknownMissingDuplicateAndInapplicableOptions() {
        assertThrows(CliException.class, () -> CliOptions.parse(
                new String[]{"run", "job.groovy", "--unknown"}, Path.of("/tmp")));
        assertThrows(CliException.class, () -> CliOptions.parse(
                new String[]{"run", "job.groovy", "--param", "noequals"}, Path.of("/tmp")));
        assertThrows(CliException.class, () -> CliOptions.parse(
                new String[]{"run", "job.groovy", "--param", "a=1", "--param", "a=2"},
                Path.of("/tmp")));
        assertThrows(CliException.class, () -> CliOptions.parse(
                new String[]{"check", "job.groovy", "--report", "x"}, Path.of("/tmp")));
        assertThrows(CliException.class, () -> CliOptions.parse(
                new String[]{"run", "job.groovy", "--timeout", "soon"}, Path.of("/tmp")));
    }
}
