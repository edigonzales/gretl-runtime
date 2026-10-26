package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.SourceLocation;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Canonical GRETL JSON event envelope (not OTLP/JSON). */
public final class EventJson {
    public static final int SCHEMA_VERSION = 1;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    private EventJson() {
    }

    public static Map<String, Object> encode(LogEvent event, LogContext context,
                                             boolean includeFailureDetail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema_version", SCHEMA_VERSION);
        result.put("timestamp", TIMESTAMP.format(event.timestamp().atOffset(ZoneOffset.UTC)));
        result.put("sequence", event.sequence());
        result.put("event_id", event.eventId());
        result.put("level", event.level().name());
        result.put("event_name", event.eventName());
        result.put("message", event.message());
        result.put("command_id", event.commandId());
        result.put("locale", context.locale().toLanguageTag());
        if (event.attributes().containsKey("correlation.id")) {
            result.put("correlation_id", event.attributes().get("correlation.id"));
        }
        event.runId().ifPresent(value -> result.put("run_id", value.toString()));
        event.jobName().ifPresent(value -> result.put("job_name", value));
        event.taskId().ifPresent(value -> result.put("task_id", value));
        event.invocationId().ifPresent(value -> result.put("invocation_id", value));
        event.operationId().ifPresent(value -> result.put("operation_id", value));
        if (!event.phase().isBlank()) {
            result.put("phase", event.phase());
        }
        if (!event.outcome().isBlank()) {
            result.put("outcome", event.outcome());
        }
        if (event.durationMillis() > 0) {
            result.put("duration_ms", event.durationMillis());
        }
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("service.name", "gretl");
        resource.put("service.version", context.runtimeVersion());
        context.deploymentEnvironment().ifPresent(value ->
                resource.put("deployment.environment.name", value));
        result.put("resource", resource);
        event.source().ifPresent(source -> result.put("source", sourceMap(source)));
        if (!event.attributes().isEmpty()) {
            result.put("attributes", new LinkedHashMap<>(event.attributes()));
        }
        if (event.failure().isPresent()) {
            FailureInfo failure = event.failure().orElseThrow();
            if (includeFailureDetail) {
                result.put("error", failureMap(failure));
            } else {
                result.put("error_ref", failure.id());
            }
        }
        return result;
    }

    public static Map<String, Object> failureMap(FailureInfo failure) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", failure.id());
        result.put("type", failure.type());
        result.put("message", failure.message());
        failure.diagnostic().ifPresent(diagnostic -> {
            result.put("code", diagnostic.code());
            diagnostic.taskId().ifPresent(value -> result.put("task_id", value));
            diagnostic.field().ifPresent(value -> result.put("field", value));
            if (!diagnostic.available().isEmpty()) {
                result.put("allowed", diagnostic.available());
            }
            diagnostic.hint().ifPresent(value -> result.put("hint", value));
        });
        failure.source().ifPresent(source -> result.put("source", sourceMap(source)));
        if (!failure.frames().isEmpty()) {
            List<Map<String, Object>> frames = new ArrayList<>();
            for (FailureInfo.Frame frame : failure.frames()) {
                Map<String, Object> value = new LinkedHashMap<>();
                if (!frame.className().isBlank()) {
                    value.put("class", frame.className());
                }
                if (!frame.methodName().isBlank()) {
                    value.put("method", frame.methodName());
                }
                if (!frame.fileName().isBlank()) {
                    value.put("file", frame.fileName());
                }
                if (frame.lineNumber() > 0) {
                    value.put("line", frame.lineNumber());
                }
                frames.add(value);
            }
            result.put("frames", frames);
        }
        if (failure.framesTruncated()) {
            result.put("frames_truncated", true);
        }
        if (!failure.causes().isEmpty()) {
            result.put("causes", failure.causes().stream().map(EventJson::failureMap).toList());
        }
        if (!failure.suppressed().isEmpty()) {
            result.put("suppressed",
                    failure.suppressed().stream().map(EventJson::failureMap).toList());
        }
        if (failure.truncated()) {
            result.put("truncated", true);
        }
        return result;
    }

    public static Map<String, Object> sourceMap(SourceLocation source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("file", source.file());
        if (source.line() > 0) {
            result.put("line", source.line());
        }
        if (source.column() > 0) {
            result.put("column", source.column());
        }
        return result;
    }

    public static Optional<SourceLocation> sourceOf(LogEvent event) {
        return event.source().or(() -> event.failure().flatMap(FailureInfo::source));
    }
}
