package ch.so.agi.gretl.runtime.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.ehi.basics.settings.Settings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.List;
import java.util.ServiceLoader;
import org.interlis2.validator.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * M0 A/B library loadability probe. It deliberately does not implement the
 * SqlExecutor or IliValidator task adapters; it performs the smallest real calls
 * needed to qualify driver/validator loading and behaviour.
 */
class LibraryAbiProbeTest {

    @TempDir
    Path tempDir;

    @Test
    void postgresqlDriverIsLoadableAndRegistersItsJdbcUrl() throws Exception {
        Class<?> driverClass = Class.forName("org.postgresql.Driver");
        Driver direct = (Driver) driverClass.getDeclaredConstructor().newInstance();
        assertTrue(direct.acceptsURL("jdbc:postgresql://127.0.0.1:5432/gretl_test"));
        assertEquals(42, direct.getMajorVersion());
        assertEquals("PostgreSQL JDBC Driver",
                direct.getClass().getPackage().getImplementationTitle());
        assertEquals("42.7.14", direct.getClass().getPackage().getImplementationVersion());

        List<Driver> drivers = ServiceLoader.load(Driver.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        assertTrue(drivers.stream().anyMatch(driver ->
                        driver.getClass().getName().equals("org.postgresql.Driver")),
                "pgJDBC must be registered through the JDBC service mechanism");

        Driver registered = DriverManager.getDriver("jdbc:postgresql://127.0.0.1:5432/gretl_test");
        assertNotNull(registered);
        assertEquals("org.postgresql.Driver", registered.getClass().getName());
        // No connection is opened: M0 qualifies loading/ABI, not a live database.
    }

    @Test
    void realValidatorCompilesLocalModelAndValidatesValidAndInvalidXtf() throws Exception {
        Path models = Files.createDirectory(tempDir.resolve("models"));
        Path data = Files.createDirectory(tempDir.resolve("data"));
        Files.writeString(models.resolve("GretlM0Fixture.ili"), model(), StandardCharsets.UTF_8);
        Path valid = data.resolve("valid.xtf");
        Path invalid = data.resolve("invalid.xtf");
        Files.writeString(valid, transfer(true), StandardCharsets.UTF_8);
        Files.writeString(invalid, transfer(false), StandardCharsets.UTF_8);

        Settings settings = new Settings();
        settings.setValue(Validator.SETTING_ILIDIRS, models.toString());

        java.io.ByteArrayOutputStream validatorOutput = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        java.io.PrintStream originalErr = System.err;
        java.util.Locale originalLocale = java.util.Locale.getDefault();
        System.setOut(new java.io.PrintStream(validatorOutput, true, StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(validatorOutput, true, StandardCharsets.UTF_8));
        java.util.Locale.setDefault(java.util.Locale.ENGLISH);
        boolean validResult;
        boolean invalidResult;
        try {
            validResult = Validator.runValidation(valid.toString(), settings);
            invalidResult = Validator.runValidation(invalid.toString(), settings);
        } finally {
            java.util.Locale.setDefault(originalLocale);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        String diagnostics = validatorOutput.toString(StandardCharsets.UTF_8);
        assertTrue(validResult, "valid local INTERLIS transfer must pass the real validator");
        assertFalse(invalidResult, "missing mandatory attribute must fail the real validator");
        assertTrue(diagnostics.contains("validation done"),
                "the real validator must report completion:\n" + diagnostics);
        assertTrue(diagnostics.contains("validation failed"),
                "the real validator must report the invalid transfer:\n" + diagnostics);
        assertTrue(diagnostics.contains("Attribute name requires a value"),
                "the real validator diagnostic text must be preserved:\n" + diagnostics);

        Class<?> transferDescription = Class.forName("ch.interlis.ili2c.metamodel.TransferDescription");
        assertNotNull(transferDescription);
        Class<?> ioxReader = Class.forName("ch.interlis.iox.IoxReader");
        assertNotNull(ioxReader);
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
