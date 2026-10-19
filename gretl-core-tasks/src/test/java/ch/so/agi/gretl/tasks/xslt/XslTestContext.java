package ch.so.agi.gretl.tasks.xslt;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class XslTestContext implements OperationContext {
    private final Path root;
    private final ResourceScope resources = new ResourceScope() {
        @Override
        public <T extends AutoCloseable> T manage(T resource) {
            return resource;
        }
    };

    XslTestContext(Path root) {
        this.root = root;
    }

    @Override
    public ExecutionIdentity identity() {
        return new ExecutionIdentity(UUID.randomUUID(), "test-job",
                Optional.of("task"), Optional.of("i000001"));
    }

    @Override
    public Object parameter(String name) {
        throw new IllegalArgumentException("unknown parameter " + name);
    }

    @Override
    public <T> T parameter(String name, Class<T> type) {
        throw new IllegalArgumentException("unknown parameter " + name);
    }

    @Override
    public Map<String, Object> config(String alias) {
        throw new IllegalArgumentException("unknown config " + alias);
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
                return false;
            }

            @Override
            public void log(LogLevel level, String eventName, String message,
                            Map<String, String> fields, Throwable cause) {
            }
        };
    }

    @Override
    public void report(Diagnostic diagnostic) {
    }

    @Override
    public SecretValue secret(String alias) {
        throw new IllegalArgumentException("unknown secret " + alias);
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
            public void throwIfCancellationRequested() throws TaskCancelledException {
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
    public void progress(Progress progress) {
    }

    @Override
    public Locale locale() {
        return Locale.GERMAN;
    }
}
