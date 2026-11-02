package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** One source graph and the same independent oracles in every configured process mode. */
class ProjectProcessLocalJobIT {
    private static final List<String> ORDER = List.of(":download:run", ":validation:nested:run", ":import:run");
    @ParameterizedTest
    @CsvSource({"success,never", "success,always", "success,on-failure", "fail,never", "fail,always", "fail,on-failure", "empty,never", "empty,always", "empty,on-failure"})
    void graphResultsOriginalCausesAndRetentionHaveTheSameContract(String outcome, String keep) throws Exception {
        var request = request(outcome, keep, List.of());
        var result = ArtifactJobRunner.run(request);
        boolean failed = outcome.equals("fail");
        assertEquals(failed ? 10 : 0, result.code(), result.err()); assertFalse(result.timedOut());
        var report = object(Json.parseFile(result.report(), 8L << 20));
        var execution = object(report.get("execution"));
        assertEquals(failed ? "FAILED" : outcome.equals("empty") ? "NO_DATA" : "SUCCEEDED", execution.get("status"));
        assertEquals(ORDER, ((List<?>)execution.get("tasks")).stream().map(t -> object(t).get("taskId")).toList());
        assertEquals(keep.equals("always") || keep.equals("on-failure") && failed, execution.get("workRetained"));
        assertEquals("ivd", Files.readString(request.directory().resolve("output/cleanup.txt")));
        if (outcome.equals("success"))
            assertEquals("download-A\n|import-C\n|9|true|helper", Files.readString(request.directory().resolve("output/result.txt")));
        else assertFalse(Files.exists(request.directory().resolve("output/result.txt")));
        if (failed) {
            String errors = Json.write(report.get("errors"));
            assertTrue(errors.contains("synthetic-project-failure")); assertTrue(errors.contains("original-cause"));
            var error = ((List<?>)report.get("errors")).stream().map(ProjectProcessLocalJobIT::object)
                    .filter(e -> String.valueOf(e.get("message")).contains("synthetic-project-failure")).findFirst().orElseThrow();
            assertTrue(object(error.get("source")).get("file").toString().endsWith("import/job.groovy"));
            assertEquals(6L, object(error.get("source")).get("line"));
        }
        assertTrue(Files.exists(request.directory().resolve("source/download/sql/update.sql")), "External input remains available");
        assertEquals("validation-B\n", Files.readString(request.directory().resolve("source/validation/nested/sql/update.sql")));
    }
    @Test void selectedDependencyClosureKeepsSameNamedLocalResourcesDistinct() throws Exception {
        var request = request("success", "never", List.of(":validation:nested:run"));
        var result = ArtifactJobRunner.run(request); assertEquals(0, result.code(), result.err());
        var report = object(Json.parseFile(result.report(), 8L << 20));
        assertEquals(ORDER.subList(0,2), ((List<?>)object(report.get("execution")).get("tasks")).stream().map(t -> object(t).get("taskId")).toList());
        assertFalse(Files.exists(request.directory().resolve("output/result.txt")));
        assertFalse(Files.exists(request.directory().resolve("cwd/unrelated-marker.txt")));
        assertEquals("download-A\n", Files.readString(request.directory().resolve("source/download/sql/update.sql")));
        assertEquals("validation-B\n", Files.readString(request.directory().resolve("source/validation/nested/sql/update.sql")));
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "cycle"})
    void invalidGraphFailsAtTheRealProductBeforeAnyTask(String kind) throws Exception {
        Path root = ArtifactJobRunner.directory("project-invalid-" + kind);
        Path input = Files.createDirectories(root.resolve("fixture"));
        Files.writeString(input.resolve("settings.gretl"), "defaults ':a'\n");
        Files.writeString(input.resolve("job.groovy"), "action('a', dependsOn: ':" + (kind.equals("cycle") ? "b" : "absent")
                + "') { throw new AssertionError('task executed') }\n" + (kind.equals("cycle") ? "action('b', dependsOn: ':a') { }\n" : ""));
        Files.writeString(input.resolve("job-package.json"), Json.write(Map.of("schemaVersion",3,"name","invalid-project","version","1",
                "runtimeVersion","6.0.0-SNAPSHOT","settingsFile","settings.gretl","sources",List.of(),"resources",List.of(),"taskModules",List.of(),"standardFunctions",false,"dependencies",List.of())));
        var result = ArtifactJobRunner.run(new ArtifactJobRunner.Request("project-invalid-" + kind, input, root, List.of(), Map.of(), Map.of(),
                JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(45), ArtifactJobRunner.JobInput.project("."), ArtifactJobRunner.Invocation.run()));
        assertEquals(2, result.code(), result.err()); assertFalse(result.timedOut());
        assertFalse(result.err().contains("ClassNotFoundException"), result.err());
        assertFalse(Files.exists(result.report()));
        if(ArtifactJobRunner.Mode.configured()!=ArtifactJobRunner.Mode.IMAGE) assertFalse(Files.exists(root.resolve("work")));
        else try(var files=Files.list(root.resolve("work"))) { assertEquals(Set.of("home","tmp"),files.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet())); }
        assertTrue(result.err().toLowerCase(java.util.Locale.ROOT).contains(kind.equals("cycle") ? "zykl" : "absent"), result.err());
    }
    @ParameterizedTest @ValueSource(strings = {":a:run", ":b:run"})
    void rootAndSameNamedLocalParametersKeepTheirNamespaces(String target) throws Exception {
        Path root = ArtifactJobRunner.directory("project-parameter-namespaces");
        Path input = Files.createDirectories(root.resolve("fixture"));
        Files.writeString(input.resolve("settings.gretl"), "include 'a', 'b'\ndefaults ':b:run'\n");
        Files.writeString(input.resolve("job.groovy"), "parameter('value', required: true, description: 'Root value')\nparameter('output', type: 'path', required: true, description: 'Output')\n");
        Files.createDirectories(input.resolve("a")); Files.createDirectories(input.resolve("b"));
        Files.writeString(input.resolve("a/job.groovy"), "parameter('value', required: true, description: 'A value')\naction('run') { def v = parameter(':value') + '/' + parameter('value'); java.nio.file.Files.writeString(parameter(':output').resolve('a.txt'), v); v }\n");
        Files.writeString(input.resolve("b/job.groovy"), "parameter('value', required: true, description: 'B value')\naction('run', dependsOn: ':a:run') { java.nio.file.Files.writeString(parameter(':output').resolve('b.txt'), result(':a:run') + '/' + parameter('value')) }\n");
        Files.writeString(input.resolve("job-package.json"), Json.write(Map.of("schemaVersion",3,"name","parameter-project","version","1",
                "runtimeVersion","6.0.0-SNAPSHOT","settingsFile","settings.gretl","sources",List.of(),"resources",List.of(),"taskModules",List.of(),"standardFunctions",false,"dependencies",List.of())));
        var result = ArtifactJobRunner.run(new ArtifactJobRunner.Request("project-parameter-namespaces", input, root, List.of(target),
                Map.of(":value","ROOT",":a:value","A",":b:value","B",":output",root.resolve("output")),Map.of(),JobRunner.baseConfig(root),Map.of(),Duration.ofSeconds(45),
                ArtifactJobRunner.JobInput.project("."),ArtifactJobRunner.Invocation.run()));
        assertEquals(0,result.code(),result.err()); assertFalse(result.timedOut());
        assertEquals("ROOT/A",Files.readString(root.resolve("output/a.txt")));
        if (target.equals(":b:run")) assertEquals("ROOT/A/B",Files.readString(root.resolve("output/b.txt")));
        else assertFalse(Files.exists(root.resolve("output/b.txt")));
    }
    @Test void externalProjectNeedsNoPackageDescriptor() throws Exception {
        var original = request("success", "never", List.of());
        Path fixture = original.directory().resolve("without-descriptor");
        SelectedPackageSupport.copy(original.jobDirectory(), fixture);
        Files.delete(fixture.resolve("job-package.json"));
        var request = new ArtifactJobRunner.Request(original.id(),fixture,original.directory(),original.targets(),original.parameters(),original.environment(),original.config(),original.endpoints(),original.timeout(),original.input(),original.invocation());
        var result = ArtifactJobRunner.run(request, ArtifactJobRunner.Mode.configured()==ArtifactJobRunner.Mode.IMAGE ? ArtifactJobRunner.Mode.IMAGE : ArtifactJobRunner.Mode.SCRIPT);
        assertEquals(0,result.code(),result.err());
        assertEquals("download-A\n|import-C\n|9|true|helper",Files.readString(request.directory().resolve("output/result.txt")));
        assertFalse(Files.exists(request.directory().resolve("job.jar")));
        assertFalse(Files.exists(request.directory().resolve("source/job-package.json")));
    }
    private ArtifactJobRunner.Request request(String outcome, String keep, List<String> targets) throws Exception {
        Path root = ArtifactJobRunner.directory("common-project-" + outcome + "-" + keep);
        var config = JobRunner.baseConfig(root);
        var runtime = new LinkedHashMap<>((Map<String,Object>)config.get("runtime")); runtime.put("keepWork", keep); config.put("runtime",runtime);
        Path fixture = Path.of(getClass().getResource("/projects/pipeline").toURI());
        return new ArtifactJobRunner.Request("common-project-" + outcome + "-" + keep, fixture, root, targets,
                Map.of(":output",root.resolve("output/result.txt"),":mode",outcome,":import:count","9"),Map.of(),config,Map.of(),Duration.ofSeconds(60),
                ArtifactJobRunner.JobInput.project("."),ArtifactJobRunner.Invocation.run());
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>)value; }
}
