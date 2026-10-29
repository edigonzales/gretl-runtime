package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.groovy.project.GretlProject;
import ch.so.agi.gretl.runtime.groovy.project.ProjectException;
import ch.so.agi.gretl.runtime.groovy.project.ProjectSourceNames;
import ch.so.agi.gretl.runtime.groovy.project.ProjectTaskIndex;
import ch.so.agi.gretl.runtime.runner.DefinitionException;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobDefinitionBuilder;
import ch.so.agi.gretl.runtime.runner.ParameterException;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads one Groovy job with a fresh compiler, class loader and binding. Top-level
 * code registers definitions; task closures are not evaluated here.
 */
public final class GroovyJobLoader {

    public JobDefinition loadCompiled(String entryClass, Path scriptLabel,
                                      DefinitionEnvironment environment) {
        return loadCompiled(entryClass, scriptLabel, environment,
                GroovyJobLoader.class.getClassLoader());
    }

    /**
     * Instantiates a precompiled script class. The packager uses the same
     * path; no source compilation or script execution happens at package time.
     */
    public JobDefinition loadCompiled(String entryClass, Path scriptLabel,
                                      DefinitionEnvironment environment,
                                      ClassLoader classLoader) {
        java.util.Objects.requireNonNull(entryClass, "entryClass");
        java.util.Objects.requireNonNull(scriptLabel, "scriptLabel");
        java.util.Objects.requireNonNull(environment, "environment");
        java.util.Objects.requireNonNull(classLoader, "classLoader");
        Path source = scriptLabel.toAbsolutePath().normalize();
        try {
            Class<?> type = Class.forName(entryClass, true, classLoader);
            if (!GretlScript.class.isAssignableFrom(type)) {
                throw new GroovyDslException("INVALID_SCRIPT_BASE",
                        Messages.get(environment.locale(), "error.groovy.invalidCompiledBase"),
                        null, null, SourceLocations.fallback(source), List.of(), null);
            }
            JobDslModel model = new JobDslModel(environment,
                    new JobDefinitionBuilder(environment.jobRoot()), source);
            Binding binding = new Binding();
            binding.setVariable(JobDslModel.BINDING_NAME, model);
            for (OperationType<?, ?> operation : environment.registry().all()) {
                binding.setVariable(operation.symbol(), operation);
            }
            GretlScript instance = (GretlScript) type.getDeclaredConstructor().newInstance();
            instance.setBinding(binding);
            instance.run();
            return instance.freeze();
        } catch (GroovyDslException exception) {
            throw exception;
        } catch (DefinitionException | ParameterException exception) {
            throw exception;
        } catch (Error error) {
            throw error;
        } catch (Throwable throwable) {
            throw new GroovyDslException("JOB_LOAD_FAILED",
                    Messages.format(environment.locale(), "error.groovy.compiledLoadFailed",
                            describe(throwable)),
                    null, null, SourceLocations.fallback(source), List.of(), throwable);
        }
    }

