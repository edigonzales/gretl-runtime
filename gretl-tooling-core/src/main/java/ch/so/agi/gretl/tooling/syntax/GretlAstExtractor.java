package ch.so.agi.gretl.tooling.syntax;

import java.util.*;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.syntax.Types;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Structural extraction from Groovy's converted AST, with explicit receiver/scope checks. */
final class GretlAstExtractor {
    record Extraction(List<Declaration> declarations,List<OpaqueStatement> opaque,Coverage coverage) {
        Extraction { declarations=List.copyOf(declarations);opaque=List.copyOf(opaque); }
    }
    private static final Set<String> DSL=Set.of("task","action","parameter","defaults","cleanup","include");
    private static final Set<String> HELPERS=Set.of("parameter","result","resultOptional","file","path","workPath","config","secret","sensitive");
    private final SourceText source;
    private final TokenIndex tokens;
    private final ParserCoordinateMapper coordinates;
    private final CancellationToken cancellation;
    private final int sourceOffset;
    private final Set<String> shadowed=new HashSet<>();
    private final Set<String> taskBindings=new HashSet<>();
    GretlAstExtractor(SourceText source,TokenIndex tokens,CancellationToken cancellation) {
        this(source,tokens,cancellation,source,0);
    }
    GretlAstExtractor(SourceText source,TokenIndex tokens,CancellationToken cancellation,SourceText parserSource,int sourceOffset) {
        this.source=source;this.tokens=tokens;this.cancellation=cancellation;coordinates=new ParserCoordinateMapper(parserSource);this.sourceOffset=sourceOffset;
    }
    Extraction extract(ModuleNode module) {
        var declarations=new ArrayList<Declaration>();var opaque=new ArrayList<OpaqueStatement>();
        shadowed.addAll(module.getStaticImports().keySet());
        if(!module.getStaticStarImports().isEmpty()){shadowed.addAll(DSL);shadowed.addAll(HELPERS);}
        module.getImports().forEach(i->{if(i.getAlias()!=null&&(dsl(i.getAlias())||helper(i.getAlias())))shadowed.add(i.getAlias());});
        module.getMethods().forEach(m->{shadowed.add(m.getName());opaque.add(new OpaqueStatement(span(m),"METHOD_DECLARATION",true));});
        for(var statement:module.getStatementBlock().getStatements())if(statement instanceof ExpressionStatement es&&es.getExpression() instanceof DeclarationExpression d)
            {
                collectNames(d.getLeftExpression(),shadowed);
                if(d.getLeftExpression() instanceof VariableExpression v&&d.getRightExpression() instanceof MethodCallExpression c&&c.getMethodAsString()!=null&&Set.of("task","action").contains(c.getMethodAsString())&&implicit(c))taskBindings.add(v.getName());
            }
        for(var type:module.getClasses()) {
            if(!type.isScript())opaque.add(new OpaqueStatement(span(type),"CLASS_DECLARATION",true));
            for(var annotation:type.getAnnotations())opaque.add(new OpaqueStatement(span(annotation),"ANNOTATION",true));
        }
        for(var statement:module.getStatementBlock().getStatements()) {
            cancellation.check();if(statement==ReturnStatement.RETURN_NULL_OR_VOID||statement instanceof EmptyStatement)continue;
            Expression expression=statement instanceof ExpressionStatement es?es.getExpression():null;
            String binding=null;TextSpan bindingSpan=null;
            if(expression instanceof DeclarationExpression d&&d.getLeftExpression() instanceof VariableExpression v){binding=v.getName();bindingSpan=span(v);expression=d.getRightExpression();}
            if(expression instanceof MethodCallExpression call&&dsl(call.getMethodAsString())&&implicit(call)&&!shadowed.contains(call.getMethodAsString())) {
                declarations.add(extractDeclaration(call,binding,bindingSpan,span(statement)));
            } else {
                boolean pure=pureContainer(expression);
                opaque.add(new OpaqueStatement(span(statement),shadowedCall(expression)?"SHADOWED_DSL":pure?"LOCAL_VALUE":"OPAQUE_CODE",!pure));
            }
        }
        boolean partial=opaque.stream().anyMatch(OpaqueStatement::mayRegisterDeclarations)||declarations.stream().anyMatch(d->d.name()==null&&d.kind()!=DeclarationKind.DEFAULTS&&d.kind()!=DeclarationKind.INCLUDE);
        boolean refsPartial=partial||declarations.stream().anyMatch(d->d.coverage().references()!=Completeness.COMPLETE);
        return new Extraction(declarations,opaque,new Coverage(partial?Completeness.PARTIAL:Completeness.COMPLETE,refsPartial?Completeness.PARTIAL:Completeness.COMPLETE,
                declarations.stream().anyMatch(d->d.coverage().fieldAssignments()!=Completeness.COMPLETE)?Completeness.PARTIAL:Completeness.COMPLETE,Completeness.COMPLETE));
    }
    private boolean pureContainer(Expression expression){
        if(expression instanceof ConstantExpression||expression instanceof VariableExpression)return true;
        if(expression instanceof ListExpression list)return list.getExpressions().stream().allMatch(this::pureContainer);
        if(expression instanceof MapExpression map)return map.getMapEntryExpressions().stream().allMatch(e->pureContainer(e.getKeyExpression())&&pureContainer(e.getValueExpression()));
        return false;
    }
    private boolean shadowedCall(Expression e){return e instanceof MethodCallExpression c&&dsl(c.getMethodAsString())&&shadowed.contains(c.getMethodAsString());}
    private Declaration extractDeclaration(MethodCallExpression call,String binding,TextSpan bindingSpan,TextSpan statementSpan) {
        var kind=DeclarationKind.valueOf(call.getMethodAsString().toUpperCase(Locale.ROOT));
        List<Expression> args=arguments(call);var options=new ArrayList<MapEntry>();var positional=new ArrayList<Expression>();ClosureExpression closure=null;
        for(var arg:args){if(arg instanceof MapExpression map)for(var entry:map.getMapEntryExpressions())options.add(mapEntry(entry));else if(arg instanceof ClosureExpression c)closure=c;else positional.add(arg);}
        ValueExpression nameValue=positional.isEmpty()?null:value(positional.getFirst());
        String name=nameValue instanceof LiteralValue l&&l.value() instanceof String s?s:null;
        StringLiteralSpan literal=nameValue instanceof LiteralValue l?l.literal():null;
        var targets=new ArrayList<ValueExpression>();
        if(kind==DeclarationKind.DEFAULTS||kind==DeclarationKind.INCLUDE){positional.forEach(p->targets.add(value(p)));name=null;literal=null;}
        ValueExpression operation=kind==DeclarationKind.TASK&&positional.size()>1?operationValue(positional.get(1),Set.of()):null;
        var body=closure==null?null:span(closure);var full=span(call);var header=new TextSpan(full.startUtf16(),body==null?full.endUtf16():body.startUtf16());
        BodyInfo info=closure==null?BodyInfo.empty():body(closure,kind==DeclarationKind.TASK);
        var refs=new ArrayList<>(info.references());var properties=new ArrayList<>(info.properties());
        var guards=new ArrayList<>(info.guardedExecution());var invocations=new ArrayList<>(info.invocations());var locals=new ArrayList<>(info.locals());
        boolean unknownReferences=info.unknownReferences();
        for(var arg:args)if(arg instanceof MapExpression map)for(var entry:map.getMapEntryExpressions()){
            Expression v=entry.getValueExpression();
            if(v instanceof ClosureExpression c){
                var parsedOption=body(c,false);refs.addAll(parsedOption.references());properties.addAll(parsedOption.properties());
                invocations.addAll(parsedOption.invocations());locals.addAll(parsedOption.locals());unknownReferences|=parsedOption.unknownReferences();guards.addAll(parsedOption.guardedExecution());
            }else{
                var parsedOption=references(new ExpressionStatement(v),Set.of(),Set.of());
                refs.addAll(parsedOption.references());properties.addAll(parsedOption.properties());invocations.addAll(parsedOption.invocations());
                unknownReferences|=parsedOption.unknownReferences();guards.addAll(parsedOption.guardedExecution());
            }
        }
        for(var argument:positional){
            var parsedArgument=references(new ExpressionStatement(argument),Set.of(),Set.of());
            refs.addAll(parsedArgument.references());properties.addAll(parsedArgument.properties());invocations.addAll(parsedArgument.invocations());
            unknownReferences|=parsedArgument.unknownReferences();guards.addAll(parsedArgument.guardedExecution());
        }
        invocations.sort(Comparator.comparingInt(i->i.span().startUtf16()));locals.sort(Comparator.comparingInt(l->l.span().startUtf16()));
        refs.sort(Comparator.comparingInt(r->r.span().startUtf16()));properties.sort(Comparator.comparingInt(p->p.span().startUtf16()));
        boolean staticName=name!=null||kind==DeclarationKind.DEFAULTS||kind==DeclarationKind.INCLUDE;
        var coverage=new Coverage(staticName?Completeness.COMPLETE:Completeness.PARTIAL,
                unknownReferences?Completeness.PARTIAL:Completeness.COMPLETE,info.dynamicFields()?Completeness.PARTIAL:Completeness.COMPLETE,Completeness.COMPLETE);
        return new Declaration(kind,name,literal,binding,bindingSpan,operation,options,targets,statementSpan,full,header,body,info.fields(),locals,invocations,refs,properties,coverage,false).withGuardedExecution(guards);
    }
    private record BodyInfo(List<FieldAssignment> fields,List<LocalVariable> locals,List<InnerInvocation> invocations,List<CallValue> references,List<PropertyValue> properties,boolean dynamicFields,boolean unknownReferences,List<TextSpan> guardedExecution) {
        static BodyInfo empty(){return new BodyInfo(List.of(),List.of(),List.of(),List.of(),List.of(),false,false,List.of());}
    }
    private BodyInfo body(ClosureExpression closure,boolean configuration) {return body(closure,configuration,shadowed);}
    private BodyInfo body(ClosureExpression closure,boolean configuration,Set<String> inherited) {
        var fields=new ArrayList<FieldAssignment>();var locals=new ArrayList<LocalVariable>();
        var localNames=new HashSet<String>(inherited);boolean dynamicFields=false;
        var statements=closure.getCode() instanceof BlockStatement block?block.getStatements():List.of(closure.getCode());
        Set<String> aliases=new HashSet<>(Set.of("ctx"));
        if(!closure.isParameterSpecified())aliases.add("it");
        if(closure.getParameters()!=null&&closure.getParameters().length==1)aliases.add(closure.getParameters()[0].getName());
        for(var statement:statements)if(statement instanceof ExpressionStatement es&&es.getExpression() instanceof DeclarationExpression d)collectNames(d.getLeftExpression(),localNames);
        var declaredNames=new HashSet<String>(localNames);declaredNames.removeAll(inherited);
        var rootParams=new HashSet<String>();if(closure.getParameters()!=null)for(var parameter:closure.getParameters())rootParams.add(parameter.getName());
        if(!closure.isParameterSpecified())rootParams.add("it");
        var receiverHidden=new HashSet<String>(inherited);receiverHidden.removeAll(rootParams);receiverHidden.addAll(declaredNames);
        localNames.addAll(rootParams);var allowedAliases=new HashSet<String>(aliases);allowedAliases.removeAll(receiverHidden);
        for(var statement:statements){
            cancellation.check();Expression e=statement instanceof ExpressionStatement es?es.getExpression():null;
            if(e instanceof DeclarationExpression d&&d.getLeftExpression() instanceof VariableExpression v){locals.add(new LocalVariable(v.getName(),span(d)));localNames.add(v.getName());}
            else if(configuration&&e instanceof BinaryExpression b&&b.getOperation().getType()==Types.ASSIGN&&b.getLeftExpression() instanceof VariableExpression v&&!localNames.contains(v.getName())){
                fields.add(new FieldAssignment(v.getName(),span(v),value(b.getRightExpression(),allowedAliases,localNames),span(b)));
            } else if(configuration&&!(statement instanceof EmptyStatement))dynamicFields=true;
        }
        var references=references(closure.getCode(),localNames,allowedAliases);
        return new BodyInfo(List.copyOf(fields),List.copyOf(locals),references.invocations(),references.references(),references.properties(),dynamicFields||(configuration&&references.unknownReferences()),references.unknownReferences(),references.guardedExecution());
    }
    private record ReferenceInfo(List<InnerInvocation> invocations,List<CallValue> references,List<PropertyValue> properties,boolean unknownReferences,List<TextSpan> guardedExecution) { }
    /** One conservative reference walk for declaration bodies and every option value. */
    private ReferenceInfo references(Statement code,Set<String> names,Set<String> aliases){
        var localNames=new HashSet<>(names);var allowedAliases=new HashSet<>(aliases);
        var invocations=new ArrayList<InnerInvocation>();var refs=new ArrayList<CallValue>();var properties=new ArrayList<PropertyValue>();var guards=new ArrayList<TextSpan>();boolean[] unknown={false};
        code.visit(new CodeVisitorSupport(){
            final Deque<Set<String>> hidden=new ArrayDeque<>();
            final Set<String> declaredHere=new HashSet<>();
            final Map<Statement,Boolean> exits=new IdentityHashMap<>();
            private boolean mayExit(Statement node){
                Boolean cached=exits.get(node);if(cached!=null)return cached;boolean result;
                if(node instanceof ReturnStatement||node instanceof ThrowStatement||node instanceof BreakStatement||node instanceof ContinueStatement||node instanceof AssertStatement)result=true;
                else if(node instanceof BlockStatement block)result=block.getStatements().stream().anyMatch(this::mayExit);
                else if(node instanceof IfStatement branch)result=mayExit(branch.getIfBlock())||mayExit(branch.getElseBlock());
                else if(node instanceof ForStatement loop)result=mayExit(loop.getLoopBlock());
                else if(node instanceof WhileStatement loop)result=mayExit(loop.getLoopBlock());
                else if(node instanceof DoWhileStatement loop)result=mayExit(loop.getLoopBlock());
                else if(node instanceof TryCatchStatement attempt)result=mayExit(attempt.getTryStatement())||mayExit(attempt.getFinallyStatement())||attempt.getCatchStatements().stream().anyMatch(c->mayExit(c.getCode()));
                else if(node instanceof SwitchStatement choice)result=mayExit(choice.getDefaultStatement())||choice.getCaseStatements().stream().anyMatch(c->mayExit(c.getCode()));
                else if(node instanceof SynchronizedStatement monitor)result=mayExit(monitor.getCode());
                else result=false;exits.put(node,result);return result;
            }
            private void guard(ASTNode node){if(node!=null&&node.getLineNumber()>0)guards.add(span(node));}
            private ValueExpression argument(Expression expression){
                if(expression instanceof VariableExpression variable&&(declaredHere.contains(variable.getName())||receiversNow().contains(variable.getName())||hidden.stream().anyMatch(names->names.contains(variable.getName()))))return new OpaqueExpression(span(expression),"SCOPED_REFERENCE");
                return value(expression,receiversNow(),hiddenNow());
            }
            @Override public void visitIfElse(IfStatement node){guard(node.getIfBlock());guard(node.getElseBlock());super.visitIfElse(node);}
            @Override public void visitWhileLoop(WhileStatement node){guard(node.getLoopBlock());super.visitWhileLoop(node);}
            @Override public void visitDoWhileLoop(DoWhileStatement node){guard(node.getLoopBlock());super.visitDoWhileLoop(node);}
            @Override public void visitForLoop(ForStatement node){
                guard(node.getLoopBlock());if(node.getVariable()==ForStatement.FOR_LOOP_DUMMY)guard(node.getCollectionExpression());node.getCollectionExpression().visit(this);hidden.push(Set.of(node.getVariable().getName()));node.getLoopBlock().visit(this);hidden.pop();
            }
            @Override public void visitCaseStatement(CaseStatement node){guard(node.getExpression());guard(node.getCode());super.visitCaseStatement(node);}
            @Override public void visitSwitch(SwitchStatement node){guard(node.getDefaultStatement());super.visitSwitch(node);}
            @Override public void visitCatchStatement(CatchStatement node){guard(node.getCode());hidden.push(Set.of(node.getVariable().getName()));super.visitCatchStatement(node);hidden.pop();}
            @Override public void visitTernaryExpression(TernaryExpression node){guard(node.getTrueExpression());guard(node.getFalseExpression());super.visitTernaryExpression(node);}
            @Override public void visitBinaryExpression(BinaryExpression node){if(node.getOperation().getType()==Types.LOGICAL_AND||node.getOperation().getType()==Types.LOGICAL_OR)guard(node.getRightExpression());super.visitBinaryExpression(node);}
            @Override public void visitBlockStatement(BlockStatement node){
                boolean exited=false;for(var statement:node.getStatements()){if(exited)guard(statement);statement.visit(this);if(mayExit(statement))exited=true;}
            }
            private Set<String> hiddenNow(){var result=new HashSet<>(localNames);hidden.forEach(result::addAll);return result;}
            private Set<String> receiversNow(){var result=new HashSet<>(allowedAliases);hidden.forEach(result::removeAll);return result;}
            @Override public void visitDeclarationExpression(DeclarationExpression expression){
                if(expression.getLeftExpression() instanceof VariableExpression v){localNames.add(v.getName());declaredHere.add(v.getName());allowedAliases.remove(v.getName());}super.visitDeclarationExpression(expression);
            }
            @Override public void visitClosureExpression(ClosureExpression nested){
                guard(nested);var names=new HashSet<String>();if(nested.getParameters()!=null)for(var p:nested.getParameters())names.add(p.getName());
                if(!nested.isParameterSpecified())names.add("it");hidden.push(names);super.visitClosureExpression(nested);hidden.pop();
            }
            @Override public void visitConstructorCallExpression(ConstructorCallExpression expression){unknown[0]=true;super.visitConstructorCallExpression(expression);}
            @Override public void visitPropertyExpression(PropertyExpression property){
                var parsed=value(property,receiversNow(),hiddenNow());
                if(parsed instanceof PropertyValue p&&resultReceiver(p.receiver()))properties.add(p);
                super.visitPropertyExpression(property);
            }
            @Override public void visitMethodCallExpression(MethodCallExpression call){
                cancellation.check();Set<String> hiddenNames=hiddenNow();String method=call.getMethodAsString();String receiver=receiver(call);
                boolean trusted=(implicit(call)&&!shadowed.contains(method)&&!hiddenNames.contains(method))||(receiver!=null&&receiversNow().contains(receiver));
                if(helper(method)&&trusted){refs.add(new CallValue(method,receiver,arguments(call).stream().map(this::argument).toList(),span(call)));if(Set.of("parameter","result","resultOptional").contains(method)&&(arguments(call).isEmpty()||!(arguments(call).getFirst() instanceof ConstantExpression)&&!(Set.of("result","resultOptional").contains(method)&&arguments(call).getFirst() instanceof VariableExpression v&&taskBindings.contains(v.getName()))))unknown[0]=true;}
                if("run".equals(method)&&receiver!=null&&receiversNow().contains(receiver)){
                    List<Expression> args=arguments(call);ClosureExpression inner=args.stream().filter(ClosureExpression.class::isInstance).map(ClosureExpression.class::cast).findFirst().orElse(null);
                    if(inner!=null&&!args.isEmpty()) {var parsed=body(inner,true,hiddenNames);invocations.add(new InnerInvocation(operationValue(args.getFirst(),hiddenNames),span(call),span(inner),parsed.fields(),
                            new Coverage(Completeness.COMPLETE,parsed.unknownReferences()?Completeness.PARTIAL:Completeness.COMPLETE,parsed.dynamicFields()?Completeness.PARTIAL:Completeness.COMPLETE,Completeness.COMPLETE)));refs.addAll(parsed.references());properties.addAll(parsed.properties());invocations.addAll(parsed.invocations());if(parsed.unknownReferences())unknown[0]=true;guards.addAll(parsed.guardedExecution());for(var argument:args)if(!(argument instanceof ClosureExpression))argument.visit(this);return;}
                }
                if((dsl(method)||helper(method))&&!trusted)unknown[0]=true;
                if(!helper(method)&&!("run".equals(method)&&trusted)&&!(method!=null&&Set.of("println","print","finishJob","log","report","progress","cancellation","budget","resources").contains(method)))unknown[0]=true;
                super.visitMethodCallExpression(call);
            }
        });
        return new ReferenceInfo(List.copyOf(invocations),List.copyOf(refs),List.copyOf(properties),unknown[0],ExecutionRegions.normalize(guards));
    }
    private ValueExpression operationValue(Expression expression,Set<String> hidden){
        var parsed=value(expression);return parsed instanceof NameValue n&&(shadowed.contains(n.name())||hidden.contains(n.name()))?new OpaqueExpression(parsed.span(),"SHADOWED_OPERATION"):parsed;
    }
    ValueExpression value(Expression expression) { return value(expression,Set.of(),shadowed); }
    private ValueExpression value(Expression expression,Set<String> aliases,Set<String> hidden) {
        cancellation.check();TextSpan span=span(expression);
        if(expression instanceof ConstantExpression c){
            Object v=c.getValue();StringLiteralSpan literal=null;
            if(v instanceof String)try{var decoded=LiteralDecoder.decode(source,span);if(decoded.value().equals(v))literal=decoded.span();}catch(IllegalArgumentException ignored){/* unquoted map keys have no literal envelope */}
            if(v==null||v instanceof String||v instanceof Number||v instanceof Boolean||v instanceof Character)return new LiteralValue(v,span,literal);
        }
        if(expression instanceof UnaryMinusExpression negative){
            var inner=value(negative.getExpression(),aliases,hidden);
            if(inner instanceof LiteralValue l&&l.value() instanceof Number n){
                Object result;
                if(n instanceof java.math.BigDecimal d)result=d.negate();
                else if(n instanceof java.math.BigInteger i)result=i.negate();
                else if(n instanceof Double)result=-n.doubleValue();
                else if(n instanceof Float)result=-n.floatValue();
                else if(n instanceof Long)result=-n.longValue();
                else result=-n.intValue();
                return new LiteralValue(result,span,null);
            }
        }
        if(expression instanceof UnaryPlusExpression positive){var inner=value(positive.getExpression(),aliases,hidden);if(inner instanceof LiteralValue l&&l.value() instanceof Number)return new LiteralValue(l.value(),span,null);}
        if(expression instanceof VariableExpression v)return new NameValue(v.getName(),span);
        if(expression instanceof ClassExpression c)return new NameValue(c.getType().getName(),span);
        if(expression instanceof ListExpression list)return new ListValue(list.getExpressions().stream().map(e->value(e,aliases,hidden)).toList(),span);
        if(expression instanceof MapExpression map)return new MapValue(map.getMapEntryExpressions().stream().map(e->new MapEntry(value(e.getKeyExpression(),aliases,hidden),value(e.getValueExpression(),aliases,hidden),span(e))).toList(),span);
        if(expression instanceof PropertyExpression p&&p.getPropertyAsString()!=null)return new PropertyValue(value(p.getObjectExpression(),aliases,hidden),p.getPropertyAsString(),span(p.getProperty()),span);
        if(expression instanceof MethodCallExpression call&&helper(call.getMethodAsString())&&((implicit(call)&&!hidden.contains(call.getMethodAsString())&&!shadowed.contains(call.getMethodAsString()))||(receiver(call)!=null&&aliases.contains(receiver(call)))))
            return new CallValue(call.getMethodAsString(),receiver(call),arguments(call).stream().map(a->value(a,aliases,hidden)).toList(),span);
        return new OpaqueExpression(span,expression instanceof ClosureExpression?"CLOSURE":expression instanceof GStringExpression?"INTERPOLATED_STRING":"GROOVY_EXPRESSION");
    }
    private static boolean resultReceiver(ValueExpression value){return value instanceof CallValue c&&(c.name().equals("result")||c.name().equals("resultOptional"))||value instanceof PropertyValue p&&resultReceiver(p.receiver());}
    private static void collectNames(Expression expression,Set<String> names){
        if(expression instanceof VariableExpression v)names.add(v.getName());
        else if(expression instanceof TupleExpression tuple)tuple.getExpressions().forEach(e->collectNames(e,names));
    }
    private MapEntry mapEntry(MapEntryExpression entry){return new MapEntry(value(entry.getKeyExpression()),value(entry.getValueExpression()),span(entry));}
    private static List<Expression> arguments(MethodCallExpression call){return call.getArguments() instanceof TupleExpression tuple?tuple.getExpressions():List.of(call.getArguments());}
    private static boolean dsl(String name){return name!=null&&DSL.contains(name);}
    private static boolean helper(String name){return name!=null&&HELPERS.contains(name);}
    private static boolean implicit(MethodCallExpression call){return !call.isSafe()&&!call.isSpreadSafe()&&(call.isImplicitThis()||call.getObjectExpression() instanceof VariableExpression v&&v.isThisExpression());}
    private static String receiver(MethodCallExpression call){return call.isImplicitThis()?null:call.getObjectExpression() instanceof VariableExpression v?v.getName():null;}
    private TextSpan span(ASTNode node){if(node.getLineNumber()<1)return new TextSpan(sourceOffset,sourceOffset);var span=coordinates.parserSpan(node.getLineNumber(),node.getColumnNumber(),node.getLastLineNumber(),node.getLastColumnNumber());return new TextSpan(span.startUtf16()+sourceOffset,span.endUtf16()+sourceOffset);}
}
