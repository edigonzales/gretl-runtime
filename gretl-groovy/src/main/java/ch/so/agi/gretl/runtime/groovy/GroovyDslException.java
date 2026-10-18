package ch.so.agi.gretl.runtime.groovy;

import java.util.ArrayList;
import java.util.List;

/** Groovy DSL error with an optional task, field and original source position. */
public class GroovyDslException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String taskId;
    private final String fieldName;
    private final SourceLocation source;
    private final ArrayList<String> available;

    public GroovyDslException(String code, String message) {
        this(code, message, null, null, null, List.of(), null);
    }

    public GroovyDslException(String code, String message, Throwable cause) {
        this(code, message, null, null, null, List.of(), cause);
    }

    public GroovyDslException(
            String code, String message, String taskId, String fieldName,
            SourceLocation source, List<String> available, Throwable cause) {
        super(buildMessage(message, taskId, fieldName, source, available), cause);
        this.code = require(code, "code");
        this.taskId = taskId;
        this.fieldName = fieldName;
        this.source = source;
        this.available = new ArrayList<>(available);
    }

    public String code() {
        return code;
    }

    public String taskId() {
        return taskId;
    }

    public String fieldName() {
        return fieldName;
    }

    public SourceLocation source() {
        return source;
    }

    public List<String> available() {
        return List.copyOf(available);
    }

    private static String buildMessage(
            String message, String taskId, String fieldName,
            SourceLocation source, List<String> available) {
        StringBuilder result = new StringBuilder(message == null ? "Groovy DSL error" : message);
        if (taskId != null && !taskId.isBlank()) {
            result.append(" [task=").append(taskId).append(']');
        }
        if (fieldName != null && !fieldName.isBlank()) {
            result.append(" [field=").append(fieldName).append(']');
        }
        if (available != null && !available.isEmpty()) {
            result.append(" [erlaubt: ").append(String.join(", ", available)).append(']');
        }
        if (source != null) {
            result.append(" [").append(source).append(']');
        }
        return result.toString();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
