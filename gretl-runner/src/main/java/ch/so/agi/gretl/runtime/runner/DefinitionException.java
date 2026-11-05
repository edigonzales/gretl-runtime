package ch.so.agi.gretl.runtime.runner;

/** A static job/provider definition error discovered before task execution. */
public class DefinitionException extends RuntimeException implements SourceLocatedFailure {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final SourceLocation source;

    public DefinitionException(String code, String message) {
        this(code,message,null,null);
    }

    public DefinitionException(String code, String message, Throwable cause) {
        this(code,message,cause,null);
    }

    public DefinitionException(String code,String message,Throwable cause,SourceLocation source) {
        super(message,cause);
        this.code=requireText(code,"code");this.source=source;
    }

    @Override public java.util.Optional<SourceLocation> failureSource() {
        return source == null || source.line() == 0 ? java.util.Optional.empty() : java.util.Optional.of(source);
    }

    public String code() {
        return code;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
