package ch.so.agi.gretl.runtime.runner;

import ch.so.agi.gretl.runtime.api.CancellationToken;
import ch.so.agi.gretl.runtime.api.ContextAccessException;
import ch.so.agi.gretl.runtime.api.ContextView;
import ch.so.agi.gretl.runtime.api.Diagnostic;
import ch.so.agi.gretl.runtime.api.ExecutionIdentity;
import ch.so.agi.gretl.runtime.api.Progress;
import ch.so.agi.gretl.runtime.api.ResourceScope;
import ch.so.agi.gretl.runtime.api.SecretValue;
import ch.so.agi.gretl.runtime.api.TaskLogger;
import ch.so.agi.gretl.runtime.api.TimeBudget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Common scoped context implementation for task, operation and cleanup. */
abstract class BaseContext implements ContextView {
    private final RunEnvironment environment;
    private final ExecutionIdentity identity;
    private final Path workBase;
    private final CloseStack closeStack;
    private final TimeBudget budget;
    private final Map<String, SecretValue> secrets = new HashMap<>();
    private final TaskLogger logger;
    private ContextState state = ContextState.ACTIVE;

    BaseContext(RunEnvironment environment, ExecutionIdentity identity, Path workBase,
                CloseStack closeStack, TimeBudget budget, Optional<String> operationId,
                Map<String, String> baseAttributes) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.workBase = Objects.requireNonNull(workBase, "workBase").toAbsolutePath().normalize();
        this.closeStack = Objects.requireNonNull(closeStack, "closeStack");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.logger = environment.logger(identity, operationId, baseAttributes,
                () -> state == ContextState.CLOSED || !environment.isOpen());
    }

    RunEnvironment environment() {
        return environment;
    }

    @Override
    public ExecutionIdentity identity() {
        return identity;
    }

    @Override
    public Object parameter(String name) {
        checkDataAccess();
        if (name == null || name.isBlank()) {
            throw new ContextAccessException("UNKNOWN_PARAMETER", "Parametername ist leer");
        }
        if (!environment.parameters().declaredNames().contains(name)) {
            throw new ContextAccessException("UNKNOWN_PARAMETER",
                    "Der Job kennt den Parameter " + name + " nicht");
        }
        return environment.parameters().get(name);
    }

    @Override
    public <T> T parameter(String name, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = parameter(name);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ContextAccessException("PARAMETER_TYPE_MISMATCH",
                    "Parameter " + name + " ist nicht vom Typ " + type.getName());
        }
        return type.cast(value);
    }

    @Override
    public Map<String, Object> config(String alias) {
        checkDataAccess();
        if (alias == null || alias.isBlank()) {
            throw new ContextAccessException("UNKNOWN_CONFIG_ALIAS", "Config-Alias ist leer");
        }
        Map<String, Object> config = environment.taskConfig().get(alias);
        if (config == null) {
            throw new ContextAccessException("UNKNOWN_CONFIG_ALIAS",
                    "Unbekannter taskConfig-Alias: " + alias);
        }
        return config;
    }

    @Override
    public Path file(String path) {
        checkDataAccess();
        return resolve(environment.jobRoot(), path);
    }

    @Override
    public Path path(String path) {
        checkDataAccess();
        return resolve(environment.startCwd(), path);
    }

    @Override
    public Path workPath(String relativePath) throws IOException {
        checkWork();
        if (relativePath == null || relativePath.isBlank()) {
            throw new ContextAccessException("INVALID_PATH", "workPath ist leer");
        }
        Path relative;
        try {
            relative = Path.of(relativePath);
        } catch (InvalidPathException exception) {
            throw new ContextAccessException("INVALID_PATH",
                    "workPath ist kein gueltiger Pfad: " + relativePath);
        }
        if (relative.isAbsolute()) {
            throw new ContextAccessException("WORK_PATH_ESCAPE",
                    "workPath muss relativ sein: " + relativePath);
        }
        Path path = workBase.resolve(relative).normalize();
        if (!path.startsWith(workBase)) {
            throw new ContextAccessException("WORK_PATH_ESCAPE",
                    "workPath verlaesst den Arbeitsbereich: " + relativePath);
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return path;
    }

    @Override
    public TaskLogger log() {
        return logger;
    }

    @Override
    public void report(Diagnostic diagnostic) {
        checkDataAccess();
        environment.report(diagnostic);
    }

    public SecretValue secret(String alias) {
        checkDataAccess();
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("secret alias must not be blank");
        }
        return secrets.computeIfAbsent(alias, key -> SecretValue.deferred(() -> {
            if (state == ContextState.CLOSED || !environment.isOpen()) {
                throw new ContextAccessException("CONTEXT_CLOSED",
                        "Secret " + key + " kann nach dem Scopeende nicht mehr gelesen werden");
            }
            String value = environment.input().secretResolver().resolve(key);
            if (value == null) {
                throw new ContextAccessException("SECRET_NOT_FOUND",
                        "Secret " + key + " wurde nicht aufgeloest");
            }
            environment.secretRegistry().register(value);
            return value;
        }));
    }

    public String sensitive(String value) {
        checkDataAccess();
        return environment.secretRegistry().register(Objects.requireNonNull(value, "value"));
    }

    public CancellationToken cancellation() {
        return environment.control();
    }

    public TimeBudget budget() {
        return budget;
    }

    public ResourceScope resources() {
        return new ResourceScope() {
            @Override
            public <T extends AutoCloseable> T manage(T resource) {
                checkWork();
                return closeStack.manage(resource);
            }
        };
    }

    public void progress(Progress progress) {
        checkDataAccess();
        environment.progress(identity, Objects.requireNonNull(progress, "progress"));
    }

    ContextState state() {
        return state;
    }

    void beginClosing() {
        if (state == ContextState.ACTIVE) {
            state = ContextState.CLOSING;
        }
    }

    void markClosed() {
        state = ContextState.CLOSED;
    }

    List<Throwable> closeResources() {
        beginClosing();
        List<Throwable> failures = closeStack.closeAll();
        markClosed();
        return failures;
    }

    void checkDataAccess() {
        if (state == ContextState.CLOSED || !environment.isOpen()) {
            throw new ContextAccessException("CONTEXT_CLOSED", "Der Context ist geschlossen");
        }
    }

    void checkWork() {
        if (state != ContextState.ACTIVE || !environment.isOpen()) {
            throw new ContextAccessException("CONTEXT_CLOSED",
                    "Neue Arbeit ist nach dem Scopeende nicht erlaubt");
        }
    }

    private Path resolve(Path base, String text) {
        if (text == null || text.isBlank()) {
            throw new ContextAccessException("INVALID_PATH", "Pfadtext ist leer");
        }
        try {
            Path path = Path.of(text);
            return (path.isAbsolute() ? path : base.resolve(path)).normalize();
        } catch (InvalidPathException exception) {
            throw new ContextAccessException("INVALID_PATH",
                    "Pfad ist ungueltig: " + text);
        }
    }
}
