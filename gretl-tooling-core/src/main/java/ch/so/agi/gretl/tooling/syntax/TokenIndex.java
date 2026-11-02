package ch.so.agi.gretl.tooling.syntax;

import java.util.*;
import ch.so.agi.gretl.tooling.source.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Complete ordered token/trivia ledger over the original source. */
public final class TokenIndex {
    public enum Channel { CODE, STRING, COMMENT, WHITESPACE, OPAQUE }
    public record LexToken(int type,String kind,String text,TextSpan span,Channel channel) { }
    private final List<LexToken> tokens;
    private final List<SyntaxProblem> problems;
    private final boolean budgetExceeded;
    public TokenIndex(List<LexToken> tokens,List<SyntaxProblem> problems,boolean budgetExceeded) { this.tokens=List.copyOf(tokens);this.problems=List.copyOf(problems);this.budgetExceeded=budgetExceeded; }
    public List<LexToken> all() { return tokens; }
    public List<LexToken> significant() { return tokens.stream().filter(t->t.channel()==Channel.CODE||t.channel()==Channel.STRING).toList(); }
    public List<SyntaxProblem> problems() { return problems; }
    public boolean budgetExceeded() { return budgetExceeded; }
    public Optional<LexToken> at(int offset) { return tokens.stream().filter(t->t.span().contains(offset)).findFirst(); }
    public List<LexToken> within(TextSpan span) { return tokens.stream().filter(t->span.contains(t.span())).toList(); }
    public Optional<LexToken> exact(TextSpan span) { return tokens.stream().filter(t->t.span().equals(span)).findFirst(); }
}
