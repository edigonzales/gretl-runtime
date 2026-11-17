package ch.so.agi.gretl.runtime.logging;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/** Writes the richest candidate that fits the JSONL record limit. */
final class RecordEncoder {
    private static final Set<String> ENVELOPE = Set.of("schema_version", "timestamp",
            "sequence", "event_id", "level", "event_name", "message", "command_id", "run_id",
            "truncated");

    private RecordEncoder() {
    }

    static byte[] encode(LogEvent event, LogContext context, int maxBytes) {
        Map<String, Object> record = EventJson.encode(event, context);
        byte[] bytes = line(record);
        if (bytes.length <= maxBytes) {
            return bytes;
        }

        record.put("truncated", true);
        if (record.remove("attributes") != null) {
            bytes = line(record);
            if (bytes.length <= maxBytes) {
                return bytes;
            }
        }

        record.remove("error");
        event.failure().ifPresent(failure -> record.put("error_ref", failure.id()));
        String message = event.message();
        int end = Math.min(512, message.length());
        if (end < message.length() && Character.isHighSurrogate(message.charAt(end - 1))) {
            end--;
        }
        record.put("message", message.substring(0, end));
        bytes = line(record);
        if (bytes.length <= maxBytes) {
            return bytes;
        }

        record.keySet().retainAll(ENVELOPE);
        record.put("message", "");
        bytes = line(record);
        return bytes.length <= maxBytes ? bytes : null;
    }

    private static byte[] line(Map<String, Object> record) {
        return (Json.write(record) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
