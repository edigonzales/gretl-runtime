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

        boolean hasError = record.remove("error") != null;
        if (hasError) {
            record.put("error_ref", event.failure().orElseThrow().id());
        }
        String message = event.message();
        int end = Math.min(512, message.length());
        if (end < message.length() && Character.isHighSurrogate(message.charAt(end - 1))) {
            end--;
        }
        if (hasError || end < message.length()) {
            record.put("message", message.substring(0, end));
            bytes = line(record);
            if (bytes.length <= maxBytes) {
                return bytes;
            }
        }

        // Prefer a message-free record with the original task and operation identity.
        record.remove("resource");
        record.remove("source");
        record.put("message", "");
        bytes = line(record);
        if (bytes.length <= maxBytes) {
            return bytes;
        }

        record.keySet().retainAll(ENVELOPE);
        bytes = line(record);
        return bytes.length <= maxBytes ? bytes : null;
    }

    private static byte[] line(Map<String, Object> record) {
        return (Json.write(record) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
