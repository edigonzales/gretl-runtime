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
import org.codehaus.groovy.control.*;

/** Parse/convert only: no transforms, script instantiation, classpath discovery or evaluation. */
public final class ApplyDirectiveReader {
    /** Half-open offsets count UTF-16 code units; source diagnostic lines/columns are one-based. */
    public record Directive(String sourceId, String from, int start, int end,
                            int literalStart, int literalEnd, int line, int column) {
        public String siteId() { return sourceId + "@" + start; }
    }
    public record Problem(String code, String sourceId, int line, int column) { }
    public record Result(List<Directive> directives, List<Problem> problems, boolean applySeen) {
        public Result(List<Directive> directives,List<Problem> problems){this(directives,problems,!directives.isEmpty());}
        public Result { directives = List.copyOf(directives); problems = List.copyOf(problems); }
        public List<Directive> requireValid() {return requireValid(ch.so.agi.gretl.i18n.Messages.sourceLocale());}
        public List<Directive> requireValid(Locale locale) {
            if (!problems.isEmpty()) {
                var p = problems.getFirst();
                throw new GroovyDslException(p.code(), ch.so.agi.gretl.i18n.Messages.format(locale, "apply." + p.code(), p.sourceId()), null, null,
                        new SourceLocation(p.sourceId(), p.line(), p.column()), List.of(), null);
            }
            return directives;
        }
    }
    public Result read(String sourceId,String text){return read(sourceId,text,false);}
    public Result read(String sourceId, String text, boolean fragment) {
        Objects.requireNonNull(sourceId); Objects.requireNonNull(text);
        var config = CompilerSettings.create(); config.setScriptBaseClass(null);
        var directives = new ArrayList<Directive>(); var problems = new ArrayList<Problem>();
        SourceUnit unit=null;boolean[] applySeen={false};
        try (var loader = new GroovyClassLoader(getClass().getClassLoader(), config)) {
            unit = new SourceUnit(ScriptSourceNames.virtualGroovyName(sourceId), text, config, loader, new ErrorCollector(config));
            unit.parse(); unit.completePhase(); unit.nextPhase(); unit.convert();
            var module = unit.getAST();
            var tokens = new GroovyLangLexer(CharStreams.fromString(text)).getAllTokens().stream()
                    .map(t->new ch.so.agi.gretl.syntax.ApplyAstInspector.LiteralToken(
                            text.offsetByCodePoints(0,t.getStartIndex()),text.offsetByCodePoints(0,t.getStopIndex()+1),
                            t.getText(),t.getType()==GroovyLexer.StringLiteral)).toList();
            var result=new ch.so.agi.gretl.syntax.ApplyAstInspector().inspect(sourceId,text,module,tokens,fragment,()->{});
            directives.addAll(result.directives().stream().map(d->new Directive(d.sourceId(),d.from(),d.start(),d.end(),d.literalStart(),d.literalEnd(),d.line(),d.column())).toList());
            problems.addAll(result.problems().stream().map(p->new Problem(p.code(),p.sourceId(),p.line(),p.column())).toList());
            applySeen[0]=result.applySeen();
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
        return new Result(directives,problems,applySeen[0]);
    }
}
