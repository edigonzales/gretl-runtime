package ch.so.agi.gretl.tooling.semantic;

import ch.so.agi.gretl.language.*;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.syntax.EffectiveOptions;
import ch.so.agi.gretl.tooling.workspace.WorkspaceManager.WorkspaceCapture;
import java.net.URI;
import java.util.*;
import static ch.so.agi.gretl.tooling.semantic.SemanticModel.*;

/** Data-only binding and conservative validation. All IO ended when the capture was acquired. */
public final class ProjectAnalyzer {
    private static final int MAX_SYMBOLS=2000,MAX_REFERENCES=16000,MAX_DIAGNOSTICS=4000;
    public Analysis analyze(WorkspaceCapture capture,CancellationToken cancellation){return new Pass(capture,cancellation).run();}
    private static final class Pass {
        final WorkspaceCapture capture;final CancellationToken cancellation;final String language;
        final Map<SymbolId,List<Symbol>> symbols=new LinkedHashMap<>();final List<Reference> references=new ArrayList<>();final List<PathReference> paths=new ArrayList<>();final List<Diagnostic> diagnostics=new ArrayList<>();final List<PlanSemantics.Edge> edges=new ArrayList<>();final List<String> defaults=new ArrayList<>();
        final Map<Declaration,EffectiveOptions> optionCache=new IdentityHashMap<>();
        final Map<URI,Map<String,List<Symbol>>> aliases=new LinkedHashMap<>();final List<Symbol> declarations=new ArrayList<>();
        final List<OperationDescriptor> operations;final boolean catalogComplete;boolean declarationsComplete,referencesComplete,graphComplete,bounded=true;
        Pass(WorkspaceCapture capture,CancellationToken cancellation){this.capture=Objects.requireNonNull(capture);this.cancellation=Objects.requireNonNull(cancellation);language=capture.configuration().language();operations=capture.catalog().result().catalog().map(c->c.operations()).orElse(List.of());catalogComplete=capture.catalog().result().state().name().equals("AVAILABLE");declarationsComplete=capture.project().complete();referencesComplete=declarationsComplete;}
        Analysis run(){
            for(var p:capture.project().problems())diagnostic(p.code(),p.error()?Severity.ERROR:Severity.INFORMATION,new Anchor(p.documentId(),p.span()),p.related().stream().map(s->new Anchor(p.documentId(),s)).toList());
            if(!catalogComplete)diagnostic("CATALOG_UNAVAILABLE",Severity.WARNING,global(),List.of());
            for(var job:capture.project().jobs()){
                cancellation.check();var doc=capture.project().documents().get(job.documentId());declarationsComplete&=doc.coverage().declarations()==Completeness.COMPLETE;referencesComplete&=doc.coverage().references()==Completeness.COMPLETE;
                for(var problem:doc.problems())diagnostic(problem.code(),Severity.ERROR,new Anchor(job.documentId(),problem.span()),List.of());
                for(var declaration:doc.declarations()){
                    if(unknownHeader(declaration)){declarationsComplete=false;referencesComplete=false;}
                    if(declarations.size()>=MAX_SYMBOLS){limit();break;}cancellation.check();
                    if(declaration.kind()==DeclarationKind.DEFAULTS||declaration.kind()==DeclarationKind.INCLUDE)continue;
                    if(declaration.kind()==DeclarationKind.PARAMETER&&declaration.options().isEmpty()){diagnostic("TOPLEVEL_PARAMETER_READ",Severity.ERROR,new Anchor(job.documentId(),declaration.header()),List.of());continue;}
                    if(declaration.name()==null){declarationsComplete=false;continue;}
                    var namespace=switch(declaration.kind()){case PARAMETER->Namespace.PARAMETER;case CLEANUP->Namespace.CLEANUP;default->Namespace.TASK;};
                    String name=qualify(job.scope(),declaration.name(),namespace,true);if(name==null){diagnostic("INVALID_NAME",Severity.ERROR,new Anchor(job.documentId(),declaration.header()),List.of());continue;}
                    var anchor=new Anchor(job.documentId(),declaration.nameLiteral()==null?declaration.header():declaration.nameLiteral().content());
                    var symbol=new Symbol(new SymbolId(namespace,name),declaration.name(),job.scope().canonical(),anchor,declaration,operation(declaration.operation(),new Anchor(job.documentId(),declaration.header())));
                    declarations.add(symbol);symbols.computeIfAbsent(symbol.id(),id->new ArrayList<>()).add(symbol);
                    if(declaration.bindingName()!=null&&namespace==Namespace.TASK)aliases.computeIfAbsent(job.documentId(),id->new LinkedHashMap<>()).computeIfAbsent(declaration.bindingName(),id->new ArrayList<>()).add(symbol);
                }
            }
            for(var values:symbols.values())if(values.size()>1){String code=switch(values.getFirst().id().namespace()){case TASK->"DUPLICATE_TASK_ID";case PARAMETER->"DUPLICATE_PARAMETER";case CLEANUP->"DUPLICATE_CLEANUP_NAME";};for(var value:values)diagnostic(code,Severity.ERROR,value.anchor(),values.stream().filter(s->s!=value).map(Symbol::anchor).toList());}
            graphComplete=declarationsComplete&&symbols.values().stream().noneMatch(v->v.size()!=1);
            for(var symbol:declarations){cancellation.check();validateOptions(symbol);if(symbol.id().namespace()==Namespace.PARAMETER)validateParameter(symbol);else if(symbol.id().namespace()==Namespace.TASK){links(symbol,"dependsOn",ReferenceKind.DEPENDS_ON,PlanSemantics.EdgeKind.DEPENDS_ON);links(symbol,"after",ReferenceKind.AFTER,PlanSemantics.EdgeKind.AFTER);} }
            collectDefaults();
            var nodes=new ArrayList<PlanSemantics.Node>();for(var symbol:declarations)if(symbol.id().namespace()==Namespace.TASK&&unique(symbol.id()).isPresent())nodes.add(new PlanSemantics.Node(symbol.id().qualifiedName(),nodes.size()));
            var graph=new PlanSemantics.DeclaredGraph(nodes,edges);
            if(graphComplete){for(var problem:new PlanSemantics().validateDeclaredDependencies(graph))graphProblem(problem);if(!defaults.isEmpty())try{new PlanSemantics().plan(graph,List.of(),defaults);}catch(PlanSemantics.GraphException problem){if(problem.code().equals("PLAN_CYCLE"))graphProblem(problem.problem());}}
            for(var symbol:declarations){cancellation.check();validateReferences(symbol,graph);if(symbol.id().namespace()==Namespace.TASK&&symbol.declaration().kind()==DeclarationKind.TASK)validateFields(symbol.operation(),symbol.declaration().fields(),symbol.declaration().coverage(),symbol.anchor(),conditional(symbol));for(var inner:symbol.declaration().invocations())if(effective(symbol.declaration()).active(inner.span()))validateFields(operation(inner.operation(),new Anchor(symbol.anchor().documentId(),inner.span())),inner.fields(),inner.coverage(),new Anchor(symbol.anchor().documentId(),inner.span()),conditionalBody(symbol,inner.span()));SqlTemplateAnalyzer.validate(capture,symbol,(code,severity,anchor)->diagnostic(code,severity,anchor,List.of()),cancellation,conditional(symbol));}
            if(!declarationsComplete||!referencesComplete)diagnostic("PARTIAL_ANALYSIS",Severity.INFORMATION,global(),List.of());
            return new Analysis(capture,symbols,references,paths,diagnostics,graph,defaults,new Capabilities(declarationsComplete,referencesComplete,graphComplete,catalogComplete,bounded));
        }
        void collectDefaults(){
            if(capture.project().projectMode()){
                for(var target:capture.project().defaultTargets()){var value=new LiteralValue(target.canonical(),target.literal().token(),target.literal());var ref=resolve(null,Namespace.TASK,value,ReferenceKind.DEFAULT,new Anchor(capture.project().settingsId(),value.span()),ProjectNameRules.root());ref.target().ifPresent(id->addDefault(id.qualifiedName(),ref.anchor()));}
            }else for(var job:capture.project().jobs())for(var declaration:capture.project().documents().get(job.documentId()).declarations())if(declaration.kind()==DeclarationKind.DEFAULTS)for(var target:declaration.targets()){
                var ref=resolve(null,Namespace.TASK,target,ReferenceKind.DEFAULT,new Anchor(job.documentId(),target.span()),job.scope());ref.target().ifPresent(id->addDefault(id.qualifiedName(),ref.anchor()));
            }
        }
        void addDefault(String name,Anchor anchor){if(defaults.contains(name))diagnostic("DUPLICATE_DEFAULT_TARGET",Severity.ERROR,anchor,List.of());else defaults.add(name);}
        void links(Symbol owner,String option,ReferenceKind kind,PlanSemantics.EdgeKind edgeKind){
            var effective=effective(owner.declaration());if(!effective.complete()){graphComplete=false;referencesComplete=false;return;}
            for(var entry:effective.entries().values())if(option.equals(text(entry.key()))){if(entry.value() instanceof LiteralValue l&&l.value()==null)continue;var items=entry.value() instanceof ListValue list?list.items():List.of(entry.value());var seen=new HashSet<SymbolId>();for(var item:items){var ref=resolve(owner,Namespace.TASK,item,kind,new Anchor(owner.anchor().documentId(),item.span()),scope(owner));if(ref.resolution()!=Resolution.RESOLVED){graphComplete=false;continue;}var target=ref.target().orElseThrow();if(!seen.add(target))diagnostic("DUPLICATE_TASK_LINK",Severity.ERROR,ref.anchor(),List.of());else if(unique(owner.id()).isPresent())edges.add(new PlanSemantics.Edge(target.qualifiedName(),owner.id().qualifiedName(),edgeKind));}}
        }
        Reference resolve(Symbol owner,Namespace namespace,ValueExpression value,ReferenceKind kind,Anchor anchor,ProjectNameRules scope){
            if(references.size()>=MAX_REFERENCES){limit();return new Reference(kind,anchor,owner==null?null:owner.id(),null,Resolution.DYNAMIC,List.of());}
            if(value instanceof LiteralValue l&&l.literal()!=null)anchor=new Anchor(anchor.documentId(),l.literal().content());
            var error=conditionalBody(owner,value.span())?Severity.WARNING:Severity.ERROR;
            String spelling=text(value);List<Symbol> found=List.of();boolean dynamic=false;
            if(spelling!=null){String qualified=qualify(scope,spelling,namespace,false);if(qualified==null){diagnostic("INVALID_NAME",error,anchor,List.of());}else found=symbols.getOrDefault(new SymbolId(namespace,qualified),List.of());}
            else if(value instanceof NameValue name&&namespace==Namespace.TASK){
                spelling=name.name();boolean shadowed=!declarationsComplete||owner!=null&&owner.declaration().locals().stream().anyMatch(l->l.name().equals(name.name())&&l.span().startUtf16()<value.span().startUtf16());found=shadowed?List.of():aliases.getOrDefault(anchor.documentId(),Map.of()).getOrDefault(name.name(),List.of());dynamic=shadowed||found.isEmpty();
            }else dynamic=true;
            var resolution=dynamic?Resolution.DYNAMIC:found.size()>1?Resolution.AMBIGUOUS:found.isEmpty()?Resolution.UNRESOLVED:Resolution.RESOLVED;
            var ref=new Reference(kind,anchor,owner==null?null:owner.id(),spelling,resolution,found.stream().map(Symbol::id).distinct().toList());references.add(ref);
            if(resolution==Resolution.DYNAMIC){referencesComplete=false;diagnostic("DYNAMIC_REFERENCE",Severity.INFORMATION,anchor,List.of());}
            else if(resolution==Resolution.AMBIGUOUS)diagnostic("AMBIGUOUS_REFERENCE",error,anchor,found.stream().map(Symbol::anchor).toList());
            else if(resolution==Resolution.UNRESOLVED&&declarationsComplete)diagnostic(namespace==Namespace.PARAMETER?"UNKNOWN_PARAMETER":"UNKNOWN_TASK_REFERENCE",error,anchor,List.of());
            return ref;
        }
        void validateReferences(Symbol owner,PlanSemantics.DeclaredGraph graph){
            if(owner.id().namespace()==Namespace.CLEANUP)for(var inner:owner.declaration().invocations())if(effective(owner.declaration()).active(inner.span()))diagnostic("INNER_INVOCATION_SCOPE",Severity.ERROR,new Anchor(owner.anchor().documentId(),inner.span()),List.of());
            for(var call:owner.declaration().references()){
                cancellation.check();if(call.arguments().isEmpty()||!effective(owner.declaration()).active(call.span()))continue;var anchor=new Anchor(owner.anchor().documentId(),call.arguments().getFirst().span());var error=conditionalBody(owner,call.span())?Severity.WARNING:Severity.ERROR;
                if(Set.of("file","workPath","path").contains(call.name())){
                    java.net.URI resource=null;PathKind kind=call.name().equals("workPath")?PathKind.WORK:PathKind.DYNAMIC;
                    if(call.name().equals("file")&&text(call.arguments().getFirst())!=null)try{resource=ch.so.agi.gretl.tooling.workspace.SourceUris.jobResource(owner.anchor().documentId(),text(call.arguments().getFirst()));if(resource!=null)kind=PathKind.JOB_RESOURCE;}catch(IllegalArgumentException invalid){diagnostic("PATH_INVALID",error,anchor,List.of());}
                    paths.add(new PathReference(new Anchor(owner.anchor().documentId(),call.span()),kind,resource,null));
                }else if(call.name().equals("result")||call.name().equals("resultOptional")){
                    var ref=resolve(owner,Namespace.TASK,call.arguments().getFirst(),call.name().equals("result")?ReferenceKind.RESULT:ReferenceKind.RESULT_OPTIONAL,anchor,scope(owner));
                    boolean allowed=owner.id().namespace()==Namespace.TASK&&(owner.declaration().body()!=null&&contains(owner.declaration().body(),call.span())||inWhen(owner,call.span()));
                    if(!allowed)diagnostic("RESULT_SCOPE",error,new Anchor(anchor.documentId(),call.span()),List.of());
                    else if(graphComplete&&ref.target().isPresent()&&!new PlanSemantics().dataPredecessors(graph,owner.id().qualifiedName()).contains(ref.target().get().qualifiedName()))diagnostic("RESULT_DEPENDENCY_REQUIRED",error,anchor,List.of());
                    if(call.name().equals("result")&&text(call.arguments().getFirst())!=null&&ref.target().isPresent())for(var property:owner.declaration().properties())if(property.receiver() instanceof CallValue receiver&&receiver.span().equals(call.span()))unique(ref.target().get()).ifPresent(producer->{if(producer.operation()!=null&&!Set.of("class","metaClass").contains(property.property())&&producer.operation().outputs().stream().noneMatch(o->o.name().equals(property.property())))diagnostic("UNKNOWN_OUTPUT",error,new Anchor(anchor.documentId(),property.propertySpan()),List.of(producer.anchor()));});
                }else if(call.name().equals("parameter")){
                    if(!(owner.declaration().body()!=null&&contains(owner.declaration().body(),call.span()))&&!inWhen(owner,call.span()))diagnostic("TOPLEVEL_PARAMETER_READ",error,new Anchor(anchor.documentId(),call.span()),List.of());
                    var ref=resolve(owner,Namespace.PARAMETER,call.arguments().getFirst(),ReferenceKind.PARAMETER,anchor,scope(owner));
                    if(ref.target().isPresent())unique(ref.target().get()).filter(p->parameterType(p).equals("path")).ifPresent(p->paths.add(new PathReference(new Anchor(owner.anchor().documentId(),call.span()),PathKind.EXTERNAL_PARAMETER,null,p.id())));
                    if(ref.target().isPresent()&&call.arguments().size()>1&&call.arguments().get(1) instanceof NameValue requested)unique(ref.target().get()).ifPresent(parameter->{String type=parameterType(parameter);String simple=requested.name().substring(requested.name().lastIndexOf('.')+1);boolean known=Set.of("String","CharSequence","Boolean","boolean","Long","long","Integer","int","Number","Path","Object").contains(simple);boolean compatible=!known||switch(type){case "text"->Set.of("String","CharSequence","Object").contains(simple);case "boolean"->Set.of("Boolean","Object").contains(simple);case "integer"->Set.of("Long","Number","Object").contains(simple);case "path"->Set.of("Path","Object").contains(simple);default->true;};if(!compatible)diagnostic("PARAMETER_READ_TYPE",error,new Anchor(anchor.documentId(),call.arguments().get(1).span()),List.of(parameter.anchor()));});
                }
            }
        }
        boolean inWhen(Symbol owner,TextSpan span){return effective(owner.declaration()).complete()&&effective(owner.declaration()).entries().values().stream().anyMatch(e->"when".equals(text(e.key()))&&e.value() instanceof OpaqueExpression opaque&&opaque.reason().equals("CLOSURE")&&contains(e.value().span(),span));}
        void validateOptions(Symbol symbol){var allowed=symbol.id().namespace()==Namespace.PARAMETER?DslVocabulary.parameterOptions():symbol.id().namespace()==Namespace.TASK?DslVocabulary.taskOptions():Set.<String>of();for(var option:symbol.declaration().options())if(text(option.key())!=null&&!allowed.contains(text(option.key())))diagnostic("UNKNOWN_OPTION",Severity.ERROR,new Anchor(symbol.anchor().documentId(),option.key().span()),List.of());}
        void validateParameter(Symbol parameter){
            if(!effective(parameter.declaration()).complete()){referencesComplete=false;return;}
            var options=options(parameter.declaration());ValueExpression description=options.get("description");if(description==null||description instanceof LiteralValue&&(text(description)==null||text(description).isBlank()))diagnostic("PARAMETER_DESCRIPTION",Severity.ERROR,parameter.anchor(),List.of());
            ValueExpression rawType=options.get("type");if(rawType instanceof LiteralValue l&&l.value()!=null&&!(l.value() instanceof String)||rawType instanceof ListValue||rawType instanceof MapValue)diagnostic("PARAMETER_TYPE",Severity.ERROR,parameter.anchor(),List.of());
            ValueExpression required=options.get("required");if(required instanceof LiteralValue l&&l.value()!=null&&!(l.value() instanceof Boolean)||required instanceof ListValue||required instanceof MapValue)diagnostic("INPUT_TYPE",Severity.ERROR,parameter.anchor(),List.of());
            String type=parameterType(parameter);if(!type.equals("dynamic")&&!DslVocabulary.parameterTypes().contains(type))diagnostic("PARAMETER_TYPE",Severity.ERROR,parameter.anchor(),List.of());
            ValueExpression value=options.get("defaultValue");if(Boolean.TRUE.equals(literal(options.get("required")))&&(value instanceof LiteralValue l&&l.value()!=null||value instanceof ListValue||value instanceof MapValue))diagnostic("PARAMETER_REQUIRED_DEFAULT",Severity.ERROR,parameter.anchor(),List.of());
            if(value instanceof ListValue||value instanceof MapValue)diagnostic("PARAMETER_DEFAULT",Severity.ERROR,new Anchor(parameter.anchor().documentId(),value.span()),List.of());
            if(value instanceof LiteralValue l&&l.value()!=null){Object v=l.value();boolean valid=switch(type){case "text","path"->v instanceof String;case "boolean"->v instanceof Boolean||v instanceof String s&&Set.of("true","false").contains(s);case "integer"->v instanceof Byte||v instanceof Short||v instanceof Integer||v instanceof Long||v instanceof String s&&s.matches("[+-]?[0-9]+");default->true;};if(!valid)diagnostic("PARAMETER_DEFAULT",Severity.ERROR,new Anchor(parameter.anchor().documentId(),value.span()),List.of());}
        }
        String parameterType(Symbol parameter){if(!effective(parameter.declaration()).complete())return "dynamic";var type=options(parameter.declaration()).get("type");return type==null||type instanceof LiteralValue l&&l.value()==null?"text":text(type)==null?"dynamic":text(type).isBlank()?"text":text(type).toLowerCase(Locale.ROOT);}
        boolean conditionalBody(Symbol symbol,TextSpan span){return symbol!=null&&symbol.declaration().body()!=null&&contains(symbol.declaration().body(),span)&&conditional(symbol);}
        boolean conditional(Symbol symbol){var options=effective(symbol.declaration());var when=options.entries().get("when");return !options.complete()||when!=null&&!(when.value() instanceof LiteralValue l&&l.value()==null);}
        void validateFields(OperationDescriptor operation,List<FieldAssignment> fields,Coverage coverage,Anchor anchor,boolean conditional){
            var severity=conditional||!catalogComplete?Severity.WARNING:Severity.ERROR;
            for(var field:fields)if(DslVocabulary.reservedConfigurationFields().contains(field.name()))diagnostic("RESERVED_CONFIGURATION_FIELD",conditional?Severity.WARNING:Severity.ERROR,new Anchor(anchor.documentId(),field.nameSpan()),List.of());
            if(operation==null)return;var inputs=new LinkedHashMap<String,InputDescriptor>();operation.inputs().forEach(i->inputs.put(i.name(),i));var assigned=new HashSet<String>();
            for(var field:fields){cancellation.check();assigned.add(field.name());var descriptor=inputs.get(field.name());var location=new Anchor(anchor.documentId(),field.nameSpan());if(descriptor==null){if(DslVocabulary.reservedConfigurationFields().contains(field.name()))continue;diagnostic("UNKNOWN_INPUT",severity,location,List.of());continue;}
                if(field.value() instanceof LiteralValue l&&l.value()==null&&descriptor.required())diagnostic("REQUIRED_INPUT",Severity.WARNING,new Anchor(anchor.documentId(),field.value().span()),List.of(),"REQUIRED_INPUT_NULL");
                if(field.value() instanceof LiteralValue l&&l.value()!=null){if(!compatible(descriptor.shape(),l.value()))diagnostic("INPUT_TYPE",Severity.WARNING,new Anchor(anchor.documentId(),field.value().span()),List.of(),"INPUT_CONVERSION");else if(!descriptor.allowedValues().isEmpty()&&l.value() instanceof String s&&descriptor.allowedValues().stream().noneMatch(v->v.equalsIgnoreCase(s)))diagnostic("INPUT_ENUM",severity,new Anchor(anchor.documentId(),field.value().span()),List.of());}
            }
            if(coverage.fieldAssignments()==Completeness.COMPLETE)for(var input:operation.inputs())if(input.required()&&!input.hasDefault()&&!assigned.contains(input.name()))diagnostic("REQUIRED_INPUT",severity,anchor,List.of(),conditional?"REQUIRED_INPUT_CONDITIONAL":"REQUIRED_INPUT");
        }
        OperationDescriptor operation(ValueExpression expression,Anchor anchor){
            String name=expression instanceof NameValue n?n.name():null;if(name==null)return null;
            var matches=operations.stream().filter(o->o.symbol().equals(name)||o.operationId().equals(name)).toList();if(matches.size()==1)return matches.getFirst();
            if(matches.isEmpty()&&catalogComplete)diagnostic("UNKNOWN_OPERATION",Severity.WARNING,expression==null?anchor:new Anchor(anchor.documentId(),expression.span()),List.of());return null;
        }
        String qualify(ProjectNameRules scope,String name,Namespace namespace,boolean declaration){try{
            if(capture.project().projectMode())return declaration?scope.qualifyLocal(name):scope.resolveReference(name);
            if(name==null||name.isBlank())return null;if(namespace==Namespace.PARAMETER&&!name.matches("[A-Za-z][A-Za-z0-9_.-]*")&&!name.matches(":(?:[A-Za-z][A-Za-z0-9_-]*)(?::[A-Za-z][A-Za-z0-9_-]*)*"))return null;return name;
        }catch(IllegalArgumentException invalid){return null;}}
        Optional<Symbol> unique(SymbolId id){var values=symbols.getOrDefault(id,List.of());return values.size()==1?Optional.of(values.getFirst()):Optional.empty();}
        ProjectNameRules scope(Symbol symbol){return ProjectNameRules.parseQualified(symbol.scope());}
        EffectiveOptions effective(Declaration declaration){return optionCache.computeIfAbsent(declaration,EffectiveOptions::of);}
        Anchor global(){return new Anchor(capture.project().id().anchor(),null);}
        void graphProblem(PlanSemantics.GraphProblem problem){var primary=unique(new SymbolId(Namespace.TASK,problem.taskId())).map(Symbol::anchor).orElse(global());diagnostic(problem.code(),Severity.ERROR,primary,problem.edges().stream().map(e->unique(new SymbolId(Namespace.TASK,e.fromId())).map(Symbol::anchor).orElse(global())).distinct().toList());}
        void limit(){if(bounded){bounded=false;declarationsComplete=false;referencesComplete=false;graphComplete=false;diagnostic("SEMANTIC_LIMIT",Severity.INFORMATION,global(),List.of());}}
        void diagnostic(String code,Severity severity,Anchor anchor,List<Anchor> related){diagnostic(code,severity,anchor,related,code);}
        void diagnostic(String code,Severity severity,Anchor anchor,List<Anchor> related,String messageKey){if(diagnostics.size()<MAX_DIAGNOSTICS)diagnostics.add(new Diagnostic(code,severity,DiagnosticMessages.message(messageKey,language),anchor,related));else{bounded=false;referencesComplete=false;diagnostics.set(MAX_DIAGNOSTICS-1,new Diagnostic("SEMANTIC_LIMIT",Severity.INFORMATION,DiagnosticMessages.message("SEMANTIC_LIMIT",language),global(),List.of()));}}
    }
    static Map<String,ValueExpression> options(Declaration declaration){var result=new LinkedHashMap<String,ValueExpression>();for(var e:EffectiveOptions.of(declaration).entries().values())if(text(e.key())!=null)result.put(text(e.key()),e.value());return result;}
    private static boolean unknownHeader(Declaration declaration){
        if(unknownExpression(declaration.operation()))return true;
        for(var option:declaration.options())if(unknownExpression(option.key())||unknownExpression(option.value()))return true;
        return declaration.targets().stream().anyMatch(ProjectAnalyzer::unknownExpression);
    }
    private static boolean unknownExpression(ValueExpression value){
        if(value instanceof OpaqueExpression opaque)return !opaque.reason().equals("CLOSURE");
        if(value instanceof ListValue list)return list.items().stream().anyMatch(ProjectAnalyzer::unknownExpression);
        if(value instanceof MapValue map)return map.entries().stream().anyMatch(e->unknownExpression(e.key())||unknownExpression(e.value()));
        if(value instanceof CallValue call)return call.arguments().stream().anyMatch(ProjectAnalyzer::unknownExpression);
        return value instanceof PropertyValue;
    }
    static String text(ValueExpression value){return value instanceof LiteralValue l&&l.value() instanceof String s?s:null;}
    static Object literal(ValueExpression value){return value instanceof LiteralValue l?l.value():null;}
    static boolean contains(TextSpan parent,TextSpan child){return parent.startUtf16()<=child.startUtf16()&&parent.endUtf16()>=child.endUtf16();}
    private static boolean integer(String value){try{Long.parseLong(value);return value.matches("[+-]?[0-9]+");}catch(NumberFormatException invalid){return false;}}
    private static boolean compatible(ValueShape shape,Object value){return switch(shape.kind()){case "Text","Path"->value instanceof String;case "Boolean"->value instanceof Boolean||value instanceof String s&&Set.of("true","false").contains(s);case "Integer"->value instanceof Number||value instanceof String s&&integer(s);case "Decimal"->value instanceof Number||value instanceof String s&&decimal(s);case "ListOf","MapOf"->false;default->true;};}
    private static boolean decimal(String value){try{new java.math.BigDecimal(value);return true;}catch(NumberFormatException invalid){return false;}}
}
