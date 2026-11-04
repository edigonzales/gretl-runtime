package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.groovy.fragment.FragmentGraphResolver;
import ch.so.agi.gretl.runtime.groovy.project.ProjectPath;
import ch.so.agi.gretl.runtime.groovy.project.ProjectTaskIndex;
import ch.so.agi.gretl.runtime.runner.*;
import groovy.lang.Binding;
import java.nio.file.Path;
import java.util.*;

/** A single definition registration, with shared GRETL state and separate script instances/bindings. */
final class FragmentLoadSession {
    private record Context(String scope,String source,boolean fragment) { }
    private final FragmentGraphResolver.Bundle bundle;
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

    FragmentLoadSession(FragmentGraphResolver.Bundle bundle,Path root,DefinitionEnvironment environment,
                        Map<String,Class<? extends GretlScript>> classes){
        this.bundle=bundle;this.root=root;this.environment=environment;this.classes=Map.copyOf(classes);
        builder=new JobDefinitionBuilder(environment.jobRoot(),environment.locale());index=new ProjectTaskIndex(environment.locale());
        var files=new LinkedHashMap<String,Path>();
        classes.keySet().forEach(id->files.put(bundle.virtualName(id),root.resolve(id)));sourceFiles=Map.copyOf(files);
        builder.sourceFiles(sourceFiles);
    }
    JobDefinition load(List<String> defaults,Map<String,SourceLocation> defaultSources){
        try {
            for(var entry:bundle.entries())register(entry.scopeId(),entry.sourceId(),false);
            if(bundle.projectMode()) {
                index.validateReferences();
                for(String target:defaults)if(!index.taskIds().contains(target))throw new ch.so.agi.gretl.runtime.groovy.project.ProjectException(
                        "UNKNOWN_DEFAULT_TARGET",Messages.format(environment.locale(),"project.defaultUnknown",target),
                        defaultSources.getOrDefault(target,new SourceLocation(root.resolve("settings.gretl").toString(),0,0)),null);
                builder.defaults(defaults.toArray(String[]::new));
            }
            var definition=builder.freeze();contexts.keySet().forEach(model->model.attachDefinition(definition));return definition;
        } finally {registering=false;}
    }
    void apply(JobDslModel caller,Map<String,?> options){
        var context=contexts.get(caller);
        if(!registering || context==null)throw error(caller,"APPLY_OUTSIDE_DEFINITION");
        if(options==null || options.size()!=1 || !(options.get("from") instanceof String from))throw error(caller,"APPLY_SYNTAX");
        int line=caller.currentSource().line();
        var edge=bundle.edgesFrom(context.scope(),context.source()).stream()
                .filter(e->e.directive().from().equals(from) && e.directive().line()==line).findFirst()
                .orElseThrow(()->error(caller,"APPLY_OUTSIDE_DEFINITION"));
        register(context.scope(),edge.targetId(),true);
    }
    void checkDefaults(JobDslModel model){
        var context=contexts.get(model);if(context!=null && context.fragment())throw error(model,"APPLY_DEFAULTS_NOT_ALLOWED");
    }
    GroovyDslException error(JobDslModel model,String code){return new GroovyDslException(code,
            Messages.format(environment.locale(),"apply."+code,model.scriptFile()),null,null,model.currentSource(),List.of(),null);}
    private void register(String scope,String source,boolean fragment){
        String key=scope+"\0"+source;
        if(!registered.add(key))throw new GroovyDslException("APPLY_DUPLICATE",Messages.format(environment.locale(),"apply.APPLY_DUPLICATE",source));
        var state=scopes.computeIfAbsent(scope,k->new DefinitionScopeState(builder));
        var model=new JobDslModel(environment,state,root.resolve(source),bundle.projectMode()?ProjectPath.parseQualified(scope):null,
                bundle.projectMode()?index:null,bundle.virtualName(source),sourceFiles);
        contexts.put(model,new Context(scope,source,fragment));model.fragments(this);
        try {
            Binding binding=new Binding();binding.setVariable(JobDslModel.BINDING_NAME,model);
            for(var operation:environment.registry().all())binding.setVariable(operation.symbol(),operation);
            GretlScript script;
            try {script=classes.get(source).getDeclaredConstructor(Binding.class).newInstance(binding);}
            catch(NoSuchMethodException noBinding){script=classes.get(source).getDeclaredConstructor().newInstance();script.setBinding(binding);}
            script.run();
        }catch(java.lang.reflect.InvocationTargetException failure){
            if(failure.getCause() instanceof Error error){model.remap(error);throw error;}
            throw failure(model,failure.getCause());
        }
        catch(GroovyDslException failure){if(failure.source()!=null && failure.source().line()>0)throw failure;throw failure(model,failure);}
        catch(Error failure){model.remap(failure);throw failure;}
        catch(Throwable failure){throw failure(model,failure);}
    }
    private GroovyDslException failure(JobDslModel model,Throwable cause){
        String code=cause instanceof GroovyDslException d?d.code():cause instanceof DefinitionException d?d.code():cause instanceof ParameterException p?p.code():"JOB_LOAD_FAILED";
        var source=model.failureSource(cause);model.remap(cause);
        return new GroovyDslException(code,source+" ["+code+"] "+Objects.toString(cause.getMessage(),cause.getClass().getName()),null,null,source,List.of(),cause);
    }
}
