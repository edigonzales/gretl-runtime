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
        if(parsed.module()!=null){
            var extracted=new GretlAstExtractor(source,tokens,cancellation).extract(parsed.module());
            var coverage=problems.isEmpty()?extracted.coverage():new Coverage(extracted.coverage().declarations(),extracted.coverage().references(),extracted.coverage().fieldAssignments(),Completeness.PARTIAL);
            return new ParsedDocument(source,kind,tokens,extracted.declarations(),extracted.opaque(),problems,coverage);
        }
        var recovery=new RecoveringDslParser(source,tokens,cancellation).recover();var islands=new ArrayList<Declaration>();int attempts=0;
        for(var declaration:recovery.declarations()){
            cancellation.check();if(++attempts>MAX_RECOVERY_ISLANDS)break;
            // Incomplete bodies stay recovered. A closed island can use the authoritative AST again.
            if(declaration.body()!=null&&!source.slice(declaration.body()).endsWith("}"))continue;
            if(declaration.span().length()==0)continue;
            var island=new SourceText(source.stamp().documentId(),source.slice(declaration.statementSpan()),source.stamp().bufferVersion(),"RECOVERY_ISLAND",false);
            var syntax=reader.readModule(island,cancellation);if(syntax.module()==null||!syntax.problems().isEmpty())continue;
            var extracted=new GretlAstExtractor(source,tokens,cancellation,island,declaration.statementSpan().startUtf16()).extract(syntax.module());
            for(var d:extracted.declarations())islands.add(merger.recoveredIsland(d));
        }
        if(attempts>MAX_RECOVERY_ISLANDS){var bounded=new ArrayList<>(problems);bounded.add(new SyntaxProblem("PARSE_LIMIT",new TextSpan(0,source.text().length()),"Recovery island budget exceeded; remaining islands are partial"));problems=List.copyOf(bounded);}
        var combined=merger.declarations(islands,recovery.declarations());
        return new ParsedDocument(source,kind,tokens,combined,recovery.opaque(),problems,Coverage.partial());
    }
}
