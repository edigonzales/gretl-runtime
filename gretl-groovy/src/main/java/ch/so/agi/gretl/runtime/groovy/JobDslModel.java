package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.TaskRef;
import ch.so.agi.gretl.runtime.groovy.project.ProjectPath;
import ch.so.agi.gretl.runtime.groovy.project.ProjectTaskIndex;
import ch.so.agi.gretl.runtime.runner.TaskLink;
import ch.so.agi.gretl.runtime.runner.ParameterException;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobDefinitionBuilder;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Mutable load-time model shared by GretlScript and the execution scopes. */
final class JobDslModel {
    static final String BINDING_NAME = "__gretlModel";

    private final DefinitionEnvironment environment;
    private final JobDefinitionBuilder builder;
    private final Path scriptFile;
    private final Map<String, TaskRef<?>> taskRefs = new LinkedHashMap<>();
    private final Map<String, OperationType<?, ?>> taskOperations = new LinkedHashMap<>();
    private final Map<String, OperationType<?, ?>> symbols;
    private final ProjectPath scope;
    private final ProjectTaskIndex index;
    private final String virtualSourceName;
    private final Map<String, Path> sourceFiles;
    private JobDefinition definition;

    JobDslModel(DefinitionEnvironment environment, JobDefinitionBuilder builder, Path scriptFile) {
        this(environment, builder, scriptFile, null, null, null, Map.of());
    }

    JobDslModel(DefinitionEnvironment environment, JobDefinitionBuilder builder, Path scriptFile,
                ProjectPath scope, ProjectTaskIndex index, String virtualSourceName, Map<String, Path> sourceFiles) {
        this.scope = scope;
        this.index = index;
        this.virtualSourceName = virtualSourceName;
        this.sourceFiles = Map.copyOf(sourceFiles);
        this.environment = environment;
        this.builder = builder;
        this.scriptFile = scriptFile.toAbsolutePath().normalize();
        Map<String, OperationType<?, ?>> symbolMap = new LinkedHashMap<>();
        for (OperationType<?, ?> operation : environment.registry().all()) {
            symbolMap.putIfAbsent(operation.symbol(), operation);
        }
        this.symbols = Collections.unmodifiableMap(symbolMap);
    }

    DefinitionEnvironment environment() {
        return environment;
    }

    JobDefinitionBuilder builder() {
        return builder;
    }

    Path scriptFile() {
        return scriptFile;
    }

    SourceLocation source() {
        return projectMode() ? new SourceLocation(scriptFile.toString(), 0, 0) : SourceLocations.fallback(scriptFile);
    }

    Map<String, OperationType<?, ?>> symbols() {
        return symbols;
    }

    Optional<OperationType<?, ?>> operationSymbol(String name) {
        return Optional.ofNullable(symbols.get(name));
    }

    void registerTask(String name, TaskRef<?> ref, OperationType<?, ?> operation) {
        if (projectMode()) {
            index.register(name, ref, operation, currentSource());
            return;
        }
        taskRefs.put(name, ref);
        if (operation == null) {
            taskOperations.remove(name);
        } else {
            taskOperations.put(name, operation);
        }
    }

    TaskRef<?> resolveTask(String name, String requestingTask) {
        if (projectMode()) {
            try { return index.require(qualifiedReference(name), requestingTask); }
            catch (GroovyDslException failure) {
                throw new GroovyDslException(failure.code(), failure.getMessage(), requestingTask, null,
                        currentSource(), failure.available(), failure);
            }
        }
        TaskRef<?> ref = taskRefs.get(name);
        if (ref == null) {
            throw new GroovyDslException("UNKNOWN_TASK_NAME",
                    Messages.format(environment.locale(), "error.groovy.unknownTask", name),
                    requestingTask, null,
                    SourceLocations.current(scriptFile), taskNames(), null);
        }
        return ref;
    }

    Optional<OperationType<?, ?>> taskOperation(String name) {
        return projectMode() ? index.operation(qualifiedReference(name)) : Optional.ofNullable(taskOperations.get(name));
    }

    List<String> taskNames() {
        return projectMode() ? index.taskIds() : List.copyOf(new LinkedHashSet<>(taskRefs.keySet()));
    }

    JobDefinition definition() {
        return definition;
    }

    void freeze() {
        if (definition == null) {
            if (projectMode()) throw new GroovyDslException("PROJECT_FREEZE_CONTROLLED", Messages.get(
                    environment.locale(), "project.freezeControlled"), null, null, currentSource(), List.of(), null);
            definition = builder.freeze();
        }
    }

    boolean projectMode() { return scope != null; }

    String qualifiedTaskId(String name) { return qualifyLocal(name); }
    String qualifiedParameterId(String name) {
        if (!projectMode() && name != null && name.contains(":")) {
            throw new ParameterException("INVALID_PARAMETER_DECLARATION", name,
                    "Invalid parameter name: " + name);
        }
        return qualifyLocal(name);
    }
    String qualifiedCleanupId(String name) { return qualifyLocal(name); }
    String parameterReference(String name) { return qualifiedReference(name); }

    private String qualifyLocal(String name) {
        if (!projectMode()) return name;
        try { return scope.qualifyLocal(name); }
        catch (IllegalArgumentException failure) { throw invalidName(name, failure); }
    }

    String qualifiedReference(String name) {
        if (!projectMode()) return name;
        try { return scope.resolveReference(name); }
        catch (IllegalArgumentException failure) { throw invalidName(name, failure); }
    }

    private GroovyDslException invalidName(String name, Throwable cause) {
        return new GroovyDslException("PROJECT_NAME_INVALID", Messages.format(environment.locale(),
                "project.nameInvalid", name, scope), null, null, currentSource(), List.of(), cause);
    }

    void ensureAvailable(String id) { if (projectMode()) index.ensureAvailable(id, currentSource()); }

    void registerLinks(String id, List<TaskLink> dependsOn, List<TaskLink> after) {
        if (projectMode()) index.rememberLinks(id, dependsOn, after);
    }

    String resourceRelativePath(String raw) {
        if (!projectMode() || Path.of(raw).isAbsolute()) return raw;
        return scope.relativeDirectory().resolve(raw).normalize().toString();
    }

    SourceLocation currentSource() { return SourceLocations.current(scriptFile, virtualSourceName); }
    SourceLocation sourceForTask(String id) {
        SourceLocation current = currentSource();
        return projectMode() && current.line() == 0 ? index.source(id).orElse(current) : current;
    }
    SourceLocation failureSource(Throwable failure) { return SourceLocations.from(failure, scriptFile, virtualSourceName); }
    void remap(Throwable failure) { if (projectMode()) SourceLocations.remap(failure, sourceFiles); }
    void attachDefinition(JobDefinition value) { definition = value; }

    UserMessageSink userMessages() {
        return environment.userMessages();
    }

    Set<String> availableSymbols() {
        return symbols.keySet();
    }
}
