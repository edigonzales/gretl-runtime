package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.SecretRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared redaction stage used before every managed text/JSON sink. */
public final class Redactor {
    private static final String MASK = "[REDACTED]";
    private static final List<String> SENSITIVE_KEY_PARTS = List.of(
            "password", "passwd", "authorization", "cookie", "token",
            "credential", "secret", "apikey", "api_key");

    private static final List<String> SAFE_ATTRIBUTE_KEYS = List.of(
            "operation.id", "operation.symbol", "event_name", "logger");

    private final SecretRegistry secrets;

    public Redactor(SecretRegistry secrets) {
        this.secrets = java.util.Objects.requireNonNull(secrets, "secrets");
    }

    public String redact(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String result = value;
        List<String> known = new ArrayList<>(secrets.values());
        known.sort((left, right) -> Integer.compare(right.length(), left.length()));
        for (String secret : known) {
            if (!secret.isEmpty()) {
                result = result.replace(secret, MASK);
            }
        }
        return result;
    }

    public Map<String, String> redactFields(Map<String, String> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        fields.forEach((key, value) -> {
            if (SAFE_ATTRIBUTE_KEYS.contains(key)) {
                result.put(key, value);
            } else if (isSensitiveKey(key)) {
                result.put(key, MASK);
            } else {
                result.put(key, redact(value));
            }
        });
        return result;
    }

    public FailureInfo redact(FailureInfo failure) {
        if (failure == null) {
            return null;
        }
        List<FailureInfo> causes = failure.causes().stream().map(this::redact).toList();
        List<FailureInfo> suppressed = failure.suppressed().stream()
                .map(this::redact).toList();
        java.util.Optional<ch.so.agi.gretl.runtime.api.DiagnosticInfo> diagnostic =
                failure.diagnostic().map(value ->
                        new ch.so.agi.gretl.runtime.api.DiagnosticInfo(
                                value.code(),
                                value.taskId().map(this::redact),
                                value.field().map(this::redact),
                                value.available().stream().map(this::redact).toList(),
                                value.hint().map(this::redact)));
        return new FailureInfo(failure.id(), failure.type(), redact(failure.message()),
                causes, suppressed, failure.truncated(), failure.frames(),
                failure.framesTruncated(), failure.source(), diagnostic);
    }

    public List<FailureInfo> redactFailures(List<FailureInfo> failures) {
        return failures.stream().map(this::redact).toList();
    }

    public String redactWithKeys(String key, String value) {
        return isSensitiveKey(key) ? MASK : redact(value);
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }
}
