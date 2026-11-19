package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.groovy.fragment.CompiledScriptBundle;
import ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleCompilation;
import ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;

/** Compiles captured SCRIPT sources and registers the resulting scripts. */
public final class GroovyJobLoader {
    public JobDefinition loadBundle(ScriptBundleSources.Snapshot snapshot, DefinitionEnvironment environment,
                                    List<String> defaults, Map<String, SourceLocation> defaultSources) {
        var bundle = snapshot.bundle();
        var config = CompilerSettings.create();
        var compilerLoader = new GroovyClassLoader(GroovyJobLoader.class.getClassLoader(), config);
        compilerLoader.addClasspath(snapshot.root().toString());
        // Resolve existing classes on the classpath, but compile only the snapshot.
        compilerLoader.setResourceLoader(name -> null);
        var unit = new CompilationUnit(config, null, compilerLoader);
        Map<String, String> names = new LinkedHashMap<>();
        for (var source : bundle.sources().values()) {
            if (!source.sourceId().equals(bundle.settingsId())) {
                String name = bundle.virtualName(source.sourceId());
                names.put(name, source.sourceId());
                unit.addSource(name, source.text());
            }
        }
        ScriptBundleCompilation.prepare(unit, names.keySet());
        try {
            unit.compile(Phases.CLASS_GENERATION);
        } catch (CompilationFailedException failure) {
            throw compilationFailure(unit, failure, snapshot.root(), bundle.entries().getFirst().sourceId(), names);
        }

        Map<String, byte[]> bytes = new LinkedHashMap<>();
        for (var compiled : unit.getClasses()) {
            bytes.put(compiled.getName(), compiled.getBytes());
        }
        ClassLoader loader = new ClassLoader(compilerLoader) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] data = bytes.get(name);
                if (data == null) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = defineClass(name, data, 0, data.length);
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        };

        Map<String, Class<? extends GretlScript>> scripts = new LinkedHashMap<>();
        try {
            for (var module : unit.getAST().getModules()) {
                String id = names.get(module.getContext().getName());
                for (var type : module.getClasses()) {
                    if (type.isScript()) {
                        if (id == null) {
                            throw new GroovyDslException("APPLY_CONTEXT",
                                    "Unexpected script source: " + module.getContext().getName());
                        }
                        Class<?> compiled = Class.forName(type.getName(), false, loader);
                        if (!GretlScript.class.isAssignableFrom(compiled)) {
                            throw invalidScriptBase(environment);
                        }
                        scripts.put(id, compiled.asSubclass(GretlScript.class));
                    }
                }
            }
        } catch (ClassNotFoundException failure) {
            throw new GroovyDslException("JOB_LOAD_FAILED", failure.getMessage(), failure);
        }
        if (scripts.size() != names.size()) throw invalidScriptBase(environment);
        return new FragmentLoadSession(bundle.entries(), bundle.edges(), bundle.settingsId(),
                snapshot.root(), environment, scripts).load(defaults, defaultSources);
    }

    private static GroovyDslException compilationFailure(CompilationUnit unit, CompilationFailedException failure,
                                                         Path root, String entryId, Map<String, String> names) {
        SourceLocation position = new SourceLocation(root.resolve(entryId).toString(), 0, 0);
        var errors = unit.getErrorCollector().getErrors();
        if (errors != null) {
            for (var error : errors) {
                if (error instanceof SyntaxErrorMessage syntax) {
                    var cause = syntax.getCause();
                    String id = names.getOrDefault(cause.getSourceLocator(), entryId);
                    position = new SourceLocation(root.resolve(id).toString(),
                            Math.max(0, cause.getStartLine()), Math.max(0, cause.getStartColumn()));
                    break;
                }
            }
        }
        String message = failure.getMessage();
        for (var name : names.entrySet()) {
            message = message.replace(name.getKey(), root.resolve(name.getValue()).toString());
        }
        return new GroovyDslException("JOB_LOAD_FAILED", message, null, null, position, List.of(), failure);
    }

    /** Loads the validated bundle's classes without reading or compiling sources. */
    public JobDefinition loadCompiledBundle(CompiledScriptBundle bundle, DefinitionEnvironment environment,
                                            ClassLoader loader) {
        Map<String, Class<? extends GretlScript>> scripts = new LinkedHashMap<>();
        try {
            // Validate every class before invoking any constructor or static initializer.
            for (var source : bundle.sources()) {
                Class<?> type = Class.forName(source.className(), false, loader);
                if (!GretlScript.class.isAssignableFrom(type)
                        || Modifier.isAbstract(type.getModifiers()) || !Modifier.isPublic(type.getModifiers())) {
                    throw invalidScriptBase(environment);
                }
                try {
                    type.getConstructor(Binding.class);
                } catch (NoSuchMethodException missing) {
                    type.getConstructor();
                }
                scripts.put(source.sourceId(), type.asSubclass(GretlScript.class));
            }
        } catch (ReflectiveOperationException failure) {
            throw new GroovyDslException("JOB_LOAD_FAILED", failure.getMessage(), failure);
        }
        return new FragmentLoadSession(bundle.entries(), bundle.edges(), bundle.settingsId(),
                environment.jobRoot(), environment, scripts).load(bundle.defaultTargets(), Map.of());
    }

    private static GroovyDslException invalidScriptBase(DefinitionEnvironment environment) {
        return new GroovyDslException("INVALID_SCRIPT_BASE",
                Messages.get(environment.locale(), "error.groovy.invalidScriptBase"));
    }
}
