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
            boolean[] helperOwnApply = {false};
            for (var method : module.getMethods()) if (method.getName().equals("apply")) shadow.add(method);
            var visitor = new CodeVisitorSupport() {
                void problem(String code, ASTNode node) {
                    problems.add(new Problem(code, sourceId, Math.max(0,node.getLineNumber()), Math.max(0,node.getColumnNumber())));
                }
                @Override public void visitDeclarationExpression(DeclarationExpression node) {
                    if (scriptContext[0] && !node.isMultipleAssignmentDeclaration() && node.getVariableExpression().getName().equals("apply")) shadow.add(node);
                    if (scriptContext[0] && node.isMultipleAssignmentDeclaration()) for (var item : node.getTupleExpression().getExpressions())
                        if (item instanceof VariableExpression v && v.getName().equals("apply")) shadow.add(node);
                    super.visitDeclarationExpression(node);
                }
                @Override public void visitBinaryExpression(BinaryExpression node) {
                    if (scriptContext[0] && node.getLeftExpression() instanceof VariableExpression v && v.getName().equals("apply")
                            && node.getOperation().getText().equals("=")) shadow.add(node);
                    super.visitBinaryExpression(node);
                }
                @Override public void visitMethodCallExpression(MethodCallExpression call) {
                    boolean self = call.isImplicitThis() || call.getObjectExpression() instanceof VariableExpression v && v.isThisExpression();
                    boolean ordinaryOwnedCall=scriptContext[0] && scriptOwnApply && !namedOptions(call);
                    if (self && !helperOwnApply[0] && !ordinaryOwnedCall && "apply".equals(call.getMethodAsString())) {
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
                helperOwnApply[0]=type.getMethods().stream().anyMatch(m->m.getName().equals("apply") && m.getDeclaringClass()==type);
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
