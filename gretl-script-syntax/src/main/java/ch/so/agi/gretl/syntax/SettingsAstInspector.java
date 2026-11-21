package ch.so.agi.gretl.syntax;

import groovyjarjarantlr4.v4.runtime.Token;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.groovy.parser.antlr4.GroovyLexer;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;

/**
 * Inspects a converted settings AST and the Groovy lexer tokens for the same parser text.
 * Supply tokens in source order, with Groovy's code-point indexes and LF-based line coordinates.
 */
public final class SettingsAstInspector {
    public sealed interface Finding permits Unsupported, Directive { }
    public record Unsupported(ASTNode node, Token importToken) implements Finding { }
    public record Directive(String name, ASTNode node, List<Argument> arguments, boolean repeated) implements Finding { }
    /** A null literal marks an unsupported argument. The AST node retains its enclosing range. */
    public record Argument(ASTNode node, String value, Token literal) { }

    public List<Finding> inspect(ModuleNode module, List<? extends Token> tokens, Runnable checkCancellation) {
        var findings = new ArrayList<Finding>();
        var statements = List.copyOf(module.getStatementBlock().getStatements());
        if (module.getPackage() != null) findings.add(new Unsupported(module.getPackage(), null));
        addUnsupported(findings, module.getImports());
        addUnsupported(findings, module.getStarImports());
        if (!module.getModuleStarImports().isEmpty()) {
            // Groovy leaves module-import AST nodes without positions.
            var token = tokens.stream().filter(t -> t.getType() == GroovyLexer.IMPORT).findFirst().orElse(null);
            findings.add(new Unsupported(module.getModuleStarImports().getFirst(), token));
        }
        addUnsupported(findings, module.getStaticImports().values());
        addUnsupported(findings, module.getStaticStarImports().values());
        addUnsupported(findings, module.getMethods());
        for (var type : module.getClasses()) {
            checkCancellation.run();
            if (!type.isScript()) findings.add(new Unsupported(type, null));
            addUnsupported(findings, type.getAnnotations());
        }

        Set<String> seen = new HashSet<>();
        for (var statement : statements) {
            checkCancellation.run();
            // Groovy's synthetic empty-script return is distinct from a user-written return.
            if (statement == ReturnStatement.RETURN_NULL_OR_VOID) continue;
            if (!(statement instanceof ExpressionStatement expression)
                    || (statement.getStatementLabels() != null && !statement.getStatementLabels().isEmpty())
                    || !(expression.getExpression() instanceof MethodCallExpression call)
                    || !call.isImplicitThis() || call.isSafe() || call.isSpreadSafe() || call.isUsingGenerics()
                    || !(call.getMethod() instanceof ConstantExpression method)
                    || !(method.getValue() instanceof String name)
                    || !(call.getArguments() instanceof ArgumentListExpression arguments)
                    || !(name.equals("include") || name.equals("defaults"))) {
                findings.add(new Unsupported(statement, null));
                continue;
            }
            var values = new ArrayList<Argument>();
            boolean supported = true;
            for (var argument : arguments.getExpressions()) {
                checkCancellation.run();
                Token literal = null;
                String value = null;
                if (argument instanceof ConstantExpression constant && constant.getValue() instanceof String text) {
                    literal = simpleLiteral(argument, tokens);
                    value = text;
                }
                supported &= literal != null;
                values.add(new Argument(argument, value, literal));
            }
            // Tooling can continue after an unsupported argument. Only structurally
            // supported calls claim a directive in that case.
            boolean repeated = seen.contains(name);
            if (supported) seen.add(name);
            findings.add(new Directive(name, call, List.copyOf(values), repeated));
        }
        return List.copyOf(findings);
    }

    private static void addUnsupported(List<Finding> findings, Iterable<? extends ASTNode> nodes) {
        for (var node : nodes) findings.add(new Unsupported(node, null));
    }

    private static Token simpleLiteral(ASTNode node, List<? extends Token> tokens) {
        long start = position(node.getLineNumber(), node.getColumnNumber());
        long end = position(node.getLastLineNumber(), node.getLastColumnNumber());
        if (node.getLineNumber() < 1 || node.getColumnNumber() < 1 || end <= start) return null;
        // A parenthesized constant can start at its opening parenthesis in the AST.
        int left = 0, right = tokens.size();
        while (left < right) {
            int middle = (left + right) >>> 1;
            if (position(tokens.get(middle)) < start) left = middle + 1;
            else right = middle;
        }
        for (int index = left; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (position(token) >= end) break;
            if (token.getChannel() != Token.DEFAULT_CHANNEL || token.getType() == GroovyLexer.NL
                    || token.getType() == GroovyLexer.LPAREN) continue;
            if (token.getType() == GroovyLexer.StringLiteral) {
                String text = token.getText();
                if ((text.startsWith("'") && !text.startsWith("'''"))
                        || (text.startsWith("\"") && !text.startsWith("\"\"\""))) return token;
            }
            break;
        }
        return null;
    }

    private static long position(Token token) {
        return position(token.getLine(), token.getCharPositionInLine() + 1);
    }

    private static long position(int line, int column) {
        return ((long) line << 32) | (column & 0xffffffffL);
    }
}
