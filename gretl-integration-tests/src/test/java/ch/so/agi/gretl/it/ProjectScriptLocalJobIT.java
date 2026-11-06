package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Script-project execution proof through the candidate distribution, without packaging a project. */
class ProjectScriptLocalJobIT {
    @Test
    void publishedExampleRunsAsOneGraphFromForeignCwd() throws Exception {
        Path root = copyExample("complete");
        var result = invoke(root, "run", "run", "--param", ":output=output.txt");
        assertEquals(0, result.code(), result.err());
        assertArrayEquals(Files.readAllBytes(root.resolve("project with spaces/prepare/data/input.txt")),
                Files.readAllBytes(root.resolve("run/cwd/output.txt")));
        Map<String, Object> report = object(Json.parse(Files.readString(root.resolve("run/reports/report.json"))));
        List<String> expected = List.of(":prepare:run", ":checks:local:run", ":publish:run");
        assertRun(root, "run", report, expected);
        assertEquals("SUCCEEDED", object(report.get("execution")).get("status"));
        assertTrue(object(report.get("source")).get("sha256").toString().matches("[0-9a-f]{64}"));
        var plan = objects(object(report.get("execution")).get("selectedPlan"));
        for (int i = 0; i < plan.size(); i++) {
            String script = List.of("prepare/job.groovy", "checks/local/job.groovy", "publish/job.groovy").get(i);
            var source = object(plan.get(i).get("source"));
            assertEquals(originalSource(root,"run",script), source.get("file"));
            assertEquals(1L, source.get("line"));
        }
    }

    @Test
    void planningSelectionAndExplicitSingleFileRemainDistinct() throws Exception {
        Path root = copyExample("selection");
        for (String command : List.of("check", "plan")) {
            var result = invoke(root, command, command, command.equals("plan") ? new String[]{"--graph", "mermaid"} : new String[]{});
            assertEquals(0, result.code(), result.err());
            if (command.equals("plan")) assertTrue(result.out().contains(":checks:local:run"));
            assertNoRunWork(root.resolve(command));
        }
        var help = invoke(root, "help", "run", "--help"); assertEquals(0, help.code(), help.err());
        assertTrue(help.out().contains(":output")); assertNoRunWork(root.resolve("help"));
        var selected = invoke(root, "selected", "run", "--target", ":checks:local:run", "--param", ":output=unused.txt");
        assertEquals(0, selected.code(), selected.err()); assertFalse(Files.exists(root.resolve("selected/cwd/unused.txt")));
        assertRun(root, "selected", object(Json.parse(Files.readString(root.resolve("selected/reports/report.json")))),
                List.of(":prepare:run", ":checks:local:run"));
        Path project = root.resolve("project with spaces");
        Files.writeString(project.resolve("job.groovy"), "action('legacy:run') { java.nio.file.Files.writeString(path('legacy.txt'), 'single') }\ndefaults 'legacy:run'\n");
        Files.writeString(project.resolve("settings.gretl"), "System.exit(99)\n");
        var single = launch(root, "single", "run", project.resolve("job.groovy"), new String[]{});
        assertEquals(0, single.code(), single.err());
        assertEquals("single", Files.readString(root.resolve("single/cwd/legacy.txt")));
        assertRun(root, "single", object(Json.parse(Files.readString(root.resolve("single/reports/report.json")))), List.of("legacy:run"));
    }

    @Test
    void failureReportAndEventsReferToOriginalScriptLineAndQualifiedTask() throws Exception {
        Path root = copyExample("failure"); Path script = root.resolve("project with spaces/publish/job.groovy");
        Files.writeString(script, "action('run', dependsOn: ':checks:local:run') {\n    def text = 'synthetic failure'\n    throw new IllegalStateException(text)\n}\n");
        var result = invoke(root, "failure", "run", "--param", ":output=unused.txt");
        assertEquals(10, result.code(), result.err());
        String text = Files.readString(root.resolve("failure/reports/report.json"));
        var report = object(Json.parse(text)); assertRun(root, "failure", report,
                List.of(":prepare:run", ":checks:local:run", ":publish:run"));
        var execution = object(report.get("execution")); assertEquals("FAILED", execution.get("status"));
        String errorRef = objects(execution.get("tasks")).getLast().get("errorRef").toString();
        var failure = objects(report.get("errors")).stream().filter(e -> e.get("id").equals(errorRef)).findFirst().orElseThrow();
        assertEquals(originalSource(root,"failure","publish/job.groovy"), object(failure.get("source")).get("file"));
        assertEquals(3L, object(failure.get("source")).get("line"));
        assertTrue(text.contains("IllegalStateException")); assertFalse(text.matches("(?s).*GretlProject_[a-f0-9]+\\.groovy.*"));
        Path events = eventFile(root, "failure");
        var event = Files.readAllLines(events).stream().map(Json::parse).map(ProjectScriptLocalJobIT::object)
                .filter(e -> "gretl.task.failed".equals(e.get("event_name"))).findFirst().orElseThrow();
        assertEquals(":publish:run", event.get("task_id"));
        assertTrue(Files.readString(events).contains(originalSource(root,"failure","publish/job.groovy")));
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
        Path directory = Files.createDirectories(root.resolve(id));
        Path fixture = root.resolve("project with spaces");
        List<String> targets = new ArrayList<>();
        Map<String, String> parameters = new LinkedHashMap<>();
        boolean help = false;
        String graph = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--target" -> targets.add(args[++i]);
                case "--param" -> { String value = args[++i]; int equals = value.indexOf('='); parameters.put(value.substring(0, equals), value.substring(equals + 1)); }
                case "--help" -> help = true;
                case "--graph" -> graph = args[++i];
                default -> throw new IllegalArgumentException("Unsupported project probe argument");
            }
        }
        var invocation = new ArtifactJobRunner.Invocation(ArtifactJobRunner.Verb.valueOf(verb.toUpperCase(java.util.Locale.ROOT)), help, graph, null);
        var selected = input.equals(fixture) ? ArtifactJobRunner.JobInput.project(".")
                : ArtifactJobRunner.JobInput.script(fixture.relativize(input).toString());
        var request = new ArtifactJobRunner.Request("project-script-" + id, fixture, directory, targets, parameters, Map.of(),
                JobRunner.baseConfig(directory), Map.of(), java.time.Duration.ofSeconds(60), selected, invocation);
        var result = ArtifactJobRunner.run(request, ArtifactJobRunner.Mode.configured()==ArtifactJobRunner.Mode.IMAGE ? ArtifactJobRunner.Mode.IMAGE : ArtifactJobRunner.Mode.SCRIPT);
        return new Result(result.code(), result.out(), result.err());
    }
    private String originalSource(Path root,String id,String relative) throws Exception {
        return ArtifactJobRunner.Mode.configured()==ArtifactJobRunner.Mode.IMAGE ? "/job/"+relative : root.resolve(id+"/source/"+relative).toRealPath().toString();
    }
    private static void assertNoRunWork(Path root) throws Exception {
        if(ArtifactJobRunner.Mode.configured()!=ArtifactJobRunner.Mode.IMAGE) assertFalse(Files.exists(root.resolve("work")));
        else try(var files=Files.list(root.resolve("work"))) { assertEquals(java.util.Set.of("home","tmp"),files.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet())); }
        try (var files = Files.list(root.resolve("reports"))) { assertEquals(0, files.count()); }
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
        try (var files = Files.list(root.resolve(id + "/reports"))) { assertEquals(1, files.filter(p -> p.getFileName().toString().equals("report.json")).count()); }
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
