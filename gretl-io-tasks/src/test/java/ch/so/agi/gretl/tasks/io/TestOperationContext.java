package ch.so.agi.gretl.tasks.io;

import ch.so.agi.gretl.runtime.api.CancellationToken;
import ch.so.agi.gretl.runtime.api.CancelReason;
import ch.so.agi.gretl.runtime.api.Diagnostic;
import ch.so.agi.gretl.runtime.api.ExecutionIdentity;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.Progress;
import ch.so.agi.gretl.runtime.api.ResourceScope;
import ch.so.agi.gretl.runtime.api.SecretValue;
import ch.so.agi.gretl.runtime.api.TaskCancelledException;
import ch.so.agi.gretl.runtime.api.TaskLogger;
import ch.so.agi.gretl.runtime.api.TimeBudget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public final class TestOperationContext implements OperationContext {
    private final Path root;
    private final Map<String, Object> parameters;
    private final Map<String, Map<String, Object>> config;
    private final Map<String, String> secrets;
    private final List<String> sensitive = new ArrayList<>();
    private final List<Progress> progress = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final ResourceScope resources = new ResourceScope() {
        @Override
        public <T extends AutoCloseable> T manage(T resource) {
            return resource;
        }
    };
    private boolean cancelled;

    public TestOperationContext(Path root, Map<String, Object> parameters,
                         Map<String, Map<String, Object>> config,
                         Map<String, String> secrets) {
        this.root = root;
        this.parameters = parameters;
        this.config = config;
        this.secrets = secrets;
    }

    @Override
    public ExecutionIdentity identity() {
        return new ExecutionIdentity(UUID.randomUUID(), "test-job",
                Optional.of("task"), Optional.of("i000001"));
    }

    @Override
    public Object parameter(String name) {
        if (!parameters.containsKey(name)) {
            throw new IllegalArgumentException("unknown parameter " + name);
        }
        return parameters.get(name);
    }

    @Override
    public <T> T parameter(String name, Class<T> type) {
        Object value = parameter(name);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("parameter type mismatch");
        }
        return type.cast(value);
    }

    @Override
    public Map<String, Object> config(String alias) {
        Map<String, Object> value = config.get(alias);
        if (value == null) {
            throw new IllegalArgumentException("unknown config " + alias);
        }
        return value;
    }

    @Override
    public Path file(String path) {
        return root.resolve(path).normalize();
    }

    @Override
    public Path path(String path) {
        return Path.of(path).toAbsolutePath().normalize();
    }

    @Override
    public Path workPath(String relativePath) throws IOException {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new IOException("escape");
        }
        Files.createDirectories(path.getParent());
        return path;
    }

    @Override
    public TaskLogger log() {
        return new TaskLogger() {
            @Override
            public boolean isEnabled(LogLevel level) {
                return true;
            }

            @Override
            public void log(LogLevel level, String eventName, String message,
                            Map<String, String> fields, Throwable cause) {
                logs.add(eventName + ": " + message);
            }
        };
    }

    @Override
    public void report(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    @Override
    public SecretValue secret(String alias) {
        return SecretValue.deferred(() -> {
            String value = secrets.get(alias);
            if (value == null) {
                throw new IllegalArgumentException("unknown secret " + alias);
            }
            return value;
        });
    }

    @Override
    public String sensitive(String value) {
        sensitive.add(value);
        return value;
    }

    @Override
    public CancellationToken cancellation() {
        return new CancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return cancelled;
            }

            @Override
            public Optional<CancelReason> reason() {
                return cancelled ? Optional.of(CancelReason.REQUESTED) : Optional.empty();
            }

            @Override
            public void throwIfCancellationRequested() throws TaskCancelledException {
                if (cancelled) {
                    throw new TaskCancelledException(CancelReason.REQUESTED);
                }
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
        return resources;
    }

    @Override
    public void progress(Progress update) {
        progress.add(update);
    }

    public List<String> sensitive() { return sensitive; }
    public List<Progress> progress() { return progress; }
    public List<String> logs() { return logs; }
    public List<Diagnostic> diagnostics() { return diagnostics; }
}
