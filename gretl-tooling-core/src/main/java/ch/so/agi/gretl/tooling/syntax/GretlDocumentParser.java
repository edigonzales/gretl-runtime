package ch.so.agi.gretl.tooling.syntax;

import java.util.*;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Single safe parsing entry point. Never loads a job, resolves dependencies or invokes workspace code. */
public final class GretlDocumentParser {
    public static final int MAX_RECOVERY_ISLANDS=256;
    public ParsedDocument parse(SourceText source){return parse(source,DocumentKind.JOB,ParseOptions.DEFAULT,CancellationToken.NONE);}
    public ParsedDocument parse(SourceText source,DocumentKind kind,ParseOptions options,CancellationToken cancellation){
        Objects.requireNonNull(source);Objects.requireNonNull(kind);Objects.requireNonNull(options);Objects.requireNonNull(cancellation);cancellation.check();
        if(source.text().length()>options.maxUtf16Length()){
            var problem=new SyntaxProblem("PARSE_LIMIT",new TextSpan(0,source.text().length()),"Source exceeds syntax analysis budget");
            var index=new TokenIndex(List.of(new TokenIndex.LexToken(0,"OPAQUE",source.text(),problem.span(),TokenIndex.Channel.OPAQUE)),List.of(problem),true);
            return new ParsedDocument(source,kind,index,List.of(),List.of(new OpaqueStatement(problem.span(),"SOURCE_LIMIT",true)),List.of(problem),Coverage.partial());
        }
        var tokens=new GroovyTokenReader().lex(source,options,cancellation);var merger=new ParseResultMerger();
        if(tokens.budgetExceeded())return new ParsedDocument(source,kind,tokens,List.of(),List.of(new OpaqueStatement(new TextSpan(0,source.text().length()),"TOKEN_OR_NESTING_LIMIT",true)),tokens.problems(),Coverage.partial());
        var reader=new SafeGroovySyntaxReader();var parsed=reader.readModule(source,cancellation);
        var problems=merger.problems(tokens.problems(),parsed.problems());
        if(parsed.problems().stream().anyMatch(p->p.code().equals("PARSE_LIMIT")))return limited(source,kind,tokens,problems);
        if(parsed.module()!=null){
            try { new SyntaxDepthGuard(options.maxNesting(),cancellation).check(parsed.module()); }
            catch(SyntaxDepthGuard.Limit exceeded) {
                var mapper=new ParserCoordinateMapper(source);var node=exceeded.node;
                var span=mapper.parserSpan(node.getLineNumber(),node.getColumnNumber(),node.getLastLineNumber(),node.getLastColumnNumber());
                var bounded=new ArrayList<>(problems);bounded.add(new SyntaxProblem("PARSE_LIMIT",span,"Converted syntax exceeds depth budget"));
                return limited(source,kind,tokens,bounded);
            }
            var extracted=new GretlAstExtractor(source,tokens,cancellation).extract(parsed.module());
            var includeTokens=tokens.all().stream().map(t->new ch.so.agi.gretl.syntax.ApplyAstInspector.LiteralToken(
                    t.span().startUtf16(),t.span().endUtf16(),t.text(),t.type()==org.apache.groovy.parser.antlr4.GroovyLexer.StringLiteral)).toList();
            var includes=new ch.so.agi.gretl.syntax.ApplyAstInspector().inspect(source.stamp().documentId().toString(),
                    source.text(),parsed.module(),includeTokens,false,cancellation::check);
            var includeProblems=new ArrayList<>(problems);var mapper=new ParserCoordinateMapper(source);
            for(var problem:includes.problems()){
                int at=mapper.parserOffset(problem.line(),problem.column());
                includeProblems.add(new SyntaxProblem(problem.code(),new TextSpan(at,at),"Invalid static include"));
            }
            var coverage=includeProblems.isEmpty()?extracted.coverage():Coverage.partial();
            // A recognized site still needs a complete closure. Do not open structural edit paths in P1.
            var opaque=extracted.opaque().stream().map(o->includes.directives().stream().anyMatch(d->
                    o.span().startUtf16()==d.start()&&o.span().endUtf16()==d.end())
                    ?new OpaqueStatement(o.span(),"UNRESOLVED_INCLUDE",true):o).toList();
            return new ParsedDocument(source,kind,tokens,extracted.declarations(),opaque,includeProblems,coverage,includes);
        }
        var recovery=new RecoveringDslParser(source,tokens,cancellation).recover();var islands=new ArrayList<Declaration>();int attempts=0;
        for(var declaration:recovery.declarations()){
            cancellation.check();if(++attempts>MAX_RECOVERY_ISLANDS)break;
            // Incomplete bodies stay recovered. A closed island can use the authoritative AST again.
            if(declaration.body()!=null&&!source.slice(declaration.body()).endsWith("}"))continue;
            if(declaration.span().length()==0)continue;
            var island=new SourceText(source.stamp().documentId(),source.slice(declaration.statementSpan()),source.stamp().bufferVersion(),"RECOVERY_ISLAND",false);
            var syntax=reader.readModule(island,cancellation);if(syntax.module()==null||!syntax.problems().isEmpty())continue;
            try { new SyntaxDepthGuard(options.maxNesting(),cancellation).check(syntax.module()); }catch(SyntaxDepthGuard.Limit exceeded){continue;}
            var extracted=new GretlAstExtractor(source,tokens,cancellation,island,declaration.statementSpan().startUtf16()).extract(syntax.module());
            for(var d:extracted.declarations())islands.add(merger.recoveredIsland(d));
        }
        if(attempts>MAX_RECOVERY_ISLANDS){var bounded=new ArrayList<>(problems);bounded.add(new SyntaxProblem("PARSE_LIMIT",new TextSpan(0,source.text().length()),"Recovery island budget exceeded; remaining islands are partial"));problems=List.copyOf(bounded);}
        var combined=merger.declarations(islands,recovery.declarations());
        return new ParsedDocument(source,kind,tokens,combined,recovery.opaque(),problems,Coverage.partial());
    }
    private ParsedDocument limited(SourceText source,DocumentKind kind,TokenIndex tokens,List<SyntaxProblem> problems) {
        return new ParsedDocument(source,kind,tokens,List.of(),List.of(new OpaqueStatement(new TextSpan(0,source.text().length()),"AST_DEPTH_LIMIT",true)),problems,Coverage.partial());
    }
}