    public JobDefinition load(Path scriptFile, DefinitionEnvironment environment) {
        java.util.Objects.requireNonNull(scriptFile, "scriptFile");
        java.util.Objects.requireNonNull(environment, "environment");
        Path script = scriptFile.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(script)) {
                throw new GroovyDslException("JOB_NOT_FOUND",
                        Messages.format(environment.locale(), "error.groovy.jobNotFound", script),
                        null, null, SourceLocations.fallback(script), List.of(), null);
            }
            JobDslModel model = new JobDslModel(environment,
                    new JobDefinitionBuilder(environment.jobRoot()), script);
            var configuration = CompilerSettings.create();
            GroovyClassLoader classLoader = new GroovyClassLoader(
                    GroovyJobLoader.class.getClassLoader(), configuration);
            Binding binding = new Binding();
            binding.setVariable(JobDslModel.BINDING_NAME, model);
            for (OperationType<?, ?> operation : environment.registry().all()) {
                binding.setVariable(operation.symbol(), operation);
            }
            GroovyShell shell = new GroovyShell(classLoader, binding, configuration);
            Script scriptInstance = shell.parse(script.toFile());
            if (!(scriptInstance instanceof GretlScript gretlScript)) {
                throw new GroovyDslException("INVALID_SCRIPT_BASE",
                        Messages.get(environment.locale(), "error.groovy.invalidScriptBase"),
                        null, null, SourceLocations.fallback(script), List.of(), null);
            }
            scriptInstance.run();
            return gretlScript.freeze();
        } catch (GroovyDslException exception) {
            throw withSource(exception, script);
        } catch (DefinitionException | ParameterException exception) {
            throw exception;
        } catch (Error error) {
            throw error;
        } catch (Throwable throwable) {
            throw new GroovyDslException("JOB_LOAD_FAILED",
                    Messages.format(environment.locale(), "error.groovy.loadFailed",
                            describe(throwable)),
                    null, null, SourceLocations.from(throwable, script),
                    List.of(), throwable);
        }
    }

    /** Register all entries into one builder, followed by one final freeze. */
    public JobDefinition loadProject(GretlProject project,
                                     DefinitionEnvironment environment) {
        java.util.Objects.requireNonNull(project, "project");
        java.util.Objects.requireNonNull(environment, "environment");
        var builder = new JobDefinitionBuilder(project.root(), environment.locale());
        var index = new ProjectTaskIndex(environment.locale());
        var configuration = CompilerSettings.create();
        var loader = new GroovyClassLoader(GroovyJobLoader.class.getClassLoader(), configuration);
        loader.addClasspath(project.root().toString());
        Map<String, Path> originals = new LinkedHashMap<>();
        for (var entry : project.entries()) {
            String virtual = ProjectSourceNames.virtualGroovyName(entry.projectPath());
            if (originals.putIfAbsent(virtual, entry.scriptFile()) != null) {
                throw new ProjectException("PROJECT_DUPLICATE_SCRIPT_CLASS",
                        Messages.format(environment.locale(), "project.classDuplicate", virtual),
                        new SourceLocation(entry.scriptFile().toString(), 0, 0), null);
            }
        }
        var sourceMap = Map.copyOf(originals);
        List<JobDslModel> models = new ArrayList<>();
        for (var entry : project.entries()) {
            String virtual = ProjectSourceNames.virtualGroovyName(entry.projectPath());
            JobDslModel model = new JobDslModel(environment, builder, entry.scriptFile(), entry.projectPath(), index,
                    virtual, sourceMap);
            models.add(model);
            try {
                Binding binding = new Binding();
                binding.setVariable(JobDslModel.BINDING_NAME, model);
                for (var operation : environment.registry().all()) binding.setVariable(operation.symbol(), operation);
                GroovyShell shell = new GroovyShell(loader, binding, configuration);
                Script script = shell.parse(Files.readString(entry.scriptFile(), java.nio.charset.StandardCharsets.UTF_8), virtual);
                registerScript(script, model);
            } catch (GroovyDslException failure) {
                if (failure.source() != null && failure.source().line() > 0) throw failure;
                throw projectLoadFailure(failure.code(), failure, model, sourceMap);
            } catch (DefinitionException failure) {
                throw projectLoadFailure(failure.code(), failure, model, sourceMap);
            } catch (ParameterException failure) {
                throw projectLoadFailure(failure.code(), failure, model, sourceMap);
            } catch (Error failure) {
                model.remap(failure); throw failure;
            } catch (Throwable failure) {
                throw projectLoadFailure("JOB_LOAD_FAILED", failure, model, sourceMap);
            }
        }
        index.validateReferences();
        for (String target : project.defaultTargets()) {
            if (!index.taskIds().contains(target)) {
                throw new ProjectException("UNKNOWN_DEFAULT_TARGET",
                        Messages.format(environment.locale(), "project.defaultUnknown", target),
                        project.defaultTargetSources().getOrDefault(target,
                                new SourceLocation(project.settingsFile().toString(), 0, 0)), null);
            }
        }
        builder.defaults(project.defaultTargets().toArray(String[]::new));
        JobDefinition definition = builder.freeze();
        models.forEach(model -> model.attachDefinition(definition));
        return definition;
    }

    void registerScript(Script script, JobDslModel model) {
        if (!(script instanceof GretlScript)) {
            throw new GroovyDslException("INVALID_SCRIPT_BASE", Messages.get(model.environment().locale(),
                    "error.groovy.invalidScriptBase"), null, null, model.source(), List.of(), null);
        }
        script.run();
    }

    private static GroovyDslException projectLoadFailure(String code, Throwable failure, JobDslModel model,
                                                          Map<String, Path> originals) {
        SourceLocation source = model.failureSource(failure);
        String message = describe(failure);
        for (var entry : originals.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue().toString())
                    .replace(entry.getKey().replace(".groovy", ""), entry.getValue().toString());
        }
        model.remap(failure);
        return new GroovyDslException(code, source + " [" + code + "] " + message,
                null, null, source, List.of(), failure);
    }

    private static GroovyDslException withSource(GroovyDslException exception, Path script) {
        if (exception.source() != null && exception.source().line() > 0) {
            return exception;
        }
        return new GroovyDslException(exception.code(), exception.getMessage(),
                exception.taskId(), exception.fieldName(),
                SourceLocations.from(exception, script), exception.available(), exception);
    }

    private static String describe(Throwable throwable) {
        if (throwable instanceof IOException) {
            return throwable.getMessage();
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getName() : message;
    }
}
