package ch.so.agi.gretl.tooling.syntax;

import java.util.*;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Parse/convert only. Platform parent cannot see workspace/provider/transform JARs. */
final class SafeGroovySyntaxReader {
    record SyntaxReadResult(ModuleNode module,List<SyntaxProblem> problems) { SyntaxReadResult { problems=List.copyOf(problems); } }
    SyntaxReadResult readModule(SourceText source,CancellationToken cancellation) {
        cancellation.check();var configuration=new CompilerConfiguration();configuration.setTargetBytecode("25");configuration.setScriptBaseClass(null);
        configuration.setDisabledGlobalASTTransformations(Set.of("groovy.grape.GrabAnnotationTransformation","org.codehaus.groovy.transform.ASTTestTransformation"));
        var problems=new ArrayList<SyntaxProblem>();var coordinates=new ParserCoordinateMapper(source);SourceUnit unit=null;ModuleNode module=null;
        try(var loader=new GroovyClassLoader(ClassLoader.getPlatformClassLoader(),configuration)) {
            // BOM remains in original coordinates. Replace only its parser-view character with one space.
            String parserText=source.hasBom()?" "+source.text().substring(1):source.text();
            unit=new SourceUnit(source.stamp().documentId().toString(),parserText,configuration,loader,new ErrorCollector(configuration));
            unit.parse();unit.completePhase();unit.nextPhase();unit.convert();module=unit.getAST();
        } catch(java.io.IOException | RuntimeException | org.apache.groovy.parser.antlr4.GroovySyntaxError e) {
            if(unit!=null&&unit.getErrorCollector().getErrors()!=null)for(Object item:unit.getErrorCollector().getErrors())if(item instanceof SyntaxErrorMessage error){
                var cause=error.getCause();int start=coordinates.parserOffset(cause.getStartLine(),cause.getStartColumn());
                int end=coordinates.parserOffset(cause.getEndLine(),cause.getEndColumn());
                if(end<=start&&start<source.text().length())end=start+Character.charCount(source.text().codePointAt(start));
                problems.add(new SyntaxProblem("SYNTAX_ERROR",new TextSpan(start,Math.max(start,end)),"Incomplete or invalid Groovy syntax"));
            }
            if(problems.isEmpty())problems.add(new SyntaxProblem("SYNTAX_ERROR",new TextSpan(source.text().length(),source.text().length()),"Groovy syntax could not be read"));
        }
        cancellation.check();return new SyntaxReadResult(module,problems);
    }
}
