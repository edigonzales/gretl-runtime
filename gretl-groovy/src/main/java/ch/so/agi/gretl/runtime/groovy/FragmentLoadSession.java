package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.groovy.fragment.FragmentGraphResolver.Edge;
import ch.so.agi.gretl.runtime.groovy.fragment.FragmentGraphResolver.Entry;
import ch.so.agi.gretl.runtime.groovy.project.ProjectException;
import ch.so.agi.gretl.runtime.groovy.project.ProjectPath;
import ch.so.agi.gretl.runtime.groovy.project.ProjectTaskIndex;
import ch.so.agi.gretl.runtime.runner.*;
import ch.so.agi.gretl.syntax.ScriptSourceNames;
import groovy.lang.Binding;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;

/** A single definition registration, with shared GRETL state and separate script instances/bindings. */
final class FragmentLoadSession {
    private record Context(String scope,String source,boolean fragment) { }
    private final List<Entry> entries;
    private final List<Edge> edges;
    private final String settingsId;
    private final Path root;
    private final DefinitionEnvironment environment;
    private final Map<String,Class<? extends GretlScript>> classes;
    private final JobDefinitionBuilder builder;
    private final ProjectTaskIndex index;
    private final Map<String,DefinitionScopeState> scopes=new LinkedHashMap<>();
    private final IdentityHashMap<JobDslModel,Context> contexts=new IdentityHashMap<>();
    private final Set<String> registered=new HashSet<>();
    private final Map<String,Path> sourceFiles;
    private boolean registering=true;

    FragmentLoadSession(List<Entry> entries, List<Edge> edges, String settingsId,
                        Path root, DefinitionEnvironment environment,
                        Map<String, Class<? extends GretlScript>> classes) {
        this.entries = entries;
        this.edges = edges;
        this.settingsId = settingsId;
        this.root = root;
        this.environment = environment;
        this.classes = classes;
        builder = new JobDefinitionBuilder(environment.jobRoot(), environment.locale());
        index = settingsId == null ? null : new ProjectTaskIndex(environment.locale());
        var files = new LinkedHashMap<String, Path>();
        classes.keySet().forEach(id -> files.put(ScriptSourceNames.virtualGroovyName(id), root.resolve(id)));
        sourceFiles = Map.copyOf(files);
        builder.sourceFiles(sourceFiles);
    }

    JobDefinition load(List<String> defaults, Map<String, SourceLocation> defaultSources) {
        try {
            for (var entry : entries) register(entry.scopeId(), entry.sourceId(), false);
            if (index != null) {
                index.validateReferences();
                var taskIds = index.taskIds();
                for (String target : defaults) {
                    if (!taskIds.contains(target)) {
                        var source = defaultSources.getOrDefault(target,
                                new SourceLocation(root.resolve(settingsId).toString(), 0, 0));
                        throw new ProjectException("UNKNOWN_DEFAULT_TARGET",
                                Messages.format(environment.locale(), "project.defaultUnknown", target), source, null);
                    }
                }
                builder.defaults(defaults.toArray(String[]::new));
            }
            var definition = builder.freeze();
            contexts.keySet().forEach(model -> model.attachDefinition(definition));
            return definition;
        } finally {
            registering = false;
        }
    }

    void apply(JobDslModel caller,Map<String,?> options){
        var context=contexts.get(caller);
        if(!registering || context==null)throw error(caller,"APPLY_OUTSIDE_DEFINITION");
        if(options==null || options.size()!=1 || !(options.get("from") instanceof String from))throw error(caller,"APPLY_SYNTAX");
        int line=caller.currentSource().line();
        var edge=edges.stream()
                .filter(e -> e.scopeId().equals(context.scope()) && e.sourceId().equals(context.source()))
                .filter(e->e.directive().from().equals(from) && e.directive().line()==line).findFirst()
                .orElseThrow(()->error(caller,"APPLY_OUTSIDE_DEFINITION"));
        register(context.scope(),edge.targetId(),true);
    }
    void checkDefaults(JobDslModel model){
        var context=contexts.get(model);if(context!=null && context.fragment())throw error(model,"APPLY_DEFAULTS_NOT_ALLOWED");
    }
    private GroovyDslException error(JobDslModel model,String code){return new GroovyDslException(code,
            Messages.format(environment.locale(),"apply."+code,model.scriptFile()),null,null,model.currentSource(),List.of(),null);}

    private void register(String scope, String source, boolean fragment) {
        String key = scope + "\0" + source;
        if (!registered.add(key)) {
            throw new GroovyDslException("APPLY_DUPLICATE",
                    Messages.format(environment.locale(), "apply.APPLY_DUPLICATE", source));
        }
        var state = scopes.computeIfAbsent(scope, k -> new DefinitionScopeState(builder));
        var project = index == null ? null : ProjectPath.parseQualified(scope);
        var model = new JobDslModel(environment, state, root.resolve(source), project, index,
                ScriptSourceNames.virtualGroovyName(source), sourceFiles, this);
        contexts.put(model, new Context(scope, source, fragment));
        try {
            Binding binding = new Binding();
            binding.setVariable(JobDslModel.BINDING_NAME, model);
            model.symbols().forEach(binding::setVariable);
            Class<? extends GretlScript> type = classes.get(source);
            GretlScript script;
            try {
                script = type.getConstructor(Binding.class).newInstance(binding);
            } catch (NoSuchMethodException noBinding) {
                script = type.getConstructor().newInstance();
                script.setBinding(binding);
            }
            script.run();
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) {
                model.remap(error);
                throw error;
            }
            throw failure(model, failure.getCause());
        } catch (GroovyDslException failure) {
            if (failure.source() != null && failure.source().line() > 0) throw failure;
            throw failure(model, failure);
        } catch (Error failure) {
            model.remap(failure);
            throw failure;
        } catch (Throwable failure) {
            throw failure(model, failure);
        }
    }

    private GroovyDslException failure(JobDslModel model, Throwable cause) {
        String code = "JOB_LOAD_FAILED";
        String task = null;
        String field = null;
        List<String> available = List.of();
        switch (cause) {
            case GroovyDslException diagnostic -> {
                code = diagnostic.code();
                task = diagnostic.taskId();
                field = diagnostic.fieldName();
                available = diagnostic.available();
            }
            case DefinitionException definition -> code = definition.code();
            case ParameterException parameter -> {
                code = parameter.code();
                field = parameter.parameterName();
            }
            default -> { }
        }
        var source = model.failureSource(cause);
        model.remap(cause);
        String message = Objects.toString(cause.getMessage(), cause.getClass().getName());
        for (var file : sourceFiles.entrySet()) {
            String virtualName = file.getKey();
            String scriptClass = virtualName.substring(0, virtualName.length() - ".groovy".length());
            // Match a whole generated name, including when one source ID prefixes another.
            message = message.replaceAll("\\b" + scriptClass + "(?:\\.groovy)?\\b",
                    Matcher.quoteReplacement(file.getValue().toString()));
        }
        return new GroovyDslException(code, message, task, field, source, available, cause);
    }
}
