package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.runtime.runner.ExecutionPlan;
import ch.so.agi.gretl.runtime.runner.ExecutionPlanner;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import ch.so.agi.gretl.runtime.runner.ParameterMode;
import ch.so.agi.gretl.runtime.runner.ParameterResolver;
import ch.so.agi.gretl.runtime.runner.ParameterSources;
import ch.so.agi.gretl.runtime.runner.ResolvedParameters;
import ch.so.agi.gretl.runtime.runner.RunEvent;
import ch.so.agi.gretl.runtime.runner.RunEventSink;
import ch.so.agi.gretl.runtime.runner.RunInput;
import ch.so.agi.gretl.runtime.runner.SequentialJobRunner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Small executable M2 harness; test fixture only, not a product runner. */
final class GroovyHarness {
    final Path tempDir;
    final Path jobDir;
    final RecordingEvents events = new RecordingEvents();
    final RecordingMessages messages = new RecordingMessages();
    final OperationRegistry registry;
    final DefinitionEnvironment environment;

    GroovyHarness(Path tempDir) throws IOException {
        this.tempDir = tempDir;
        this.jobDir = Files.createDirectories(tempDir.resolve("job"));
        this.registry = OperationRegistry.fromProviders(List.of(new TestGroovyOperations.Provider()));
        this.environment = new DefinitionEnvironment(
                registry, jobDir, tempDir, messages);
    }

    JobDefinition loadResource(String resourceName) throws IOException {
        return loadResource(resourceName, resourceName);
    }

    JobDefinition loadResource(String resourceName, String targetName) throws IOException {
        Path target = jobDir.resolve(targetName);
        try (InputStream input = getClass().getResourceAsStream("/jobs/" + resourceName)) {
            if (input == null) {
                throw new IOException("missing job resource /jobs/" + resourceName);
            }
            Files.copy(input, target);
        }
        return new GroovyJobLoader().load(target, environment);
    }

    JobDefinition loadSource(String fileName, String source) throws IOException {
        Path target = jobDir.resolve(fileName);
        Files.writeString(target, source, StandardCharsets.UTF_8);
        return new GroovyJobLoader().load(target, environment);
    }

    JobOutcome run(JobDefinition definition, Map<String, String> parameters) {
        ResolvedParameters resolved = new ParameterResolver(tempDir).resolve(
                definition,
                new ParameterSources(Map.of(), Map.of(), parameters),
                ParameterMode.RUN);
        return run(definition, resolved, Optional.empty());
    }

    JobOutcome run(JobDefinition definition, Map<String, String> parameters,
                   ch.so.agi.gretl.runtime.runner.RunControl control) {
        ResolvedParameters resolved = new ParameterResolver(tempDir).resolve(
                definition,
                new ParameterSources(Map.of(), Map.of(), parameters),
                ParameterMode.RUN);
        return run(definition, resolved, Optional.of(control));
    }

    private JobOutcome run(JobDefinition definition, ResolvedParameters parameters,
                           Optional<ch.so.agi.gretl.runtime.runner.RunControl> control) {
        ExecutionPlan plan = new ExecutionPlanner().plan(definition, List.of());
        RunInput.Builder input = RunInput.builder()
                .parameters(parameters)
                .jobRoot(jobDir)
                .startCwd(tempDir)
                .workRoot(tempDir.resolve("work"))
                .jobName("groovy-harness")
                .runId(UUID.randomUUID())
                .clock(Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC))
                .monotonicClock(new HarnessClock())
                .eventSink(events);
        control.ifPresent(input::runControl);
        return new SequentialJobRunner().run(definition, plan, input.build());
    }

    static final class RecordingMessages implements UserMessageSink {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void println(String message) {
            messages.add(message);
        }

        List<String> messages() {
            return List.copyOf(messages);
        }
    }

    static final class RecordingEvents implements RunEventSink {
        private final List<RunEvent> events = new ArrayList<>();

        @Override
        public void emit(RunEvent event) {
            events.add(event);
        }

        List<RunEvent> events() {
            return List.copyOf(events);
        }

        long count(String name) {
            return events.stream().filter(event -> event.eventName().equals(name)).count();
        }

        RunEvent last(String name) {
            return events.stream().filter(event -> event.eventName().equals(name))
                    .reduce((first, second) -> second)
                    .orElseThrow();
        }

        List<RunEvent> named(String name) {
            return events.stream().filter(event -> event.eventName().equals(name)).toList();
        }
    }

    static final class HarnessClock implements ch.so.agi.gretl.runtime.runner.MonotonicClock {
        private long ticks;

        @Override
        public long ticks() {
            return ticks;
        }

        void advanceMillis(long millis) {
            ticks += millis * 1_000_000L;
        }
    }
}
