package ch.so.agi.gretl.cli;

import ch.so.agi.gretl.job.runtime.PlanGraphFormat;
import ch.so.agi.gretl.job.runtime.StartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
    void parsesPlanGraphAndOutputOptions() {
        CliOptions options = CliOptions.parse(new String[]{
                "plan", "job.groovy", "--graph", "Mermaid",
                "--output", "plan.mmd"
        }, Path.of("/tmp"));

        assertEquals(CliOptions.Command.PLAN, options.command());
        assertEquals(PlanGraphFormat.MERMAID, options.graphFormat());
        assertEquals("plan.mmd", options.output().getFileName().toString());
    }

    @Test
    void parsesAsciiDocOperationOptions() {
        CliOptions options = CliOptions.parse(new String[]{
                "operations", "HttpDownload", "--format", "adoc", "--output", "docs"
        }, Path.of("/tmp"), Locale.GERMAN);
        assertEquals(CliOptions.Command.OPERATIONS, options.command());
        assertEquals(DocFormat.ADOC, options.docFormat());
        assertEquals("HttpDownload", options.operationSymbol());
        assertEquals("docs", options.output().getFileName().toString());

        CliOptions single = CliOptions.parse(new String[]{
                "operations", "HttpDownload", "--format", "adoc"
        }, Path.of("/tmp"), Locale.GERMAN);
        assertEquals(DocFormat.ADOC, single.docFormat());
        assertNull(single.output());

        CliOptions plain = CliOptions.parse(new String[]{
                "operations", "HttpDownload"
        }, Path.of("/tmp"), Locale.GERMAN);
        assertEquals(DocFormat.TEXT, plain.docFormat());
    }

    @Test
    void rejectsInvalidAsciiDocOperationOptions() {
        StartException missingOutput = assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"operations", "--format", "adoc"}, Path.of("/tmp"),
                Locale.GERMAN));
        assertTrue(missingOutput.getMessage().contains("--output"), missingOutput.getMessage());

        StartException outputWithoutFormat = assertThrows(StartException.class,
                () -> CliOptions.parse(new String[]{"operations", "--output", "docs"},
                        Path.of("/tmp"), Locale.GERMAN));
        assertTrue(outputWithoutFormat.getMessage().contains("--format adoc"),
                outputWithoutFormat.getMessage());

        StartException unknownFormat = assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"operations", "--format", "yaml"}, Path.of("/tmp"),
                Locale.GERMAN));
        assertTrue(unknownFormat.getMessage().contains("Nicht unterstuetztes Format"),
                unknownFormat.getMessage());
    }

    @Test
    void rejectsGraphOptionsOutsidePlan() {
        StartException run = assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"run", "job.groovy", "--graph", "mermaid"}, Path.of("/tmp"),
                java.util.Locale.ENGLISH));
        assertTrue(run.getMessage().contains("only valid for plan"), run.getMessage());
        StartException output = assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"check", "job.groovy", "--output", "x"}, Path.of("/tmp"),
                java.util.Locale.ENGLISH));
        assertTrue(output.getMessage().contains("only valid for plan"), output.getMessage());
        StartException noGraph = assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"plan", "job.groovy", "--output", "x"}, Path.of("/tmp"),
                java.util.Locale.ENGLISH));
        assertTrue(noGraph.getMessage().contains("requires --graph"), noGraph.getMessage());
        StartException format = assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"plan", "job.groovy", "--graph", "svg"}, Path.of("/tmp"),
                java.util.Locale.ENGLISH));
        assertTrue(format.getMessage().contains("Unsupported graph format"), format.getMessage());
    }

    @Test
    void parserErrorsFollowTheRequestedLocale() {
        StartException english = assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"run", "--lang", "en"}, Path.of("/tmp"), Locale.ENGLISH));
        assertTrue(english.getMessage().contains("Job file missing"), english.getMessage());
        StartException german = assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"run", "--lang", "de"}, Path.of("/tmp"), Locale.GERMAN));
        assertTrue(german.getMessage().contains("Jobdatei fehlt"), german.getMessage());
    }

    @Test
    void rejectsUnknownMissingDuplicateAndInapplicableOptions() {
        assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"run", "job.groovy", "--unknown"}, Path.of("/tmp")));
        assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"run", "job.groovy", "--param", "noequals"}, Path.of("/tmp")));
        assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"run", "job.groovy", "--param", "a=1", "--param", "a=2"},
                Path.of("/tmp")));
        assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"check", "job.groovy", "--report", "x"}, Path.of("/tmp")));
        assertThrows(StartException.class, () -> CliOptions.parse(
                new String[]{"run", "job.groovy", "--timeout", "soon"}, Path.of("/tmp")));
    }
}
