package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Phase 2 script-project proof through the candidate distribution, without packaging a project. */
class ProjectScriptLocalJobIT {
    @Test
    void prj101102107112PublishedExampleRunsAsOneGraphFromForeignCwd() throws Exception {
        Path root = copyExample("complete");
        var result = invoke(root, "run", "run", "--param", ":output=output.txt");
        assertEquals(0, result.code(), result.err());
        assertArrayEquals(Files.readAllBytes(root.resolve("project with spaces/prepare/data/input.txt")),
                Files.readAllBytes(root.resolve("foreign cwd/output.txt")));
        Map<String, Object> report = object(Json.parse(Files.readString(root.resolve("run/report.json"))));
        List<String> expected = List.of(":prepare:run", ":checks:local:run", ":publish:run");
        assertRun(root, "run", report, expected);
        assertEquals("SUCCEEDED", object(report.get("execution")).get("status"));
        assertTrue(object(report.get("source")).get("sha256").toString().matches("[0-9a-f]{64}"));
        var plan = objects(object(report.get("execution")).get("selectedPlan"));
        for (int i = 0; i < plan.size(); i++) {
            String script = List.of("prepare/job.groovy", "checks/local/job.groovy", "publish/job.groovy").get(i);
            var source = object(plan.get(i).get("source"));
            assertEquals(root.resolve("project with spaces").resolve(script).toRealPath().toString(), source.get("file"));
            assertEquals(1L, source.get("line"));
        }
    }

    @Test
    void prj103110111PlanningSelectionAndExplicitSingleFileRemainDistinct() throws Exception {
        Path root = copyExample("selection");
        for (String command : List.of("check", "plan")) {
            var result = invoke(root, command, command, command.equals("plan") ? new String[]{"--graph", "mermaid"} : new String[]{});
            assertEquals(0, result.code(), result.err());
            if (command.equals("plan")) assertTrue(result.out().contains(":checks:local:run"));
            assertFalse(Files.exists(root.resolve(command + "/reports")));
        }
        var help = invoke(root, "help", "run", "--help"); assertEquals(0, help.code(), help.err());
        assertTrue(help.out().contains(":output")); assertFalse(Files.exists(root.resolve("help/reports")));
        var selected = invoke(root, "selected", "run", "--target", ":checks:local:run", "--param", ":output=unused.txt");
        assertEquals(0, selected.code(), selected.err()); assertFalse(Files.exists(root.resolve("foreign cwd/unused.txt")));
        assertRun(root, "selected", object(Json.parse(Files.readString(root.resolve("selected/report.json")))),
                List.of(":prepare:run", ":checks:local:run"));
        Path project = root.resolve("project with spaces");
        Files.writeString(project.resolve("job.groovy"), "action('legacy:run') { java.nio.file.Files.writeString(path('legacy.txt'), 'single') }\ndefaults 'legacy:run'\n");
        Files.writeString(project.resolve("settings.gretl"), "System.exit(99)\n");
        var single = launch(root, "single", "run", project.resolve("job.groovy"), new String[]{});
        assertEquals(0, single.code(), single.err());
        assertEquals("single", Files.readString(root.resolve("foreign cwd/legacy.txt")));
        assertRun(root, "single", object(Json.parse(Files.readString(root.resolve("single/report.json")))), List.of("legacy:run"));
    }

    @Test
    void prj112FailureReportAndEventsReferToOriginalScriptLineAndQualifiedTask() throws Exception {
        Path root = copyExample("failure"); Path script = root.resolve("project with spaces/publish/job.groovy");
        Files.writeString(script, "action('run', dependsOn: ':checks:local:run') {\n    def text = 'synthetic failure'\n    throw new IllegalStateException(text)\n}\n");
        var result = invoke(root, "failure", "run", "--param", ":output=unused.txt");
        assertEquals(10, result.code(), result.err());
        String text = Files.readString(root.resolve("failure/report.json"));
        var report = object(Json.parse(text)); assertRun(root, "failure", report,
                List.of(":prepare:run", ":checks:local:run", ":publish:run"));
        var execution = object(report.get("execution")); assertEquals("FAILED", execution.get("status"));
        String errorRef = objects(execution.get("tasks")).getLast().get("errorRef").toString();
        var failure = objects(report.get("errors")).stream().filter(e -> e.get("id").equals(errorRef)).findFirst().orElseThrow();
        assertEquals(script.toRealPath().toString(), object(failure.get("source")).get("file"));
        assertEquals(3L, object(failure.get("source")).get("line"));
        assertTrue(text.contains("IllegalStateException")); assertFalse(text.matches("(?s).*GretlProject_[a-f0-9]+\\.groovy.*"));
        Path events = eventFile(root, "failure");
        var event = Files.readAllLines(events).stream().map(Json::parse).map(ProjectScriptLocalJobIT::object)
                .filter(e -> "gretl.task.failed".equals(e.get("event_name"))).findFirst().orElseThrow();
        assertEquals(":publish:run", event.get("task_id"));
        assertTrue(Files.readString(events).contains(script.toRealPath().toString()));
    }

