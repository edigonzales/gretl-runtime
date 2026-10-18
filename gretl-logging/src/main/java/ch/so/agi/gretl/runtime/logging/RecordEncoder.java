package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.runner.FailureInfo;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Encodes one event as a single valid JSON line within the configured limit. */
final class RecordEncoder {
    private final LogContext context;

    RecordEncoder(LogContext context) {
        this.context = context;
    }

    Encoded encode(LogEvent event, int maxBytes, boolean includeFailureDetail) {
        Map<String, Object> map = EventJson.encode(event, context, includeFailureDetail);
        String json = Json.write(map);
        byte[] bytes = (json + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return new Encoded(bytes, false);
        }

        map.remove("attributes");
        if (Json.write(map).getBytes(StandardCharsets.UTF_8).length + 1 <= maxBytes) {
            return new Encoded((Json.write(map) + "\n").getBytes(StandardCharsets.UTF_8), true);
        }

        if (event.failure().isPresent()) {
            map.remove("error");
            map.put("error_ref", event.failure().orElseThrow().id());
            if (Json.write(map).getBytes(StandardCharsets.UTF_8).length + 1 <= maxBytes) {
                return new Encoded((Json.write(map) + "\n").getBytes(StandardCharsets.UTF_8), true);
            }
        }

        String message = event.message();
        int limit = 512;
        while (limit >= 0) {
            map.put("message", message.length() <= limit ? message : message.substring(0, limit));
            json = Json.write(map);
            bytes = (json + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes.length <= maxBytes) {
                return new Encoded(bytes, true);
            }
            limit /= 2;
        }

        Map<String, Object> minimal = new LinkedHashMap<>();
        minimal.put("schema_version", EventJson.SCHEMA_VERSION);
        minimal.put("timestamp", event.timestamp().toString());
        minimal.put("sequence", event.sequence());
        minimal.put("event_id", event.eventId());
        minimal.put("level", event.level().name());
        minimal.put("event_name", event.eventName());
        minimal.put("message", "");
        minimal.put("command_id", event.commandId());
        minimal.put("truncated", true);
        if (event.runId().isPresent()) {
            minimal.put("run_id", event.runId().orElseThrow().toString());
        }
        String minimalJson = Json.write(minimal);
        bytes = (minimalJson + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("maxRecordBytes too small for JSON envelope");
        }
        return new Encoded(bytes, true);
    }

    record Encoded(byte[] bytes, boolean truncated) {
    }
}
