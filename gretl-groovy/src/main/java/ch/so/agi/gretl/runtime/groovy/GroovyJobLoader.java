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
            var snapshot=ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources.single(script,environment.locale());
            if(snapshot!=null)return loadBundle(snapshot,environment,List.of(),Map.of());
            JobDslModel model = new JobDslModel(environment,
                    new JobDefinitionBuilder(environment.jobRoot(), environment.locale()), script);
            var configuration = CompilerSettings.create();
            GroovyClassLoader classLoader = new GroovyClassLoader(
                    GroovyJobLoader.class.getClassLoader(), configuration);
            Binding binding = newScriptBinding(model, environment);
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
        try {
            var snapshot=ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources.project(project,environment.locale());
            if(snapshot!=null)return loadBundle(snapshot,new DefinitionEnvironment(environment.registry(),project.root(),environment.startCwd(),environment.userMessages(),environment.locale()),project.defaultTargets(),project.defaultTargetSources());
        }catch(IOException failure){throw new GroovyDslException("JOB_LOAD_FAILED",failure.getMessage(),failure);}
        return loadProjectScripts(project, new DefinitionEnvironment(environment.registry(), project.root(),
                environment.startCwd(), environment.userMessages(), environment.locale()));
    }

    private JobDefinition loadProjectScripts(GretlProject project, DefinitionEnvironment environment) {
        var builder = new JobDefinitionBuilder(environment.jobRoot(), environment.locale());
        var index = new ProjectTaskIndex(environment.locale());
        var configuration = CompilerSettings.create();
        var loader = new GroovyClassLoader(GroovyJobLoader.class.getClassLoader(), configuration);
        loader.addClasspath(environment.jobRoot().toString());
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
        builder.sourceFiles(sourceMap);
        List<JobDslModel> models = new ArrayList<>();
        for (var entry : project.entries()) {
            String virtual = ProjectSourceNames.virtualGroovyName(entry.projectPath());
            JobDslModel model = new JobDslModel(environment, builder, entry.scriptFile(), entry.projectPath(), index,
                    virtual, sourceMap);
            models.add(model);
            try {
                Binding binding = newScriptBinding(model, environment);
                var shell = new GroovyShell(loader, binding, configuration);
                Script script = shell.parse(Files.readString(entry.scriptFile(),
                        java.nio.charset.StandardCharsets.UTF_8), virtual);
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
                                new SourceLocation(environment.jobRoot().resolve("settings.gretl").toString(), 0, 0)), null);
            }
        }
        builder.defaults(project.defaultTargets().toArray(String[]::new));
        JobDefinition definition = builder.freeze();
        models.forEach(model -> model.attachDefinition(definition));
        return definition;
    }

    /** Consume exactly these captured bytes; no filesystem source lookup during registration. */
    public JobDefinition loadBundle(ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources.Snapshot snapshot,
                                    DefinitionEnvironment environment,List<String> defaults,Map<String,SourceLocation> defaultSources){
        var bundle=snapshot.bundle();var config=CompilerSettings.create();
        var compilerLoader=new GroovyClassLoader(GroovyJobLoader.class.getClassLoader(),config);
        compilerLoader.addClasspath(snapshot.root().toString());
        var unit=new org.codehaus.groovy.control.CompilationUnit(config,null,compilerLoader);
        Map<String,String> names=new LinkedHashMap<>();
        for(var source:bundle.sources().values())if(!source.sourceId().equals(bundle.settingsId())) {
            String name=bundle.virtualName(source.sourceId());names.put(name,source.sourceId());unit.addSource(name,source.text());
        }
        ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleCompilation.prepare(unit,names.keySet());
        try {unit.compile(org.codehaus.groovy.control.Phases.CLASS_GENERATION);}
        catch(org.codehaus.groovy.control.CompilationFailedException failure){
            SourceLocation position=new SourceLocation(snapshot.root().resolve(bundle.entries().getFirst().sourceId()).toString(),0,0);
            if(unit.getErrorCollector().getErrors()!=null)for(Object item:unit.getErrorCollector().getErrors())
                if(item instanceof org.codehaus.groovy.control.messages.SyntaxErrorMessage syntax){
                    String id=names.getOrDefault(syntax.getCause().getSourceLocator(),bundle.entries().getFirst().sourceId());
                    position=new SourceLocation(snapshot.root().resolve(id).toString(),Math.max(0,syntax.getCause().getStartLine()),Math.max(0,syntax.getCause().getStartColumn()));break;
                }
            String message=failure.getMessage();for(var e:names.entrySet())message=message.replace(e.getKey(),e.getValue());
            throw new GroovyDslException("JOB_LOAD_FAILED",message,null,null,position,List.of(),failure);
        }
        Map<String,byte[]> bytes=new LinkedHashMap<>();for(var compiled:unit.getClasses())bytes.put(compiled.getName(),compiled.getBytes());
        ClassLoader loader=new ClassLoader(compilerLoader){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!bytes.containsKey(name))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){
                    Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);if(resolve)resolveClass(type);return type;
                }
            }
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                byte[] data=bytes.get(name);if(data==null)throw new ClassNotFoundException(name);return defineClass(name,data,0,data.length);
            }
        };
        Map<String,Class<? extends GretlScript>> scripts=new LinkedHashMap<>();
        try {
            for(var module:unit.getAST().getModules())for(var type:module.getClasses())if(type.isScript()) {
                String id=names.get(module.getContext().getName());
                if(id==null)throw new GroovyDslException("APPLY_CONTEXT","Unexpected script source: "+module.getContext().getName());
                Class<?> compiled=Class.forName(type.getName(),false,loader);
                if(!GretlScript.class.isAssignableFrom(compiled))throw new GroovyDslException("INVALID_SCRIPT_BASE",Messages.get(environment.locale(),"error.groovy.invalidScriptBase"));
                scripts.put(id,compiled.asSubclass(GretlScript.class));
            }
        }catch(ClassNotFoundException failure){throw new GroovyDslException("JOB_LOAD_FAILED",failure.getMessage(),failure);}
        if(scripts.size()!=names.size())throw new GroovyDslException("INVALID_SCRIPT_BASE",Messages.get(environment.locale(),"error.groovy.invalidScriptBase"));
        var metadata=ch.so.agi.gretl.runtime.groovy.fragment.CompiledScriptBundle.from(bundle,
                scripts.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().getName())), defaults);
        return new FragmentLoadSession(metadata,snapshot.root(),environment,scripts).load(defaultSources);
    }

    /** Bytecode-only path: validated metadata and classes, without source reads or compilation. */
    public JobDefinition loadCompiledBundle(ch.so.agi.gretl.runtime.groovy.fragment.CompiledScriptBundle bundle,
            DefinitionEnvironment environment,ClassLoader loader) {
        Map<String,Class<? extends GretlScript>> scripts=new LinkedHashMap<>();
        try {
            // Validate every class before invoking any constructor or static initializer.
            for(var source:bundle.sources()) {
                Class<?> type=Class.forName(source.className(),false,loader);
                if(!GretlScript.class.isAssignableFrom(type))throw new GroovyDslException("INVALID_SCRIPT_BASE",Messages.get(environment.locale(),"error.groovy.invalidScriptBase"));
                if(java.lang.reflect.Modifier.isAbstract(type.getModifiers())||!java.lang.reflect.Modifier.isPublic(type.getModifiers()))
                    throw new GroovyDslException("INVALID_SCRIPT_BASE",Messages.get(environment.locale(),"error.groovy.invalidScriptBase"));
                try{type.getConstructor(Binding.class);}catch(NoSuchMethodException missing){type.getConstructor();}
                scripts.put(source.sourceId(),type.asSubclass(GretlScript.class));
            }
        }catch(ReflectiveOperationException failure){throw new GroovyDslException("JOB_LOAD_FAILED",failure.getMessage(),failure);}
        return new FragmentLoadSession(bundle,environment.jobRoot(),environment,scripts).load(Map.of());
    }

    private static Binding newScriptBinding(JobDslModel model, DefinitionEnvironment environment) {
        Binding binding = new Binding();
        binding.setVariable(JobDslModel.BINDING_NAME, model);
        for (OperationType<?, ?> operation : environment.registry().all()) {
            binding.setVariable(operation.symbol(), operation);
        }
        return binding;
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
