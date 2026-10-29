package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliProjectRunTest {
    @TempDir Path temp;

    @Test
    void prj102And110RealHttpExtractChainHasOneRunAndPlanningHasNoTaskWork() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        var zipBytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(zipBytes)) { zip.putNextEntry(new ZipEntry("payload.txt")); zip.write("synthetic payload ü\n".getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/data", exchange -> { requests.incrementAndGet(); byte[] bytes = zipBytes.toByteArray(); exchange.sendResponseHeaders(200, bytes.length); try (var output = exchange.getResponseBody()) { output.write(bytes); } });
        server.start();
        try {
            Path root = temp.resolve("project with spaces");
            write(root, "settings.gretl", "include 'download', 'validation', 'import'\ndefaults ':import:load'\n");
            write(root, "job.groovy", "parameter('output', description: 'Output', type: 'path', required: true)\n");
            write(root, "download/job.groovy", "parameter('url', description: 'URL', required: true)\ntask('fetch', HttpDownload) { url = parameter('url'); output = workPath('data.zip') }\ndefaults 'fetch'\n");
            write(root, "validation/job.groovy", "task('check', Extract, dependsOn: ':download:fetch') { archive = result(':download:fetch').file; compression = 'ZIP'; outputDirectory = workPath('data') }\ndefaults 'check'\n");
            write(root, "import/job.groovy", "action('load', dependsOn: ':validation:check') { def input = result(':validation:check').files.find { it.fileName.toString() == 'payload.txt' }; java.nio.file.Files.copy(input, parameter(':output')) }\ncleanup('release') { java.nio.file.Files.writeString(path('cleanup.txt'), 'done') }\ndefaults 'load'\n");
            for (String command : List.of("check", "plan")) {
                var result = run(command, root.toString()); assertEquals(0, result.code(), result.err());
            }
            var help = run("run", root.toString(), "--help"); assertEquals(0, help.code(), help.err());
            assertTrue(help.out().contains(":download:url")); assertTrue(help.out().contains(":import:load"));
            var graph = run("plan", root.toString(), "--graph", "mermaid"); assertEquals(0, graph.code(), graph.err());
            assertTrue(graph.out().contains(":validation:check")); assertTrue(graph.out().contains(":download:fetch"));
            assertEquals(0, requests.get()); assertFalse(Files.exists(temp.resolve("cleanup.txt"))); assertFalse(Files.exists(temp.resolve("gretl-reports")));
            Path output = temp.resolve("output.txt"); Path report = temp.resolve("run.json");
            var result = run("run", root.toString(), "--param", ":download:url=http://127.0.0.1:" + server.getAddress().getPort() + "/data", "--param", ":output=" + output, "--report", report.toString(), "--keep-work", "always");
            assertEquals(0, result.code(), result.err()); assertEquals(1, requests.get());
            assertEquals("synthetic payload ü\n", Files.readString(output)); assertEquals("done", Files.readString(temp.resolve("cleanup.txt")));
            var reportJson = object(Json.parse(Files.readString(report)));
            List<String> expectedOrder = List.of(":download:fetch", ":validation:check", ":import:load");
            var tasks = (List<?>) object(reportJson.get("execution")).get("tasks");
            assertEquals(expectedOrder, tasks.stream().map(t -> object(t).get("taskId")).toList());
            String runId = object(reportJson.get("identity")).get("runId").toString();
            try (var files = Files.list(temp.resolve("gretl-reports"))) {
                var events = files.filter(p -> p.toString().endsWith(".events.jsonl")).toList(); assertEquals(1, events.size());
                String text = Files.readString(events.getFirst());
                assertEquals(1, text.lines().filter(line -> line.contains("\"event_name\":\"gretl.run.started\"")).count());
                assertEquals(1, text.lines().filter(line -> line.contains("\"event_name\":\"gretl.run.finished\"")).count());
                var parsed = text.lines().map(Json::parse).map(CliProjectRunTest::object).toList();
                assertEquals(expectedOrder, parsed.stream().filter(e -> "gretl.task.started".equals(e.get("event_name"))).map(e -> e.get("task_id")).toList());
                assertTrue(parsed.stream().allMatch(e -> !e.containsKey("run_id") || runId.equals(e.get("run_id"))));
            }
        } finally { server.stop(0); }
    }

    @Test
    void prj114InvalidUnknownTargetsAndMissingSettingsNeverStartARun() throws Exception {
        Path root = temp.resolve("project"); write(root, "settings.gretl", "defaults ':missing'\n"); write(root, "job.groovy", "action('run') { throw new AssertionError('not executed') }\n");
        var unknown = run("run", root.toString()); assertEquals(2, unknown.code()); assertTrue(unknown.err().contains("UNKNOWN_DEFAULT_TARGET"), unknown.err());
        write(root, "settings.gretl", "defaults ':run'\n");
        var invalid = run("run", root.toString(), "--target", "run"); assertEquals(2, invalid.code()); assertTrue(invalid.err().contains("PROJECT_TARGET_INVALID"), invalid.err());
        var target = run("run", root.toString(), "--target", ":missing"); assertEquals(2, target.code()); assertTrue(target.err().contains("UNKNOWN_TARGET"), target.err()); assertTrue(target.err().contains("missing"));
        Files.delete(root.resolve("settings.gretl")); var missing = run("run", root.toString()); assertEquals(2, missing.code()); assertTrue(missing.err().contains("PROJECT_SETTINGS_MISSING"), missing.err());
        assertFalse(Files.exists(temp.resolve("gretl-reports")));
    }

    @Test
    void prj108QualifiedParametersSharePrecedenceTypesAndGlobalAliases() throws Exception {
        Path root = temp.resolve("project");
        write(root, "settings.gretl", "include 'a', 'b'\ndefaults ':a:run'\n");
        write(root, "job.groovy", "parameter('output', description: 'Output', type: 'path', defaultValue: 'actual.txt')\n");
        write(root, "a/job.groovy", """
                parameter('count', description: 'Count', type: 'integer', defaultValue: '1')
                action('run') {
                    assert parameter('count') instanceof Long
                    assert parameter(':b:enabled') instanceof Boolean
                    assert parameter(':b:enabled')
                    assert ctx.config('shared').label == 'global'
                    assert secret('shared').reveal() == 'synthetic-secret'
                    java.nio.file.Files.writeString(parameter(':output'), parameter('count').toString())
                }
                """);
        write(root, "b/job.groovy", "parameter('enabled', description: 'Enabled', type: 'boolean', required: true)\naction('unused') { throw new AssertionError('not selected') }\n");
        Path config = write(temp, "conf/runtime.json", Json.write(Map.of("schemaVersion", 1,
                "params", Map.of(":a:count", "2", ":b:enabled", "true", "otherJob", "ignored"),
                "taskConfig", Map.of("shared", Map.of("label", "global")),
                "secrets", Map.of("shared", Map.of("file", "secret.txt")))));
        write(temp, "conf/secret.txt", "synthetic-secret\n");
        Path params = write(temp, "conf/params.json", Json.write(Map.of(":a:count", "3")));
        for (int expected : List.of(2, 3, 4)) {
            var args = new java.util.ArrayList<>(List.of("run", root.toString(), "--config", config.toString()));
            if (expected >= 3) args.addAll(List.of("--params-file", params.toString()));
            if (expected == 4) args.addAll(List.of("--param", ":a:count=4"));
            var result = run(args.toArray(String[]::new)); assertEquals(0, result.code(), result.err());
            assertEquals(Integer.toString(expected), Files.readString(temp.resolve("actual.txt")));
            assertFalse(result.err().contains("synthetic-secret"));
        }
        // Declaration default, while the required value and aliases still use the common resolver.
        Files.writeString(config, Json.write(Map.of("schemaVersion", 1, "params", Map.of(":b:enabled", "true"),
                "taskConfig", Map.of("shared", Map.of("label", "global")),
                "secrets", Map.of("shared", Map.of("file", "secret.txt")))));
        var defaults = run("run", root.toString(), "--config", config.toString()); assertEquals(0, defaults.code(), defaults.err());
        assertEquals("1", Files.readString(temp.resolve("actual.txt")));
    }

    @Test
    void prj108MissingUnselectedRequiredAndInvalidExplicitParametersFailBeforeRun() throws Exception {
        Path root = temp.resolve("project");
        write(root, "settings.gretl", "include 'a', 'b'\ndefaults ':a:run'\n");
        write(root, "a/job.groovy", "action('run') { throw new AssertionError('never run') }\n");
        write(root, "b/job.groovy", "parameter('count', description: 'Count', type: 'integer', required: true)\n");
        for (String command : List.of("check", "plan")) {
            var result = run(command, root.toString()); assertEquals(0, result.code(), result.err());
        }
        var help = run("run", root.toString(), "--help"); assertEquals(0, help.code(), help.err());
        assertTrue(help.out().contains(":b:count"));
        var missing = run("run", root.toString()); assertEquals(2, missing.code());
        assertTrue(missing.err().contains("MISSING_REQUIRED_PARAMETER"), missing.err()); assertTrue(missing.err().contains(":b:count"));
        for (String value : List.of("count=2", ":unknown=2", ":b:count=not-an-integer")) {
            var result = run("run", root.toString(), "--param", value); assertEquals(2, result.code(), result.err());
            assertTrue(result.err().contains(value.startsWith(":b:count=") ? "INVALID_PARAMETER_VALUE" : "UNKNOWN_PARAMETER"), result.err());
        }
        Path params = write(temp, "params.json", "{\":b:count\":\"2\",\"count\":\"3\"}");
        var unknown = run("run", root.toString(), "--params-file", params.toString()); assertEquals(2, unknown.code());
        assertTrue(unknown.err().contains("UNKNOWN_PARAMETER"), unknown.err()); assertTrue(unknown.err().contains("count"));
        assertFalse(Files.exists(temp.resolve("gretl-reports")));
    }

    private Path write(Path root, String name, String text) throws Exception { Path p = root.resolve(name); Files.createDirectories(p.getParent()); Files.writeString(p, text); return p; }
    private Result run(String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int code = new GretlCli(new PrintStream(out), new PrintStream(err), Map.of(), temp, getClass().getClassLoader()).run(args);
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
    record Result(int code, String out, String err) {}
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) { return (Map<String, Object>) value; }
}
