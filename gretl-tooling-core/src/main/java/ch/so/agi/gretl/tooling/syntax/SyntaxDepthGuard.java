package ch.so.agi.gretl.tooling.syntax;

import ch.so.agi.gretl.tooling.CancellationToken;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.ModuleNode;

/** Bounds converted AST depth before any recursive extractor or reference visitor sees it.
 * Property, unary and operator chains need no lexical brackets, so token nesting alone is insufficient.
 * Every visitor entry point in pinned Groovy 6 is covered, including forwarding/delegating variants.
 */
final class SyntaxDepthGuard extends CodeVisitorSupport {
    static final class Limit extends RuntimeException {
        private static final long serialVersionUID=1L;
        final transient ASTNode node;
        Limit(ASTNode node) { super("Converted syntax exceeds depth budget",null,false,false);this.node=node; }
    }
    private final int maximum;
    private final CancellationToken cancellation;
    private int depth;
    SyntaxDepthGuard(int maximum,CancellationToken cancellation) { this.maximum=maximum;this.cancellation=cancellation; }
    void check(ModuleNode module) { module.getStatementBlock().visit(this); }
    private void bounded(ASTNode node,Runnable traversal) {
        cancellation.check();if(depth>=maximum)throw new Limit(node);
        depth++;try { traversal.run(); } finally { depth--; }
    }
    @Override public void visitBlockStatement(org.codehaus.groovy.ast.stmt.BlockStatement node) { bounded(node,()->super.visitBlockStatement(node)); }
    @Override public void visitForLoop(org.codehaus.groovy.ast.stmt.ForStatement node) { bounded(node,()->super.visitForLoop(node)); }
    @Override public void visitWhileLoop(org.codehaus.groovy.ast.stmt.WhileStatement node) { bounded(node,()->super.visitWhileLoop(node)); }
    @Override public void visitDoWhileLoop(org.codehaus.groovy.ast.stmt.DoWhileStatement node) { bounded(node,()->super.visitDoWhileLoop(node)); }
    @Override public void visitIfElse(org.codehaus.groovy.ast.stmt.IfStatement node) { bounded(node,()->super.visitIfElse(node)); }
    @Override public void visitExpressionStatement(org.codehaus.groovy.ast.stmt.ExpressionStatement node) { bounded(node,()->super.visitExpressionStatement(node)); }
    @Override public void visitReturnStatement(org.codehaus.groovy.ast.stmt.ReturnStatement node) { bounded(node,()->super.visitReturnStatement(node)); }
    @Override public void visitAssertStatement(org.codehaus.groovy.ast.stmt.AssertStatement node) { bounded(node,()->super.visitAssertStatement(node)); }
    @Override public void visitTryCatchFinally(org.codehaus.groovy.ast.stmt.TryCatchStatement node) { bounded(node,()->super.visitTryCatchFinally(node)); }
    @Override public void visitCatchStatement(org.codehaus.groovy.ast.stmt.CatchStatement node) { bounded(node,()->super.visitCatchStatement(node)); }
    @Override public void visitSwitch(org.codehaus.groovy.ast.stmt.SwitchStatement node) { bounded(node,()->super.visitSwitch(node)); }
    @Override public void visitCaseStatement(org.codehaus.groovy.ast.stmt.CaseStatement node) { bounded(node,()->super.visitCaseStatement(node)); }
    @Override public void visitBreakStatement(org.codehaus.groovy.ast.stmt.BreakStatement node) { bounded(node,()->super.visitBreakStatement(node)); }
    @Override public void visitContinueStatement(org.codehaus.groovy.ast.stmt.ContinueStatement node) { bounded(node,()->super.visitContinueStatement(node)); }
    @Override public void visitSynchronizedStatement(org.codehaus.groovy.ast.stmt.SynchronizedStatement node) { bounded(node,()->super.visitSynchronizedStatement(node)); }
    @Override public void visitThrowStatement(org.codehaus.groovy.ast.stmt.ThrowStatement node) { bounded(node,()->super.visitThrowStatement(node)); }
    @Override public void visitEmptyStatement(org.codehaus.groovy.ast.stmt.EmptyStatement node) { bounded(node,()->super.visitEmptyStatement(node)); }
    @Override public void visitYieldStatement(org.codehaus.groovy.ast.stmt.YieldStatement node) { bounded(node,()->super.visitYieldStatement(node)); }
    @Override public void visitMethodCallExpression(org.codehaus.groovy.ast.expr.MethodCallExpression node) { bounded(node,()->super.visitMethodCallExpression(node)); }
    @Override public void visitStaticMethodCallExpression(org.codehaus.groovy.ast.expr.StaticMethodCallExpression node) { bounded(node,()->super.visitStaticMethodCallExpression(node)); }
    @Override public void visitConstructorCallExpression(org.codehaus.groovy.ast.expr.ConstructorCallExpression node) { bounded(node,()->super.visitConstructorCallExpression(node)); }
    @Override public void visitBinaryExpression(org.codehaus.groovy.ast.expr.BinaryExpression node) { bounded(node,()->super.visitBinaryExpression(node)); }
    @Override public void visitTernaryExpression(org.codehaus.groovy.ast.expr.TernaryExpression node) { bounded(node,()->super.visitTernaryExpression(node)); }
    @Override public void visitSwitchExpression(org.codehaus.groovy.ast.expr.SwitchExpression node) { bounded(node,()->super.visitSwitchExpression(node)); }
    @Override public void visitShortTernaryExpression(org.codehaus.groovy.ast.expr.ElvisOperatorExpression node) { bounded(node,()->super.visitShortTernaryExpression(node)); }
    @Override public void visitPostfixExpression(org.codehaus.groovy.ast.expr.PostfixExpression node) { bounded(node,()->super.visitPostfixExpression(node)); }
    @Override public void visitPrefixExpression(org.codehaus.groovy.ast.expr.PrefixExpression node) { bounded(node,()->super.visitPrefixExpression(node)); }
    @Override public void visitBooleanExpression(org.codehaus.groovy.ast.expr.BooleanExpression node) { bounded(node,()->super.visitBooleanExpression(node)); }
    @Override public void visitNotExpression(org.codehaus.groovy.ast.expr.NotExpression node) { bounded(node,()->super.visitNotExpression(node)); }
    @Override public void visitClosureExpression(org.codehaus.groovy.ast.expr.ClosureExpression node) { bounded(node,()->super.visitClosureExpression(node)); }
    @Override public void visitLambdaExpression(org.codehaus.groovy.ast.expr.LambdaExpression node) { bounded(node,()->super.visitLambdaExpression(node)); }
    @Override public void visitTupleExpression(org.codehaus.groovy.ast.expr.TupleExpression node) { bounded(node,()->super.visitTupleExpression(node)); }
    @Override public void visitListExpression(org.codehaus.groovy.ast.expr.ListExpression node) { bounded(node,()->super.visitListExpression(node)); }
    @Override public void visitArrayExpression(org.codehaus.groovy.ast.expr.ArrayExpression node) { bounded(node,()->super.visitArrayExpression(node)); }
    @Override public void visitMapExpression(org.codehaus.groovy.ast.expr.MapExpression node) { bounded(node,()->super.visitMapExpression(node)); }
    @Override public void visitMapEntryExpression(org.codehaus.groovy.ast.expr.MapEntryExpression node) { bounded(node,()->super.visitMapEntryExpression(node)); }
    @Override public void visitRangeExpression(org.codehaus.groovy.ast.expr.RangeExpression node) { bounded(node,()->super.visitRangeExpression(node)); }
    @Override public void visitSpreadExpression(org.codehaus.groovy.ast.expr.SpreadExpression node) { bounded(node,()->super.visitSpreadExpression(node)); }
    @Override public void visitSpreadMapExpression(org.codehaus.groovy.ast.expr.SpreadMapExpression node) { bounded(node,()->super.visitSpreadMapExpression(node)); }
    @Override public void visitMethodPointerExpression(org.codehaus.groovy.ast.expr.MethodPointerExpression node) { bounded(node,()->super.visitMethodPointerExpression(node)); }
    @Override public void visitMethodReferenceExpression(org.codehaus.groovy.ast.expr.MethodReferenceExpression node) { bounded(node,()->super.visitMethodReferenceExpression(node)); }
    @Override public void visitUnaryMinusExpression(org.codehaus.groovy.ast.expr.UnaryMinusExpression node) { bounded(node,()->super.visitUnaryMinusExpression(node)); }
    @Override public void visitUnaryPlusExpression(org.codehaus.groovy.ast.expr.UnaryPlusExpression node) { bounded(node,()->super.visitUnaryPlusExpression(node)); }
    @Override public void visitBitwiseNegationExpression(org.codehaus.groovy.ast.expr.BitwiseNegationExpression node) { bounded(node,()->super.visitBitwiseNegationExpression(node)); }
    @Override public void visitCastExpression(org.codehaus.groovy.ast.expr.CastExpression node) { bounded(node,()->super.visitCastExpression(node)); }
    @Override public void visitConstantExpression(org.codehaus.groovy.ast.expr.ConstantExpression node) { bounded(node,()->super.visitConstantExpression(node)); }
    @Override public void visitClassExpression(org.codehaus.groovy.ast.expr.ClassExpression node) { bounded(node,()->super.visitClassExpression(node)); }
    @Override public void visitVariableExpression(org.codehaus.groovy.ast.expr.VariableExpression node) { bounded(node,()->super.visitVariableExpression(node)); }
    @Override public void visitDeclarationExpression(org.codehaus.groovy.ast.expr.DeclarationExpression node) { bounded(node,()->super.visitDeclarationExpression(node)); }
    @Override public void visitPropertyExpression(org.codehaus.groovy.ast.expr.PropertyExpression node) { bounded(node,()->super.visitPropertyExpression(node)); }
    @Override public void visitAttributeExpression(org.codehaus.groovy.ast.expr.AttributeExpression node) { bounded(node,()->super.visitAttributeExpression(node)); }
    @Override public void visitFieldExpression(org.codehaus.groovy.ast.expr.FieldExpression node) { bounded(node,()->super.visitFieldExpression(node)); }
    @Override public void visitGStringExpression(org.codehaus.groovy.ast.expr.GStringExpression node) { bounded(node,()->super.visitGStringExpression(node)); }
    @Override public void visitArgumentlistExpression(org.codehaus.groovy.ast.expr.ArgumentListExpression node) { bounded(node,()->super.visitArgumentlistExpression(node)); }
    @Override public void visitClosureListExpression(org.codehaus.groovy.ast.expr.ClosureListExpression node) { bounded(node,()->super.visitClosureListExpression(node)); }
    @Override public void visitBytecodeExpression(org.codehaus.groovy.classgen.BytecodeExpression node) { bounded(node,()->super.visitBytecodeExpression(node)); }
    @Override public void visitEmptyExpression(org.codehaus.groovy.ast.expr.EmptyExpression node) { bounded(node,()->super.visitEmptyExpression(node)); }
    @Override public void visit(org.codehaus.groovy.ast.stmt.Statement node) { bounded(node,()->super.visit(node)); }
    @Override public void visit(org.codehaus.groovy.ast.expr.Expression node) { bounded(node,()->super.visit(node)); }
    @Override public void visitListOfExpressions(java.util.List<? extends org.codehaus.groovy.ast.expr.Expression> nodes) {
        cancellation.check();for(var node:nodes)visit(node);
    }
}

