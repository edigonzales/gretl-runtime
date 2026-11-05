package ch.so.agi.gretl.runtime.groovy.fragment;

import java.util.Set;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;

/** Compilation-only support for empty/class-only fragments, without changing any source bytes. */
public final class ScriptBundleCompilation {
    private ScriptBundleCompilation() { }
    public static void prepare(CompilationUnit unit,Set<String> scriptSourceNames) {
        var names=Set.copyOf(scriptSourceNames);
        unit.addPhaseOperation((CompilationUnit.ISourceUnitOperation)source->{
            if(!names.contains(source.getName()))return;
            var module=source.getAST();
            if(module.getClasses().stream().noneMatch(ClassNode::isScript)) {
                module.getStatementBlock().addStatement(EmptyStatement.INSTANCE);
                module.getClasses(); // Materialize this source's no-op Script; declarations stay in their own classes.
            }
        },Phases.CONVERSION);
    }
}
