package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.SourceLocation;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Human-readable stderr representation; never an authoritative JSON channel. */
public final class TextFormatter {
    private final Locale locale;

    public TextFormatter() {
        this(Locale.GERMAN);
    }

    public TextFormatter(Locale locale) {
        this.locale = java.util.Objects.requireNonNull(locale, "locale");
    }

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'");

    public String format(LogEvent event) {
        if (event.level() == ch.so.agi.gretl.runtime.api.LogLevel.ERROR) {
            return formatErrorEvent(event);
        }
        StringBuilder out = new StringBuilder();
        out.append(timestamp(event.timestamp()))
                .append(' ').append(event.level()).append(' ')
                .append(title(event.eventName()))
                .append(" [").append(event.eventName()).append(']');
        event.taskId().ifPresent(value -> out.append(" task=").append(value));
        event.invocationId().ifPresent(value -> out.append(" invocation=").append(value));
        event.operationId().ifPresent(value -> out.append(" operation=").append(value));
        if (!event.message().isBlank()) {
            out.append(": ").append(event.message());
        }
        if (!event.phase().isBlank()) {
            out.append(" phase=").append(event.phase());
        }
        if (!event.outcome().isBlank()) {
            out.append(" outcome=").append(event.outcome());
        }
        if (event.durationMillis() > 0) {
            out.append(" duration_ms=").append(event.durationMillis());
        }
        if (!event.attributes().isEmpty()) {
            for (Map.Entry<String, String> entry : event.attributes().entrySet()) {
                out.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
            }
        }
        if (event.failure().isPresent()) {
            appendFailure(out, event.failure().orElseThrow(),
                    label("log.field.cause", "Ursache"), 0);
        }
        return out.toString();
    }

