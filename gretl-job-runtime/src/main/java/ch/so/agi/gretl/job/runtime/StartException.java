package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import ch.so.agi.gretl.runtime.logging.Json;
import ch.so.agi.gretl.runtime.logging.TextFormatter;
import ch.so.agi.gretl.runtime.runner.DefinitionException;
import ch.so.agi.gretl.runtime.runner.ParameterException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** CLI failure with a documented exit-code class and a human-readable diagnostic. */
public final class StartException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int code;
    private final String diagnosticCode;
    private final String field;
    private final String task;
    private final String source;

    public StartException(int code, String message) {
        this(code, message, null, "CLI_ERROR", null, null, null);
    }

    public StartException(int code, String message, Throwable cause) {
        this(code, message, cause, "CLI_ERROR", null, null, null);
    }

    public StartException(int code, String message, Throwable cause, String diagnosticCode,
                        String field, String task, String source) {
        super(message, cause);
        this.code = code;
        this.diagnosticCode = diagnosticCode == null || diagnosticCode.isBlank()
                ? "CLI_ERROR" : diagnosticCode;
        this.field = field;
        this.task = task;
        this.source = source;
    }

    public int code() {
        return code;
    }

    public String diagnosticCode() {
        return diagnosticCode;
    }

    public String field() {
        return field;
    }

    public String task() {
        return task;
    }

    public String source() {
        return source;
    }

    public static int report(Throwable failure, PrintStream err, Instant timestamp, String commandId,
                             Locale locale, boolean json) {
        return report(failure, err, timestamp, commandId, locale, json, 1);
    }

    static int report(Throwable failure, PrintStream err, Instant timestamp, String commandId,
                      Locale locale, boolean json, long sequence) {
        StartException problem = switch (failure) {
            case StartException start -> start;
            case ParameterException parameter -> new StartException(2, message(parameter), parameter,
                    parameter.code(), parameter.parameterName(), null, null);
            case DefinitionException definition -> new StartException(2, message(definition), definition,
                    definition.code(), null, null,
                    definition.failureSource().map(position -> position.file() + ":" + position.line()).orElse(null));
            case GroovyDslException dsl -> new StartException(2, message(dsl), dsl,
                    dsl.code(), dsl.fieldName(), dsl.taskId(),
                    dsl.source() == null ? null : dsl.source().file() + ":" + dsl.source().line());
            default -> new StartException(40, Messages.format(locale, "cli.error.internal", message(failure)),
                    failure, "INTERNAL_ERROR", null, null, null);
        };
        print(problem, err, timestamp, commandId, locale, json, sequence);
        return problem.code();
    }

    private static void print(StartException exception, PrintStream err, Instant timestamp, String commandId,
                              Locale locale, boolean json, long sequence) {
        String message = message(exception);
        if (json) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("schema_version", 1);
            event.put("timestamp", timestamp.toString());
            event.put("sequence", sequence);
            event.put("event_id", commandId + ":" + sequence);
            event.put("level", "ERROR");
            event.put("event_name", "gretl.cli.error");
            event.put("message", message);
            event.put("command_id", commandId);
            event.put("exit_code", exception.code());
            event.put("diagnostic_code", exception.diagnosticCode());
            if (exception.field() != null) event.put("field", exception.field());
            if (exception.task() != null) event.put("task", exception.task());
            if (exception.source() != null) event.put("source", exception.source());
            err.println(Json.write(event));
        } else {
            Map<String, String> details = new LinkedHashMap<>();
            if (exception.field() != null && !exception.field().isBlank()) {
                details.put(Messages.get(locale, "log.field.field"), exception.field());
            }
            if (exception.task() != null && !exception.task().isBlank()) {
                details.put(Messages.get(locale, "log.field.task"), exception.task());
            }
            if (exception.source() != null && !exception.source().isBlank()) {
                details.put(Messages.get(locale, "log.field.source"), exception.source());
            }
            err.println(new TextFormatter(locale).formatProblem(timestamp,
                    exception.diagnosticCode(), null, message, details));
        }
    }

    public static String message(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getName() : message;
    }
}
