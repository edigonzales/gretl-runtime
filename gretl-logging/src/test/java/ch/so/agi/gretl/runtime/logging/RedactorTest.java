package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.SecretRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedactorTest {
    @Test
    void masksKnownSecretsLongestFirstAndSensitiveFields() {
        SecretRegistry registry = new SecretRegistry();
        registry.register("short");
        registry.register("longer-secret");
        Redactor redactor = new Redactor(registry);

        assertEquals("x [REDACTED] y [REDACTED] z",
                redactor.redact("x longer-secret y short z"));
        assertEquals("[REDACTED]", redactor.redactWithKeys("Authorization", "Bearer value"));
        assertEquals("[REDACTED]",
                redactor.redactFields(Map.of("password", "value", "safe", "ok")).get("password"));
        assertEquals("ok",
                redactor.redactFields(Map.of("safe", "ok")).get("safe"));
        assertEquals("gretl.sql-executor",
                redactor.redactFields(Map.of("operation.id", "gretl.sql-executor"))
                        .get("operation.id"));
    }

    @Test
    void redactsFailureMessagesRecursivelyWithoutLosingCauseGraph() {
        SecretRegistry registry = new SecretRegistry();
        registry.register("top-secret");
        Redactor redactor = new Redactor(registry);
        FailureInfo info = FailureInfo.from(
                new IllegalStateException("outer top-secret",
                        new IllegalArgumentException("inner top-secret")));

        FailureInfo redacted = redactor.redact(info);
        assertFalse(redacted.message().contains("top-secret"));
        assertEquals(1, redacted.causes().size());
        assertFalse(redacted.causes().get(0).message().contains("top-secret"));
        assertTrue(redacted.message().contains("[REDACTED]"));
    }
}
