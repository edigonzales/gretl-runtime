package ch.so.agi.gretl.runtime.groovy.fragment;

import ch.so.agi.gretl.runtime.groovy.CompilerSettings;
import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import ch.so.agi.gretl.runtime.groovy.SourceLocation;
import groovy.lang.GroovyClassLoader;
import groovyjarjarantlr4.v4.runtime.CharStreams;
import java.io.IOException;
import java.util.*;
import org.apache.groovy.parser.antlr4.GroovyLangLexer;
import org.apache.groovy.parser.antlr4.GroovyLexer;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.control.*;

/** Parse/convert only: no transforms, script instantiation, classpath discovery or evaluation. */
public final class ApplyDirectiveReader {
    /** Half-open offsets count UTF-16 code units; source diagnostic lines/columns are one-based. */
    public record Directive(String sourceId, String from, int start, int end,
                            int literalStart, int literalEnd, int line, int column) {
        public String siteId() { return sourceId + "@" + start; }
    }
    public record Problem(String code, String sourceId, int line, int column) { }
    public record Result(List<Directive> directives, List<Problem> problems) {
        public Result { directives = List.copyOf(directives); problems = List.copyOf(problems); }
        public List<Directive> requireValid() {
            if (!problems.isEmpty()) {
                var p = problems.getFirst();
                throw new GroovyDslException(p.code(), ch.so.agi.gretl.i18n.Messages.format(ch.so.agi.gretl.i18n.Messages.sourceLocale(), "apply." + p.code(), p.sourceId()), null, null,
                        new SourceLocation(p.sourceId(), p.line(), p.column()), List.of(), null);
            }
            return directives;
        }
    }
    public Result read(String sourceId, String text) {
        Objects.requireNonNull(sourceId); Objects.requireNonNull(text);
        var config = CompilerSettings.create(); config.setScriptBaseClass(null);
        var directives = new ArrayList<Directive>(); var problems = new ArrayList<Problem>();
        SourceUnit unit=null;
        try (var loader = new GroovyClassLoader(getClass().getClassLoader(), config)) {
            unit = new SourceUnit(sourceId, text, config, loader, new ErrorCollector(config));
            unit.parse(); unit.completePhase(); unit.nextPhase(); unit.convert();
            var module = unit.getAST();
            var tokens = new GroovyLangLexer(CharStreams.fromString(text)).getAllTokens();
            Set<MethodCallExpression> top = Collections.newSetFromMap(new IdentityHashMap<>());
            for (var statement : module.getStatementBlock().getStatements()) {
                if (statement instanceof ExpressionStatement expression && expression.getExpression() instanceof MethodCallExpression call
                        && (statement.getStatementLabels() == null || statement.getStatementLabels().isEmpty())) top.add(call);
            }
            boolean scriptOwnApply=module.getMethods().stream().anyMatch(m->m.getName().equals("apply"))
                    || module.getStatementBlock().getStatements().stream().anyMatch(statement->statement instanceof ExpressionStatement expression
                        && shadowsApply(expression.getExpression()));
            var shadow = new ArrayList<ASTNode>();
            boolean[] scriptContext = {true};
            ClassNode[] helperType = {null};
            for (var method : module.getMethods()) if (method.getName().equals("apply")) shadow.add(method);
            for(var statement:module.getStatementBlock().getStatements())
                if(statement instanceof ExpressionStatement expression && shadowsApply(expression.getExpression())) shadow.add(expression.getExpression());
            var visitor = new CodeVisitorSupport() {
                void problem(String code, ASTNode node) {
                    problems.add(new Problem(code, sourceId, Math.max(0,node.getLineNumber()), Math.max(0,node.getColumnNumber())));
                }
                @Override public void visitMethodCallExpression(MethodCallExpression call) {
                    boolean self = call.isImplicitThis() || call.getObjectExpression() instanceof VariableExpression v && v.isThisExpression();
                    boolean named=namedOptions(call);
                    boolean ordinaryOwnedCall=scriptContext[0] ? scriptOwnApply && !named
                            : ownsHelperApply(helperType[0],call,module,named);
                    if (self && !ordinaryOwnedCall && "apply".equals(call.getMethodAsString())) {
                        if (!top.contains(call) || !call.isImplicitThis() || call.isSafe() || call.isSpreadSafe() || call.isUsingGenerics()) {
                            problem("APPLY_CONTEXT",call);
                        } else {
                            var args = call.getArguments();
                            var list = args instanceof TupleExpression tuple ? tuple.getExpressions() : List.of(args);
                            if (list.size()!=1 || !(list.getFirst() instanceof MapExpression map) || map.getMapEntryExpressions().size()!=1) {
                                problem("APPLY_SYNTAX",call);
                            } else {
                                var entry = map.getMapEntryExpressions().getFirst();
                                var value = entry.getValueExpression();
                                var token = tokens.stream().filter(t -> t.getLine()==value.getLineNumber()
                                        && t.getCharPositionInLine()+1==value.getColumnNumber()).findFirst().orElse(null);
                                boolean quoted = token!=null && token.getType()==GroovyLexer.StringLiteral
                                        && (token.getText().startsWith("'") || token.getText().startsWith("\""))
                                        && !token.getText().startsWith("'''") && !token.getText().startsWith("\"\"\"");
                                if (!(entry.getKeyExpression() instanceof ConstantExpression key) || !"from".equals(key.getValue())
                                        || !(value instanceof ConstantExpression constant) || !(constant.getValue() instanceof String from)
                                        || from.isEmpty() || !quoted) problem("APPLY_SYNTAX",call);
                                else directives.add(new Directive(sourceId,from,offset(text,call.getLineNumber(),call.getColumnNumber()),
                                        offset(text,call.getLastLineNumber(),call.getLastColumnNumber()),
                                        text.offsetByCodePoints(0,token.getStartIndex()), text.offsetByCodePoints(0,token.getStopIndex()+1),
                                        call.getLineNumber(),call.getColumnNumber()));
                            }
                        }
                    }
                    super.visitMethodCallExpression(call);
                }
            };
            module.getStatementBlock().visit(visitor);
            for (var method : module.getMethods()) if (method.getCode()!=null) method.getCode().visit(visitor);
            scriptContext[0]=false;
            for (var type : module.getClasses()) if (!type.isScript()) {
                helperType[0]=type;
                for (var field : type.getFields()) if (field.getInitialExpression()!=null) field.getInitialExpression().visit(visitor);
                for (var initializer : type.getObjectInitializerStatements()) initializer.visit(visitor);
                for (var method : type.getMethods()) if (method.getDeclaringClass()==type && method.getCode()!=null) method.getCode().visit(visitor);
                for (var constructor : type.getDeclaredConstructors()) if (constructor.getCode()!=null) constructor.getCode().visit(visitor);
            }
            if (!directives.isEmpty() && !shadow.isEmpty()) visitor.problem("APPLY_CONTEXT",shadow.getFirst());
        } catch (CompilationFailedException failure) {
            int line=0,column=0;
            if(unit!=null && unit.getErrorCollector().getErrors()!=null) {
                for(Object item:unit.getErrorCollector().getErrors()) {
                    if(item instanceof org.codehaus.groovy.control.messages.SyntaxErrorMessage syntax) {
                        line=Math.max(0,syntax.getCause().getStartLine());column=Math.max(0,syntax.getCause().getStartColumn());break;
                    }
                }
            }
            problems.add(new Problem("APPLY_SYNTAX",sourceId,line,column));
        } catch (StackOverflowError failure) {
            problems.add(new Problem("APPLY_LIMIT",sourceId,0,0));
        } catch (IOException failure) { throw new IllegalStateException(failure); }
        return new Result(directives,problems);
    }
    private static boolean ownsHelperApply(ClassNode owner,MethodCallExpression call,ModuleNode module,boolean mapOptions) {
        int count=call.getArguments() instanceof TupleExpression tuple?tuple.getExpressions().size():1;
        Set<String> seen=new HashSet<>();boolean unknownParent=false;
        Deque<ClassNode> pending=new ArrayDeque<>();if(owner!=null)pending.add(owner);
        while(!pending.isEmpty()) {
            owner=pending.removeFirst();if(!seen.add(owner.getName()))continue;
            var classBounds=genericBounds(owner.getGenericsTypes());
            for(var field:owner.getFields()) if(field.getName().equals("apply")) {
                Expression initializer=field.getInitialExpression();
                while(initializer instanceof CastExpression cast && Set.of("Closure","groovy.lang.Closure","Object","java.lang.Object").contains(typeName(cast.getType(),module))) initializer=cast.getExpression();
                if(initializer instanceof ClosureExpression closure) {
                    var parameters=closure.getParameters();
                    // Groovy represents explicit { -> } by null and implicit { it } by an empty array.
                    if(parameters!=null && parameters.length==0 && count<=1)return true;
                    if(matchesParameters(parameters==null?Parameter.EMPTY_ARRAY:parameters,count,mapOptions,module,classBounds))return true;
                } else if(Set.of("groovy.lang.Closure","Closure").contains(typeName(field.getType(),module))) return true;
            }
            for(var method:owner.getMethods()) if(method.getName().equals("apply") && method.getDeclaringClass()==owner) {
                var bounds=new LinkedHashMap<>(classBounds);bounds.putAll(genericBounds(method.getGenericsTypes()));
                if(matchesParameters(method.getParameters(),count,mapOptions,module,bounds))return true;
            }
            var parents=new ArrayList<ClassNode>(Arrays.asList(owner.getInterfaces()));
            if(owner.getUnresolvedSuperClass()!=null)parents.add(owner.getUnresolvedSuperClass());
            for(var parent:parents) {
                String name=typeName(parent,module);
                String qualified=owner.getPackageName()==null?name:owner.getPackageName()+"."+name;
                var local=module.getClasses().stream().filter(t->t.getName().equals(name)||t.getName().equals(qualified)).findFirst();
                if(local.isPresent())pending.addLast(local.get());
                else if(!Set.of("java.lang.Object","Object","groovy.lang.Script","Script",
                        "ch.so.agi.gretl.runtime.groovy.GretlScript","GretlScript").contains(name))unknownParent=true;
            }
        }
        // An external helper API may own apply. Never load it to guess method binding.
        return unknownParent;
    }
    private static boolean matchesParameters(Parameter[] parameters,int count,boolean mapOptions,ModuleNode module,Map<String,ClassNode> bounds) {
        int required=parameters.length;
        while(required>0 && (parameters[required-1].hasInitialExpression()
                || required==parameters.length && parameters[required-1].getType().isArray())) required--;
        boolean varargs=parameters.length>0 && parameters[parameters.length-1].getType().isArray();
        if(count<required || count>parameters.length && !varargs)return false;
        if(!mapOptions)return true;
        if(parameters.length==0)return false;
        var type=parameters[0].getType();if(type.isArray())type=type.getComponentType();
        Set<String> seen=new HashSet<>();
        while(bounds.containsKey(type.getName()) && seen.add(type.getName()))type=bounds.get(type.getName());
        String first=typeName(type,module);
        if(!parameters[0].isDynamicTyped() && module.getClasses().stream()
                .anyMatch(t->t.getName().equals(first)||t.getNameWithoutPackage().equals(first)))return false;
        return parameters[0].isDynamicTyped() || Set.of("java.lang.Object","Object","java.util.Map","Map",
                "java.util.AbstractMap","AbstractMap","java.util.HashMap","HashMap","java.util.LinkedHashMap","LinkedHashMap",
                "java.io.Serializable","Serializable","java.lang.Cloneable","Cloneable").contains(first);
    }
    private static Map<String,ClassNode> genericBounds(GenericsType[] parameters) {
        var bounds=new LinkedHashMap<String,ClassNode>();
        if(parameters!=null)for(var parameter:parameters) {
            var upper=parameter.getUpperBounds();
            bounds.put(parameter.getName(),upper==null || upper.length==0?ClassHelper.OBJECT_TYPE:upper[0]);
        }
        return bounds;
    }
    private static String typeName(ClassNode type,ModuleNode module) {
        String name=type.getName();
        for(var imported:module.getImports())if(Objects.equals(imported.getAlias(),name))return imported.getType().getName();
        return name;
    }
    private static boolean namedOptions(MethodCallExpression call) {
        var arguments=call.getArguments();
        return arguments instanceof MapExpression || arguments instanceof TupleExpression tuple
                && tuple.getExpressions().stream().anyMatch(e->e instanceof MapExpression);
    }
    private static boolean shadowsApply(Expression expression) {
        if(expression instanceof DeclarationExpression declaration && declaration.isMultipleAssignmentDeclaration())
            return declaration.getTupleExpression().getExpressions().stream().anyMatch(e->e instanceof VariableExpression v && v.getName().equals("apply"));
        return expression instanceof BinaryExpression binary && binary.getOperation().getText().equals("=")
                && binary.getLeftExpression() instanceof VariableExpression v && v.getName().equals("apply");
    }
    private static int offset(String text, int line, int column) {
        int start=0;
        for (int n=1;n<line;n++) { int end=text.indexOf('\n',start); if(end<0)return text.length();start=end+1; }
        int end=text.indexOf('\n',start);if(end<0)end=text.length();
        return text.offsetByCodePoints(start,Math.min(Math.max(0,column-1),text.codePointCount(start,end)));
    }
}
