package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliHelpAndDefinitionTest {
    @TempDir
    Path tempDir;

    @Test
    void globalHelpVersionAndOperationsDoNotLoadAJob() {
        Result version = cli().run("--version");
        assertEquals(0, version.code());
        assertTrue(version.out().contains("gretl-runtime 0.1.0-SNAPSHOT"));
        assertTrue(version.err().isEmpty());

        Result help = cli().run("--help");
        assertEquals(0, help.code());
        assertTrue(help.out().contains("gretl operations"));
        assertTrue(help.err().isEmpty());

        Result packageHelp = cli().run("package", "--help");
        assertEquals(0, packageHelp.code());
        assertTrue(packageHelp.out().contains("gretl package"));
        Result englishPackageHelp = cli().run("package", "--help", "--lang", "en");
        assertEquals(0, englishPackageHelp.code());
        assertTrue(englishPackageHelp.out().contains("Creates an executable job JAR"));

        Result englishHelp = cli().run("--help", "--lang", "en");
        assertEquals(0, englishHelp.code());
        assertTrue(englishHelp.out().contains("Usage:"), englishHelp.out());
        assertTrue(englishHelp.out().contains("Runtime options for run:"));
        assertTrue(englishHelp.out().contains("Plan options:"));
        assertTrue(englishHelp.out().contains("--graph mermaid|plantuml"));

        Result operations = cli().run("operations");
        assertEquals(0, operations.code());
        assertTrue(operations.out().contains("HttpDownload"));
        assertTrue(operations.out().contains("Extract"));
        assertTrue(operations.out().contains("SqlExecutor"));
        assertFalse(operations.out().contains("Publisher"));

        Result englishDetail = cli().run("operations", "HttpDownload", "--lang", "en");
        assertEquals(0, englishDetail.code());
        assertTrue(englishDetail.out().contains("Download a file over HTTP"));
        assertTrue(englishDetail.out().contains("Absolute HTTP/HTTPS URL"));

        Result detail = cli().run("operations", "HttpDownload");
        assertEquals(0, detail.code());
        assertTrue(detail.out().contains("url"));
        assertTrue(detail.out().contains("finalUri"));
        assertTrue(detail.out().contains("maxBytes"));

        Result unknown = cli().run("operations", "DoesNotExist");
        assertEquals(2, unknown.code());
        assertTrue(unknown.err().contains("Unbekannter Baustein"));
    }

    @Test
    void operationsCanRenderAsciiDocReference() throws Exception {
        Path docs = tempDir.resolve("docs");
        Result all = cli().run("operations", "--format", "adoc",
                "--output", docs.toString());
        assertEquals(0, all.code(), all.err());
        assertTrue(Files.isRegularFile(docs.resolve("task-httpdownload.adoc")));
        assertTrue(Files.isRegularFile(docs.resolve("task-extract.adoc")));
        assertTrue(Files.isRegularFile(docs.resolve("task-sqlexecutor.adoc")));
        assertFalse(Files.exists(docs.resolve("task-reference.adoc")));

        String table = Files.readString(docs.resolve("task-httpdownload.adoc"),
                StandardCharsets.UTF_8);
        assertTrue(table.startsWith("[cols=\"4,6,1\""), table);
        assertTrue(table.contains("*url* = uri"), table);
        assertFalse(table.contains(".Beispiel"), table);
        assertFalse(table.contains(".Ergebnisse"), table);

        Result single = cli().run("operations", "HttpDownload", "--format", "adoc");
        assertEquals(0, single.code(), single.err());
        assertTrue(single.out().contains("Absolute"), single.out());
        assertTrue(single.out().contains("*url* = uri"), single.out());

        Result missingOutput = cli().run("operations", "--format", "adoc");
        assertEquals(2, missingOutput.code());
        assertTrue(missingOutput.err().contains("--output"), missingOutput.err());
    }

    @Test
    void jobHelpCheckAndPlanDoNotCreateWorkOrReports() throws Exception {
        Path script = writeJob();
        Result help = cli().run("run", script.toString(), "--help");
        assertEquals(0, help.code());
        assertTrue(help.out().contains("quelle"));
        assertTrue(help.out().contains("Standardziele: download"));
        assertFalse(Files.exists(tempDir.resolve("work")));
        assertFalse(Files.exists(tempDir.resolve("report.json")));

        Result check = cli().run("check", script.toString());
        assertEquals(0, check.code());
        assertTrue(check.out().contains("Offene Pflichtparameter: quelle"));
        assertFalse(Files.exists(tempDir.resolve("work")));
        assertFalse(Files.exists(tempDir.resolve("report.json")));

        Result plan = cli().run("plan", script.toString(), "--target", "download");
        assertEquals(0, plan.code());
        assertTrue(plan.out().contains("download"));
        assertTrue(plan.out().contains("Reihenfolge"));
        assertFalse(Files.exists(tempDir.resolve("work")));

        Result mermaid = cli().run("plan", script.toString(), "--target", "download",
                "--graph", "mermaid");
        assertEquals(0, mermaid.code(), mermaid.err());
        assertTrue(mermaid.out().startsWith("flowchart TB"), mermaid.out());
        assertTrue(mermaid.out().contains("download"));
        assertFalse(mermaid.out().contains("Reihenfolge"));

        Path graphFile = tempDir.resolve("dag.puml");
        Result plantuml = cli().run("plan", script.toString(), "--target", "download",
                "--graph", "plantuml", "--output", graphFile.toString());
        assertEquals(0, plantuml.code(), plantuml.err());
        assertTrue(plantuml.out().isEmpty());
        assertTrue(Files.readString(graphFile).startsWith("@startuml"));
        assertFalse(Files.exists(tempDir.resolve("work")));
    }

    @Test
    void checkAndPlanExecuteTopLevelButNeverTaskBody() throws Exception {
        Path loaded = tempDir.resolve("loaded.txt");
        Path body = tempDir.resolve("body.txt");
        Path script = tempDir.resolve("marker.groovy");
        Files.writeString(script, """
                java.nio.file.Files.writeString(java.nio.file.Path.of('%s'), 'loaded')
                action('work') {
                    java.nio.file.Files.writeString(java.nio.file.Path.of('%s'), 'ran')
                    return 'ok'
                }
                defaults 'work'
                """.formatted(loaded, body));
        Result check = cli().run("check", script.toString());
        assertEquals(0, check.code(), check.err());
        assertTrue(Files.exists(loaded));
        assertFalse(Files.exists(body));

        Result plan = cli().run("plan", script.toString());
        assertEquals(0, plan.code(), plan.err());
        assertFalse(Files.exists(body));
    }

    @Test
    void runRejectsMissingAndUnknownParametersBeforeWork() throws Exception {
        Path script = writeJob();
        Result missing = cli().run("run", script.toString(),
                "--work-dir", tempDir.resolve("work").toString(),
                "--report", tempDir.resolve("report.json").toString());
        assertEquals(2, missing.code());
        assertTrue(missing.err().contains("quelle"));
        assertFalse(Files.exists(tempDir.resolve("work")));

        Result unknown = cli().run("run", script.toString(), "--param", "qulle=x",
                "--work-dir", tempDir.resolve("work").toString());
        assertEquals(2, unknown.code());
        assertTrue(unknown.err().contains("qulle"));
        assertFalse(Files.exists(tempDir.resolve("work")));
    }

    @Test
    void invalidConfigurationIsCodeTwoAndJsonEarlyErrorIsStructured() throws Exception {
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"runtime\":{\"unknown\":true}}");
        Path script = writeJob();
        Result result = cli().run("run", script.toString(), "--config", config.toString(),
                "--log-format", "json", "--param", "quelle=http://example.invalid/x");
        assertEquals(2, result.code());
        String line = result.err().lines().findFirst().orElseThrow();
        assertTrue(line.startsWith("{"));
        assertTrue(line.contains("\"event_name\":\"gretl.cli.error\""));
        assertTrue(line.contains("\"exit_code\":2"));
    }

    private Path writeJob() throws Exception {
        Path script = tempDir.resolve("job.groovy");
        Files.writeString(script, """
                parameter('quelle', required: true, description: 'Quelle')
                task('download', HttpDownload) {
                    url = parameter('quelle')
                    output = workPath('data.bin')
                }
                defaults 'download'
                """, StandardCharsets.UTF_8);
        return script;
    }

    private Cli cli() {
        return new Cli();
    }

    private final class Cli {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private final GretlCli cli = new GretlCli(
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                Map.of("GRETL_LANGUAGE", "de"), tempDir, getClass().getClassLoader());

        Result run(String... args) {
            int code = cli.run(args);
            return new Result(code, out.toString(StandardCharsets.UTF_8),
                    err.toString(StandardCharsets.UTF_8));
        }
    }

    private record Result(int code, String out, String err) {
    }
}