    private Path copyExample(String name) throws Exception {
        Path root = Files.createDirectories(Path.of("target/project-script-evidence", name + "-" + UUID.randomUUID()).toAbsolutePath());
        Path original = Path.of("../examples/project-pipeline").toRealPath();
        try (var paths = Files.walk(original)) {
            for (Path source : paths.toList()) {
                Path target = root.resolve("project with spaces").resolve(original.relativize(source));
                if (Files.isDirectory(source)) Files.createDirectories(target); else Files.copy(source, target);
            }
        }
        Files.createDirectories(root.resolve("foreign cwd")); return root;
    }

    private Result invoke(Path root, String id, String command, String... args) throws Exception {
        return launch(root, id, command, root.resolve("project with spaces"), args);
    }

    private Result launch(Path root, String id, String verb, Path input, String[] args) throws Exception {
        Path evidence = Files.createDirectories(root.resolve(id));
        Path config = evidence.resolve("runtime.json");
        Files.writeString(config, Json.write(Map.of("schemaVersion", 1, "runtime", Map.of(
                "workRoot", evidence.resolve("work").toString(), "reportRoot", evidence.resolve("reports").toString()))));
        Path distribution = Path.of("../gretl-dist/target/dist").toRealPath();
        var command = new ArrayList<>(List.of(distribution.resolve("bin/gretl").toString(), verb, input.toString(),
                "--config", config.toString()));
        if (verb.equals("run")) command.addAll(List.of("--report", evidence.resolve("report.json").toString()));
        command.addAll(List.of(args));
        Instant start = Instant.now();
        var process = new ProcessBuilder(command).directory(root.resolve("foreign cwd").toFile())
                .redirectOutput(evidence.resolve("stdout.log").toFile()).redirectError(evidence.resolve("stderr.log").toFile()).start();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("command", command); metadata.put("cwd", root.resolve("foreign cwd").toString());
        metadata.put("startedAt", start.toString()); metadata.put("finishedAt", Instant.now().toString());
        metadata.put("exitCode", finished ? process.exitValue() : -1); metadata.put("timedOut", !finished);
        metadata.put("distributionManifestSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(distribution.resolve("SHA256SUMS")))));
        Files.writeString(evidence.resolve("execution.json"), Json.write(metadata));
        assertTrue(finished, "Distribution process timed out: " + evidence);
        return new Result(process.exitValue(), Files.readString(evidence.resolve("stdout.log")), Files.readString(evidence.resolve("stderr.log")));
    }

    private void assertRun(Path root, String id, Map<String, Object> report, List<String> expected) throws Exception {
        var execution = object(report.get("execution"));
        assertEquals(expected, objects(execution.get("tasks")).stream().map(t -> t.get("taskId")).toList());
        assertEquals(expected, objects(execution.get("selectedPlan")).stream().map(t -> t.get("taskId")).toList());
        String runId = object(report.get("identity")).get("runId").toString();
        var events = Files.readAllLines(eventFile(root, id)).stream().map(Json::parse).map(ProjectScriptLocalJobIT::object).toList();
        assertEquals(1, events.stream().filter(e -> "gretl.run.started".equals(e.get("event_name"))).count());
        assertEquals(1, events.stream().filter(e -> "gretl.run.finished".equals(e.get("event_name"))).count());
        assertEquals(expected, events.stream().filter(e -> "gretl.task.started".equals(e.get("event_name"))).map(e -> e.get("task_id")).toList());
        assertTrue(events.stream().allMatch(e -> !e.containsKey("run_id") || runId.equals(e.get("run_id"))));
        try (var files = Files.list(root.resolve(id))) { assertEquals(1, files.filter(p -> p.getFileName().toString().equals("report.json")).count()); }
    }

    private Path eventFile(Path root, String id) throws Exception {
        try (var files = Files.list(root.resolve(id + "/reports"))) {
            var events = files.filter(p -> p.toString().endsWith(".events.jsonl")).toList(); assertEquals(1, events.size()); return events.getFirst();
        }
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) { return (Map<String, Object>) value; }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> objects(Object value) { return (List<Map<String, Object>>) value; }
    record Result(int code, String out, String err) {}
}
