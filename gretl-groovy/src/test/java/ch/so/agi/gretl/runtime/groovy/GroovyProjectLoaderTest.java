package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import ch.so.agi.gretl.runtime.runner.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroovyProjectLoaderTest {
    @TempDir Path temp;

    @Test
    void prj101SameBasenamesAndLocalNamesKeepBodiesBindingsAndOneDefinition() throws Exception {
        var h = new GroovyHarness(temp);
        write(h, "settings.gretl", "include 'a', 'b:c'\ndefaults ':run', ':a:run', ':b:c:run'\n");
        write(h, "job.groovy", "def value = 'root'\naction('run') { value }\ndefaults 'ignored'\n");
        write(h, "a/job.groovy", "assert !binding.hasVariable('value')\nvalue = 'a'\ndef own = binding.getVariable('value')\naction('run') { own }\ndefaults 'ignored'\n");
        write(h, "b/c/job.groovy", "assert !binding.hasVariable('value')\nvalue = 'bc'\ndef own = binding.getVariable('value')\naction('run') { own }\ndefaults 'ignored'\n");
        var definition = load(h);
        assertEquals(List.of(":run", ":a:run", ":b:c:run"), definition.tasks().stream().map(TaskDefinition::id).toList());
        assertEquals(definition.defaultTargets(), definition.tasks().stream().map(TaskDefinition::id).toList());
        var outcome = h.run(definition, Map.of());
        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals("root", value(outcome, ":run"));
        assertEquals("a", value(outcome, ":a:run"));
        assertEquals("bc", value(outcome, ":b:c:run"));
        assertEquals(1, h.events.count("gretl.run.started"));
        assertEquals(1, h.events.count("gretl.run.finished"));
        assertNotEquals(definition.definitionId(), load(h).definitionId());
    }

    @Test
    void prj104ForwardCrossProjectTypedResultUsesActualDependencyAndOperationFields() throws Exception {
        var h = new GroovyHarness(temp);
        write(h, "settings.gretl", "include 'consumer', 'producer'\ndefaults ':consumer:run'\n");
        write(h, "consumer/job.groovy", "task('run', Echo, dependsOn: ':producer:run') { text = result(':producer:run').result + '-consumer' }\n");
        write(h, "producer/job.groovy", "task('run', Echo) { text = 'producer' }\n");
        var definition = load(h);
        assertEquals(List.of(":producer:run"), definition.task(":consumer:run").orElseThrow().dependsOn().stream().map(r -> r.id()).toList());
        var outcome = h.run(definition, Map.of());
        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals("producer-consumer", ((TestGroovyOperations.EchoResult) value(outcome, ":consumer:run")).result());
        assertEquals(List.of(":producer:run", ":consumer:run"), outcome.tasks().stream().map(TaskOutcome::taskId).toList());
    }

    @Test
    void prj108RootLocalAndAbsoluteParametersRetainConversions() throws Exception {
        var h = new GroovyHarness(temp);
        write(h, "settings.gretl", "include 'a'\ndefaults ':a:run'\n");
        write(h, "job.groovy", "parameter('root', description: 'Root', defaultValue: 'R')\n");
        write(h, "a/job.groovy", "parameter('count', description: 'Count', type: 'integer', required: true)\naction('run') { parameter(':root') + '-' + parameter('count', Long) }\n");
        var definition = load(h);
        assertEquals(List.of(":root", ":a:count"), definition.parameters().stream().map(ParameterDefinition::name).toList());
        assertEquals("R-9", value(h.run(definition, Map.of(":a:count", "9")), ":a:run"));
    }

    @Test
    void prj112ProjectSourcesUseOriginalFilesForRegistrationAndRuntimeFailure() throws Exception {
        var h = new GroovyHarness(temp);
        write(h, "settings.gretl", "include 'a:b'\ndefaults ':a:b:run'\n");
        Path source = write(h, "a/b/job.groovy", "// heading\naction('run') {\n throw new IllegalStateException('original-error')\n}\n");
        var definition = load(h);
        assertEquals(source.toString(), definition.task(":a:b:run").orElseThrow().source().file());
        assertEquals(2, definition.task(":a:b:run").orElseThrow().source().line());
        var outcome = h.run(definition, Map.of());
        assertEquals(RunStatus.FAILED, outcome.status());
        var failure = outcome.tasks().getFirst().failure().orElseThrow();
        assertEquals(source.toString(), failure.source().orElseThrow().file());
        assertEquals(3, failure.source().orElseThrow().line());
        assertTrue(failure.message().contains("original-error"));
    }

    private JobDefinition load(GroovyHarness h) {
        return new GroovyJobLoader().loadProject(new GretlProjectParser().parse(h.jobDir), h.environment);
    }

    static Object value(JobOutcome outcome, String id) {
        return ((ResultSlot.Value) outcome.tasks().stream().filter(t -> t.taskId().equals(id)).findFirst().orElseThrow().slot()).value();
    }

    private Path write(GroovyHarness h, String name, String content) throws Exception {
        Path path = h.jobDir.resolve(name); Files.createDirectories(path.getParent()); Files.writeString(path, content); return path.toRealPath();
    }
}
