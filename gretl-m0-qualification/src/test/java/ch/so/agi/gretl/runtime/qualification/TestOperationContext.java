package ch.so.agi.gretl.runtime.qualification;

import ch.so.agi.gretl.runtime.api.CancelReason;
import ch.so.agi.gretl.runtime.api.CancellationToken;
import ch.so.agi.gretl.runtime.api.ContextAccessException;
import ch.so.agi.gretl.runtime.api.Diagnostic;
import ch.so.agi.gretl.runtime.api.ExecutionIdentity;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.Progress;
import ch.so.agi.gretl.runtime.api.ResourceScope;
import ch.so.agi.gretl.runtime.api.SecretValue;
import ch.so.agi.gretl.runtime.api.TaskLogger;
import ch.so.agi.gretl.runtime.api.TimeBudget;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Clearly marked M0 test fixture. It implements the public OperationContext
 * only for local probes and must never be shipped as a runtime context.
 */
public final class TestOperationContext implements OperationContext {
    private final ExecutionIdentity identity;
    private final Path workRoot;
    private final Map<String, Object> parameters;
    private final TaskLogger logger = new RecordingLogger();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final List<Progress> progress = new ArrayList<>();

    public TestOperationContext(Path workRoot, Map<String, Object> parameters) {
        this.identity = new ExecutionIdentity(
                UUID.randomUUID(), "m0-qualification", Optional.of("probe"), Optional.of("i000001"));
        this.workRoot = workRoot;
        this.parameters = Map.copyOf(parameters);
    }

    @Override
    public ExecutionIdentity identity() {
        return identity;
    }

    @Override
    public Object parameter(String name) {
        return parameters.get(name);
    }

    @Override
    public <T> T parameter(String name, Class<T> type) {
        Object value = parameter(name);
        if (value == null) return null;
        if (!type.isInstance(value)) {
            throw new ContextAccessException("PARAMETER_TYPE_MISMATCH",
                    "Parameter " + name + " ist nicht vom Typ " + type.getName());
        }
        return type.cast(value);
    }

    @Override
    public Map<String, Object> config(String alias) {
        return Map.of();
    }

    @Override
    public Path file(String path) {
        return Path.of(path).toAbsolutePath().normalize();
    }

    @Override
    public Path path(String path) {
        return Path.of(path).toAbsolutePath().normalize();
    }

    @Override
    public Path workPath(String relativePath) {
        return workRoot.resolve(relativePath).toAbsolutePath().normalize();
    }

    @Override
    public TaskLogger log() {
        return logger;
    }

    @Override
    public void report(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    @Override
    public SecretValue secret(String alias) {
        return SecretValue.deferred(() -> "fixture-secret");
    }

    @Override
    public String sensitive(String value) {
        return value;
    }

    @Override
    public CancellationToken cancellation() {
        return new CancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return false;
            }

            @Override
            public Optional<CancelReason> reason() {
                return Optional.empty();
            }

            @Override
            public void throwIfCancellationRequested() {
            }
        };
    }

    @Override
    public TimeBudget budget() {
        return new TimeBudget() {
            @Override
            public Optional<Duration> remaining() {
                return Optional.empty();
            }

            @Override
            public boolean expired() {
                return false;
            }
        };
    }

    @Override
    public ResourceScope resources() {
        return new ResourceScope() {
            @Override
            public <T extends AutoCloseable> T manage(T resource) {
                return resource;
            }
        };
    }

    @Override
    public void progress(Progress value) {
        progress.add(value);
    }

    public List<Diagnostic> diagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public List<Progress> progressUpdates() {
        return Collections.unmodifiableList(progress);
    }

    public RecordingLogger logger() {
        return (RecordingLogger) logger;
    }

    /** Captures TaskLogger calls for assertions; a test fixture only. */
    public static final class RecordingLogger implements TaskLogger {
        private final List<Entry> entries = new ArrayList<>();

        @Override
        public boolean isEnabled(LogLevel level) {
            return true;
        }

        @Override
        public void log(LogLevel level, String eventName, String message,
                        Map<String, String> fields, Throwable cause) {
            entries.add(new Entry(level, eventName, message,
                    Collections.unmodifiableMap(new LinkedHashMap<>(fields)), cause));
        }

        public List<Entry> entries() {
            return Collections.unmodifiableList(entries);
        }

        public record Entry(LogLevel level, String eventName, String message,
                            Map<String, String> fields, Throwable cause) {
        }
    }
}
