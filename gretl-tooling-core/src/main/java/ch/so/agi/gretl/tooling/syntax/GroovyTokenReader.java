package ch.so.agi.gretl.tooling.syntax;

import java.util.*;
import java.util.function.Supplier;
import groovyjarjarantlr4.v4.runtime.*;
import org.apache.groovy.parser.antlr4.*;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import static ch.so.agi.gretl.tooling.syntax.TokenIndex.*;

/** Uses the pinned Groovy lexer, including its string modes and comment callbacks. */
public final class GroovyTokenReader {
    public static final int MAX_LITERAL_UTF16=65_536;
    public static final int MAX_NUMBER_UTF16=4_096;
    public static final int MAX_LOGICAL_STATEMENT_TOKENS=512;
    public TokenIndex lex(SourceText source,ParseOptions budget,CancellationToken cancellation) {
        var mapper=new ParserCoordinateMapper(source);var raw=new ArrayList<LexToken>();var comments=new ArrayList<LexToken>();var problems=new ArrayList<SyntaxProblem>();
        var lexer=new GroovyLangLexer(CharStreams.fromString(source.hasBom()?" "+source.text().substring(1):source.text())) {
            @Override protected void handleComment(int type,Supplier<String> text) {
                var span=new TextSpan(mapper.lexerOffset(_tokenStartCharIndex),mapper.lexerOffset(getCharIndex()));
                comments.add(new LexToken(type,"COMMENT",source.slice(span),span,Channel.COMMENT));
            }
        };
        lexer.removeErrorListeners();lexer.setErrorIgnored(true);
        int nesting=0,grouping=0,gstrings=0,gstart=-1,tokenCount=0,statementTokens=0,previousType=Token.INVALID_TYPE;boolean limited=false,pendingLineBreak=false;
        try {
            while(true) {
                cancellation.check();Token token=lexer.nextToken();if(token.getType()==Token.EOF)break;
                int type=token.getType();var span=new TextSpan(mapper.lexerOffset(token.getStartIndex()),mapper.lexerOffset(token.getStopIndex()+1));
                // A newline is a safe complexity boundary only outside parenthesized groups,
                // after a complete operand and before an unambiguous new statement operand.
                // Continued .property, operators, calls, ternaries and unary chains never reset it.
                if(type==GroovyLexer.NL)pendingLineBreak=true;
                else if(type!=GroovyLexer.SH_COMMENT){
                    if(type==GroovyLexer.SEMI||(pendingLineBreak&&grouping==0&&canEndStatement(previousType)&&canStartStatement(type)))statementTokens=0;
                    pendingLineBreak=false;
                    if(++statementTokens>MAX_LOGICAL_STATEMENT_TOKENS){limited=true;problems.add(new SyntaxProblem("PARSE_LIMIT",span,"Logical statement exceeds syntax analysis budget"));break;}
                    previousType=type;
                }
                if(type==GroovyLexer.LPAREN||type==GroovyLexer.LBRACK)grouping++;
                if(type==GroovyLexer.RPAREN||type==GroovyLexer.RBRACK)grouping=Math.max(0,grouping-1);
                if(type==GroovyLexer.LPAREN||type==GroovyLexer.LBRACK||type==GroovyLexer.LBRACE)nesting++;
                if(type==GroovyLexer.RPAREN||type==GroovyLexer.RBRACK||type==GroovyLexer.RBRACE)nesting=Math.max(0,nesting-1);
                if(nesting>budget.maxNesting()||++tokenCount>budget.maxTokens()) {limited=true;problems.add(new SyntaxProblem("PARSE_LIMIT",span,"Syntax analysis budget exceeded"));break;}
                if((type==GroovyLexer.StringLiteral&&span.length()>MAX_LITERAL_UTF16)||((type==GroovyLexer.IntegerLiteral||type==GroovyLexer.FloatingPointLiteral)&&span.length()>MAX_NUMBER_UTF16)){
                    limited=true;problems.add(new SyntaxProblem("PARSE_LIMIT",span,"Literal exceeds syntax analysis budget"));break;
                }
                if(type==GroovyLexer.GStringBegin) { if(gstrings++==0)gstart=span.startUtf16(); }
                if(gstrings>0) {
                    if(type==GroovyLexer.GStringEnd && --gstrings==0){var full=new TextSpan(gstart,span.endUtf16());if(full.length()>MAX_LITERAL_UTF16){limited=true;problems.add(new SyntaxProblem("PARSE_LIMIT",full,"Interpolated string exceeds syntax analysis budget"));break;}raw.add(new LexToken(type,"GString",source.slice(full),full,Channel.STRING));}
                    continue;
                }
                String kind=lexer.getVocabulary().getSymbolicName(type);
                Channel channel=type==GroovyLexer.StringLiteral?Channel.STRING:Channel.CODE;
                if(type==GroovyLexer.NL)channel=Channel.WHITESPACE;
                if(type==GroovyLexer.SH_COMMENT)channel=Channel.COMMENT;
                if(type==GroovyLexer.UNEXPECTED_CHAR){channel=Channel.OPAQUE;problems.add(new SyntaxProblem("LEXICAL_ERROR",span,"Unexpected or incomplete token"));}
                raw.add(new LexToken(type,kind==null?"UNKNOWN":kind,source.slice(span),span,channel));
            }
        } catch(java.util.concurrent.CancellationException e){throw e;}
        catch(RuntimeException | org.apache.groovy.parser.antlr4.GroovySyntaxError e){
            int offset=mapper.lexerOffset(lexer._tokenStartCharIndex);problems.add(new SyntaxProblem("LEXICAL_ERROR",new TextSpan(offset,Math.min(source.text().length(),offset+1)),"Incomplete Groovy token"));
        }
        if(gstrings>0){var span=new TextSpan(gstart,source.text().length());raw.add(new LexToken(GroovyLexer.GStringBegin,"GString",source.slice(span),span,Channel.STRING));if(!limited)problems.add(new SyntaxProblem("LEXICAL_ERROR",span,"Unterminated interpolated string"));}
        // Comment tokens are reported as NL by Groovy. Retain one comment range, not an overlapping newline.
        var commentSpans=new HashSet<TextSpan>();comments.forEach(c->commentSpans.add(c.span()));
        raw.removeIf(t->commentSpans.contains(t.span()));
        raw.addAll(comments);raw.sort(Comparator.comparingInt(t->t.span().startUtf16()));
        var ledger=new ArrayList<LexToken>();int cursor=0;
        for(var token:raw){if(token.span().startUtf16()<cursor)continue;if(token.span().startUtf16()>cursor)addGap(ledger,source,cursor,token.span().startUtf16());ledger.add(token);cursor=token.span().endUtf16();}
        if(cursor<source.text().length())addGap(ledger,source,cursor,source.text().length());
        return new TokenIndex(ledger,problems,limited);
    }
    private static boolean canEndStatement(int type) {
        return type==GroovyLexer.Identifier||type==GroovyLexer.CapitalizedIdentifier||type==GroovyLexer.StringLiteral||type==GroovyLexer.GStringEnd||type==GroovyLexer.IntegerLiteral||type==GroovyLexer.FloatingPointLiteral||type==GroovyLexer.BooleanLiteral||type==GroovyLexer.NullLiteral||type==GroovyLexer.RPAREN||type==GroovyLexer.RBRACK||type==GroovyLexer.RBRACE;
    }
    private static boolean canStartStatement(int type) {
        return type==GroovyLexer.Identifier||type==GroovyLexer.CapitalizedIdentifier||type==GroovyLexer.DEF||type==GroovyLexer.FINAL||type==GroovyLexer.RETURN||type==GroovyLexer.IF||type==GroovyLexer.FOR||type==GroovyLexer.WHILE||type==GroovyLexer.TRY||type==GroovyLexer.THROW||type==GroovyLexer.StringLiteral||type==GroovyLexer.IntegerLiteral||type==GroovyLexer.FloatingPointLiteral||type==GroovyLexer.BooleanLiteral||type==GroovyLexer.NullLiteral;
    }
    private static void addGap(List<LexToken> out,SourceText source,int start,int end) {
        String text=source.text().substring(start,end);Channel channel=text.chars().allMatch(c->Character.isWhitespace(c)||c=='\uFEFF')?Channel.WHITESPACE:Channel.OPAQUE;
        out.add(new LexToken(0,"TRIVIA",text,new TextSpan(start,end),channel));
    }
}
