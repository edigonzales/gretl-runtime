package ch.so.agi.gretl.tasks.ilivalidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.ehi.basics.logging.EhiLogger;
import ch.so.agi.gretl.runtime.api.OperationProvider;
import ch.so.agi.gretl.runtime.api.OperationType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IliValidatorOperationTest {
    @TempDir
    Path tempDir;

    @Test
    void validatesValidAndInvalidTransferWithProtectedReportAndCleanup() throws Exception {
        Path models = Files.createDirectories(tempDir.resolve("models"));
        Files.writeString(models.resolve("GretlM0Fixture.ili"), model());
        Path valid = tempDir.resolve("valid.xtf");
        Path invalid = tempDir.resolve("invalid.xtf");
        Files.writeString(valid, transfer(true));
        Files.writeString(invalid, transfer(false));

        TestContext context = new TestContext(tempDir.resolve("work"));
        EhiLogger logger = EhiLogger.getInstance();
        int before = listenerCount(logger);
        ValidationResult result = new IliValidatorOperation.IliValidatorHandler()
                .execute(request(valid, models), context);
        assertEquals(0, result.warnings());
        assertEquals(valid.toAbsolutePath().normalize(), result.file());
        assertTrue(Files.isRegularFile(result.reportFile()));
        assertTrue(Files.readString(result.reportFile()).contains("events="));
        assertFalse(context.diagnostics().isEmpty());
        assertTrue(context.diagnostics().get(0).artifacts().get(0).restricted());
        assertEquals(before, listenerCount(logger));

        TestContext invalidContext = new TestContext(tempDir.resolve("invalid-work"));
        IliValidatorTaskException exception = assertThrows(IliValidatorTaskException.class,
                () -> new IliValidatorOperation.IliValidatorHandler()
                        .execute(request(invalid, models), invalidContext));
        assertTrue(exception.errors() > 0);
        assertTrue(exception.reportFile().isPresent());
        assertTrue(Files.isRegularFile(exception.reportFile().orElseThrow()));
        assertTrue(Files.readString(exception.reportFile().orElseThrow())
                .contains("Attribute name requires a value"));
        assertTrue(invalidContext.diagnostics().get(0).severity()
                == ch.so.agi.gretl.runtime.api.DiagnosticSeverity.ERROR);
        assertEquals(before, listenerCount(logger));
    }

    @Test
    void missingDataModelAndConfigAreSafeErrors() {
        Path models = tempDir.resolve("models");
        try {
            Files.createDirectories(models);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        TestContext context = new TestContext(tempDir.resolve("work"));
        assertThrows(IliValidatorTaskException.class, () -> new IliValidatorOperation
                .IliValidatorHandler().execute(request(tempDir.resolve("missing.xtf"), models),
                        context));
        assertThrows(IliValidatorTaskException.class, () -> new IliValidatorOperation
                .IliValidatorHandler().execute(request(tempDir.resolve("a.xtf"),
                        tempDir.resolve("missing-models")), context));
    }

    @Test
    void providerAndMetadataAreSharedWithHelpAndDsl() {
        List<OperationProvider> providers = ServiceLoader.load(
                OperationProvider.class, getClass().getClassLoader()).stream()
                .map(ServiceLoader.Provider::get).toList();
        assertTrue(providers.stream().anyMatch(provider ->
                provider instanceof IliValidatorProvider));
        OperationType<?, ?> type = IliValidatorOperation.type();
        assertEquals("IliValidator", type.symbol());
        assertEquals("gretl.ilivalidator", type.id());
        assertTrue(type.inputs().stream().anyMatch(field -> field.name().equals("dataFile")));
        assertTrue(type.outputs().stream().anyMatch(field -> field.name().equals("reportFile")));
        assertTrue(type.example().contains("IliValidator"));
    }

    private static IliValidatorOperation.IliValidatorRequest request(Path data, Path models) {
        return new IliValidatorOperation.IliValidatorRequest(data, models, null);
    }

    private static int listenerCount(EhiLogger logger) throws Exception {
        var field = EhiLogger.class.getDeclaredField("logListenerv");
        field.setAccessible(true);
        return ((Set<?>) field.get(logger)).size();
    }

    private static String model() {
        return """
                INTERLIS 2.3;
                MODEL GretlM0Fixture (en)
                  AT "mailto:test@example.invalid"
                  VERSION "2026-10-18" =
                  TOPIC Site =
                    CLASS Punkt =
                      name : MANDATORY TEXT*50;
                      value : 0.0 .. 1000.0;
                    END Punkt;
                  END Site;
                END GretlM0Fixture.
                """;
    }

    private static String transfer(boolean withName) {
        String name = withName ? "<name>Äpfel &amp; Öl</name>" : "";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                <HEADERSECTION SENDER="gretl-m0" VERSION="2.3">
                <MODELS><MODEL NAME="GretlM0Fixture" VERSION="2026-10-18" URI="https://example.invalid"></MODEL></MODELS>
                </HEADERSECTION>
                <DATASECTION>
                <GretlM0Fixture.Site BID="b1">
                <GretlM0Fixture.Site.Punkt TID="p1">
                %s
                <value>12.5</value>
                </GretlM0Fixture.Site.Punkt>
                </GretlM0Fixture.Site>
                </DATASECTION>
                </TRANSFER>
                """.formatted(name);
    }
}
