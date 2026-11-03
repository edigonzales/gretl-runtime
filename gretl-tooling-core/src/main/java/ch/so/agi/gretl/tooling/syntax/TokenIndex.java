package ch.so.agi.gretl.tooling.syntax;

import java.util.*;
import ch.so.agi.gretl.tooling.source.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Complete ordered token/trivia ledger over the original source. */
public final class TokenIndex {
    public enum Channel { CODE, STRING, COMMENT, WHITESPACE, OPAQUE }
    public record LexToken(int type,String kind,String text,TextSpan span,Channel channel) { }
    private final List<LexToken> tokens;
    private final List<LexToken> significant;
    private final List<SyntaxProblem> problems;
    private final boolean budgetExceeded;
    public TokenIndex(List<LexToken> tokens,List<SyntaxProblem> problems,boolean budgetExceeded) {
        this.tokens=List.copyOf(tokens);this.problems=List.copyOf(problems);this.budgetExceeded=budgetExceeded;
        int end=0;for(var token:this.tokens){if(token.span().startUtf16()<end)throw new IllegalArgumentException("Unordered token ledger");end=token.span().endUtf16();}
        this.significant=this.tokens.stream().filter(t->t.channel()==Channel.CODE||t.channel()==Channel.STRING).toList();
    }
    public List<LexToken> all() { return tokens; }
    public List<LexToken> significant() { return significant; }
    public List<SyntaxProblem> problems() { return problems; }
    public boolean budgetExceeded() { return budgetExceeded; }
    public Optional<LexToken> at(int offset) {
        int low=0,high=tokens.size();while(low<high){int mid=(low+high)>>>1;if(tokens.get(mid).span().startUtf16()<=offset)low=mid+1;else high=mid;}
        return low>0&&tokens.get(low-1).span().contains(offset)?Optional.of(tokens.get(low-1)):Optional.empty();
    }
    public List<LexToken> within(TextSpan span) {
        var selected=new ArrayList<LexToken>();
        for(int i=lowerBound(span.startUtf16());i<tokens.size()&&tokens.get(i).span().startUtf16()<=span.endUtf16();i++)if(span.contains(tokens.get(i).span()))selected.add(tokens.get(i));
        return List.copyOf(selected);
    }
    public Optional<LexToken> exact(TextSpan span) {
        for(int i=lowerBound(span.startUtf16());i<tokens.size()&&tokens.get(i).span().startUtf16()==span.startUtf16();i++)if(tokens.get(i).span().equals(span))return Optional.of(tokens.get(i));
        return Optional.empty();
    }
    private int lowerBound(int offset){int low=0,high=tokens.size();while(low<high){int mid=(low+high)>>>1;if(tokens.get(mid).span().startUtf16()<offset)low=mid+1;else high=mid;}return low;}
}
