package ch.so.agi.gretl.tooling.syntax;

/** Hard work bounds checked before invoking Groovy's non-interruptible syntax reader. */
public record ParseOptions(int maxUtf16Length,int maxTokens,int maxNesting) {
    public static final ParseOptions DEFAULT = new ParseOptions(1_048_576,200_000,128);
    public ParseOptions { if(maxUtf16Length<1||maxTokens<1||maxNesting<1||maxNesting>256)throw new IllegalArgumentException("Invalid parse budget"); }
}
