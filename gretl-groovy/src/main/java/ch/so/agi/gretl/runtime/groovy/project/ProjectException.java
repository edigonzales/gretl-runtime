package ch.so.agi.gretl.runtime.groovy.project;

import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import ch.so.agi.gretl.runtime.groovy.SourceLocation;
import java.util.List;

/** A preparation/definition error using the existing Groovy diagnostic contract. */
public final class ProjectException extends GroovyDslException {
    private static final long serialVersionUID = 1L;

    ProjectException(String code, String message, SourceLocation source, Throwable cause) {
        super(code, source + (source.column() > 0 ? ":" + source.column() : "")
                + " [" + code + "] " + message, null, null, source, List.of(), cause);
    }
}
