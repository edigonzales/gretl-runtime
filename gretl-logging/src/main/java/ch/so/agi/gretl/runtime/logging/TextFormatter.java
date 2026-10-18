package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.SourceLocation;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Human-readable stderr representation; never an authoritative JSON channel. */
public final class TextFormatter {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'");

    private static final Pattern FIELD = Pattern.compile("\\s*\\[field=([^\\]]+)\\]");
    private static final Pattern TASK = Pattern.compile("\\s*\\[task=([^\\]]+)\\]");
    private static final Pattern ALLOWED = Pattern.compile("\\s*\\[erlaubt: ([^\\]]+)\\]");
    private static final Pattern GROOVY_SOURCE =
            Pattern.compile("\\s*\\[[^\\]]+\\.groovy:\\d+(?::\\d+)?\\]");

    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("gretl.run.started", "Lauf gestartet"),
            Map.entry("gretl.run.finished", "Lauf beendet"),
            Map.entry("gretl.task.started", "Task gestartet"),
            Map.entry("gretl.task.finished", "Task abgeschlossen"),
            Map.entry("gretl.task.skipped", "Task uebersprungen"),
            Map.entry("gretl.task.failed", "Task fehlgeschlagen"),
            Map.entry("gretl.operation.started", "Operation gestartet"),
            Map.entry("gretl.operation.finished", "Operation abgeschlossen"),
            Map.entry("gretl.operation.failed", "Operation fehlgeschlagen"),
            Map.entry("gretl.operation.cancelled", "Operation abgebrochen"),
            Map.entry("gretl.operation.progress", "Fortschritt"),
            Map.entry("gretl.script.message", "Skriptmeldung"),
            Map.entry("gretl.cleanup.started", "Aufraeumen gestartet"),
            Map.entry("gretl.cleanup.finished", "Aufraeumen abgeschlossen"),
            Map.entry("gretl.http.download", "HTTP-Download abgeschlossen"),
            Map.entry("gretl.http.failed", "HTTP-Download fehlgeschlagen"),
            Map.entry("gretl.sql.finished", "SQL-Ausfuehrung abgeschlossen"),
            Map.entry("gretl.ilivalidator.finished", "Validierung abgeschlossen"),
            Map.entry("gretl.ilivalidator.failed", "Validierung fehlgeschlagen"),
            Map.entry("CLI_ERROR", "CLI-Fehler"),
            Map.entry("REPORT_WRITE_FAILED", "Runbericht nicht schreibbar"),
            Map.entry("PARAMETER_UNKNOWN", "Parameter unbekannt"),
            Map.entry("UNKNOWN_PARAMETER", "Parameter unbekannt"),
            Map.entry("UNKNOWN_OPTION", "Option unbekannt"),
            Map.entry("INVALID_OPTION", "Option ungueltig"),
            Map.entry("MISSING_OPTION", "Option fehlt"),
            Map.entry("UNKNOWN_OPERATION", "Taskart unbekannt"),
            Map.entry("MISSING_CONFIGURATION", "Konfiguration fehlt"),
            Map.entry("UNKNOWN_PROPERTY", "Feld unbekannt"),
            Map.entry("UNKNOWN_HELPER", "Hilfsfunktion unbekannt"));

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
            appendFailure(out, event.failure().orElseThrow(), "Ursache", 0);
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
            fields.put("Meldung", cleanMessage(message));
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
        Map<String, String> fields = new LinkedHashMap<>();
        event.taskId().ifPresent(value -> fields.put("Task", value));
        event.operationId().ifPresent(value -> fields.put("Operation", value));
        event.invocationId().ifPresent(value -> fields.put("Aufruf", value));
        if (!event.message().isBlank()) {
            fields.put("Meldung", cleanMessage(event.message()));
            addMessageDetails(fields, event.message());
        }
        event.failure().ifPresent(failure -> {
            String raw = fields.containsKey("Meldung")
                    ? event.message() : failure.message();
            if (!raw.isBlank()) {
                fields.put("Meldung", cleanMessage(raw));
                addMessageDetails(fields, raw);
            }
            failure.source().ifPresent(value -> fields.put("Quelle", source(value)));
        });
        if (!event.phase().isBlank()) {
            fields.put("Phase", event.phase());
        }
        if (!event.outcome().isBlank()) {
            fields.put("Ergebnis", event.outcome());
        }
        if (event.durationMillis() > 0) {
            fields.put("Dauer", event.durationMillis() + " ms");
        }
        if (!event.attributes().isEmpty()) {
            event.attributes().forEach((key, value) ->
                    fields.putIfAbsent(key, value));
        }
        StringBuilder out = new StringBuilder();
        out.append(timestamp(event.timestamp())).append(" ERROR ")
                .append(title(event.eventName()))
                .append(" [").append(event.eventName()).append(']');
        appendFields(out, fields);
        event.failure().ifPresent(failure -> {
            for (FailureInfo cause : failure.causes()) {
                appendFailure(out, cause, "Weitere Ursache", 0);
            }
            for (FailureInfo suppressed : failure.suppressed()) {
                appendFailure(out, suppressed, "Unterdrueckte Ausnahme", 0);
            }
        });
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
        String message = cleanMessage(failure.message());
        if (message == null || message.isBlank()) {
            out.append(simpleType(failure.type()));
        } else {
            out.append(message).append(" (").append(simpleType(failure.type())).append(')');
        }
        if (failure.source().isPresent()) {
            out.append(" [").append(source(failure.source().orElseThrow())).append(']');
        }
        if (failure.truncated() || failure.framesTruncated()) {
            out.append(" [gekuerzt]");
        }
        for (FailureInfo cause : failure.causes()) {
            appendFailure(out, cause, "Weitere Ursache", depth + 1);
        }
        for (FailureInfo suppressed : failure.suppressed()) {
            appendFailure(out, suppressed, "Unterdrueckte Ausnahme", depth + 1);
        }
    }

    private String timestamp(Instant instant) {
        return TIMESTAMP.format(instant.atOffset(ZoneOffset.UTC));
    }

    private String title(String eventName) {
        return TITLES.getOrDefault(eventName, eventName);
    }

    private static void addMessageDetails(Map<String, String> fields, String message) {
        String field = group(FIELD, message);
        if (field != null) {
            fields.putIfAbsent("Feld", field);
        }
        String allowed = group(ALLOWED, message);
        if (allowed != null) {
            fields.putIfAbsent("Hilfe", "Erlaubte Felder: " + allowed);
        }
    }

    private static String cleanMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String cleaned = TASK.matcher(message).replaceAll("");
        cleaned = FIELD.matcher(cleaned).replaceAll("");
        cleaned = ALLOWED.matcher(cleaned).replaceAll("");
        cleaned = GROOVY_SOURCE.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    private static String group(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
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