    /**
     * Formats a message that failed before the logging pipeline existed. The
     * layout intentionally mirrors {@link #format(LogEvent)} so users do not
     * have to switch mental models between early and runtime errors.
     */
    public String formatProblem(Instant timestamp, String code, String title,
                                String message, Map<String, String> details) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (message != null && !message.isBlank()) {
            fields.put(label("log.field.message", "Meldung"), message);
        }
        if (details != null) {
            details.forEach(fields::putIfAbsent);
        }
        StringBuilder out = new StringBuilder();
        out.append(timestamp(timestamp)).append(" ERROR ")
                .append(title == null || title.isBlank() ? title(code) : title)
                .append(" [").append(code).append(']');
        appendFields(out, fields);
        return out.toString();
    }

    private String formatErrorEvent(LogEvent event) {
        if ("gretl.run.finished".equals(event.eventName())) {
            return formatRunFinished(event);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        event.taskId().ifPresent(value ->
                fields.put(label("log.field.task", "Task"), value));
        event.operationId().ifPresent(value ->
                fields.put(label("log.field.operation", "Operation"), value));
        event.invocationId().ifPresent(value ->
                fields.put(label("log.field.invocation", "Aufruf"), value));
        FailureInfo failure = event.failure().orElse(null);
        if (failure != null) {
            failure.diagnostic().ifPresent(diagnostic -> {
                fields.put(label("log.field.code", "Code"), diagnostic.code());
                diagnostic.field().ifPresent(value ->
                        fields.put(label("log.field.field", "Feld"), value));
                if (!diagnostic.available().isEmpty()) {
                    fields.put(label("log.field.allowed", "Erlaubte Felder"),
                            String.join(", ", diagnostic.available()));
                }
                diagnostic.hint().ifPresent(value ->
                        fields.put(label("log.field.hint", "Hinweis"), value));
            });
        }
        String message = event.message().isBlank()
                ? (failure == null ? "" : failure.message())
                : event.message();
        if (!message.isBlank()) {
            fields.put(label("log.field.message", "Meldung"), message);
        }
        if (failure != null) {
            failure.source().ifPresent(value ->
                fields.put(label("log.field.source", "Quelle"), source(value)));
        }
        if (!event.phase().isBlank()) {
            fields.put(label("log.field.phase", "Phase"), event.phase());
        }
        if (!event.outcome().isBlank()) {
            fields.put(label("log.field.outcome", "Ergebnis"), event.outcome());
        }
        if (event.durationMillis() > 0) {
            fields.put(label("log.field.duration", "Dauer"), event.durationMillis() + " ms");
        }
        if (!event.attributes().isEmpty()) {
            event.attributes().forEach((key, value) -> fields.putIfAbsent(key, value));
        }
        StringBuilder out = new StringBuilder();
        out.append(timestamp(event.timestamp())).append(" ERROR ")
                .append(title(event.eventName()))
                .append(" [").append(event.eventName()).append(']');
        appendFields(out, fields);
        if (failure != null) {
            for (FailureInfo cause : failure.causes()) {
                appendFailure(out, cause, label("log.field.furtherCause", "Weitere Ursache"), 0);
            }
            for (FailureInfo suppressed : failure.suppressed()) {
                appendFailure(out, suppressed, label("log.field.suppressed", "Unterdrueckte Ausnahme"), 0);
            }
        }
        return out.toString();
    }

    private String formatRunFinished(LogEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        FailureInfo failure = event.failure().orElse(null);
        if (failure != null) {
            failure.diagnostic().ifPresent(diagnostic -> {
                fields.put(label("log.field.code", "Code"), diagnostic.code());
                diagnostic.taskId().ifPresent(value -> fields.put("Task", value));
            });
        }
        if (!event.outcome().isBlank()) {
            fields.put(label("log.field.outcome", "Ergebnis"), event.outcome());
        }
        if (event.durationMillis() > 0) {
            fields.put(label("log.field.duration", "Dauer"), event.durationMillis() + " ms");
        }
        event.attributes().forEach((key, value) -> fields.putIfAbsent(key, value));
        StringBuilder out = new StringBuilder();
        out.append(timestamp(event.timestamp())).append(" ERROR ")
                .append(title(event.eventName()))
                .append(" [").append(event.eventName()).append(']');
        appendFields(out, fields);
        return out.toString();
    }

    private void appendFields(StringBuilder out, Map<String, String> fields) {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            out.append('\n').append("  ").append(entry.getKey()).append(": ")
                    .append(entry.getValue());
        }
    }

    private void appendFailure(StringBuilder out, FailureInfo failure, String label, int depth) {
        out.append('\n').append("  ".repeat(depth + 1)).append(label).append(": ");
        String message = failure.message();
        if (message == null || message.isBlank()) {
            out.append(simpleType(failure.type()));
        } else {
            out.append(message).append(" (").append(simpleType(failure.type())).append(')');
        }
        failure.diagnostic().ifPresent(diagnostic ->
                out.append(" [").append(diagnostic.code()).append(']'));
        if (failure.source().isPresent()) {
            out.append(" [").append(source(failure.source().orElseThrow())).append(']');
        }
        if (failure.truncated() || failure.framesTruncated()) {
            out.append(" [gekuerzt]");
        }
        for (FailureInfo cause : failure.causes()) {
            appendFailure(out, cause, label("log.field.furtherCause", "Weitere Ursache"), depth + 1);
        }
        for (FailureInfo suppressed : failure.suppressed()) {
            appendFailure(out, suppressed, label("log.field.suppressed", "Unterdrueckte Ausnahme"), depth + 1);
        }
    }

    private String timestamp(Instant instant) {
        return TIMESTAMP.format(instant.atOffset(ZoneOffset.UTC));
    }

    private String title(String eventName) {
        String key = "log.title." + eventName;
        if (Messages.contains(locale, key)) {
            return Messages.get(locale, key);
        }
        return eventName;
    }

    private String label(String key, String fallback) {
        return Messages.contains(locale, key) ? Messages.get(locale, key) : fallback;
    }

    private static String source(SourceLocation location) {
        StringBuilder value = new StringBuilder(location.file())
                .append(':').append(location.line());
        if (location.column() > 0) {
            value.append(':').append(location.column());
        }
        return value.toString();
    }

    private static String simpleType(String type) {
        if (type == null || type.isBlank()) {
            return "Fehler";
        }
        int dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }
}
