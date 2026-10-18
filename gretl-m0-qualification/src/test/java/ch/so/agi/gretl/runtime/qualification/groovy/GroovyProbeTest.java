package ch.so.agi.gretl.runtime.qualification.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import groovy.lang.Closure;
import groovy.lang.GroovySystem;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * M0 Groovy probe. It is not the M2 DSL and does not use a Gradle plugin: the
 * production-side Groovy dependency is embedded and the external script is
 * compiled at test time by the real Groovy 6 compiler.
 */
class GroovyProbeTest {

    @TempDir
    Path tempDir;

    @Test
    void productRuntimeVersionsAreTheQualifiedOnes() {
        assertEquals("25", System.getProperty("java.specification.version"));
        assertEquals("6.0.0", GroovySystem.getVersion());
    }

    @Test
    void externalScriptUsesMapFirstCallsAndStoresClosuresForLaterEvaluation() throws Exception {
        Path script = copyProbe("m0-probe.groovy");
        M0ProbeScriptBase base = load(script);

        assertEquals(0, base.evaluationCount(), "load must not evaluate task closures");
        assertEquals(1, base.parameters().size());
        M0ProbeScriptBase.ParameterDeclaration parameter = base.parameters().get("quelle");
        assertNotNull(parameter);
        assertEquals(Boolean.TRUE, parameter.options().get("required"));
        assertEquals("input.txt", parameter.options().get("defaultValue"));
        assertFalse(((String) parameter.options().get("description")).isBlank());

        M0ProbeScriptBase.TaskDeclaration messen = base.tasks().get("messen");
        assertNotNull(messen);
        assertEquals("FileSize", messen.type());
        assertEquals("vorbereiten", messen.options().get("dependsOn"));
        assertNotEquals(Closure.DELEGATE_ONLY, messen.body().getResolveStrategy(),
                "the stored original closure must not be mutated");

        Map<String, Object> first = base.runTask("messen");
        assertEquals(1, base.evaluationCount());
        assertEquals("input.txt", first.get("input"));
        assertInstanceOf(String.class, first.get("note"));
        assertEquals("Datei: input.txt", first.get("note"));

        Map<String, Object> second = base.runTask("messen");
        assertEquals(2, base.evaluationCount());
        assertEquals("input.txt", second.get("input"));
        assertNotSame(first, second);

        base.setParameterValue("quelle", "second.txt");
        Map<String, Object> third = base.runTask("messen");
        assertEquals("second.txt", third.get("input"),
                "each invocation must read a fresh delegate scope and current parameter view");

        Map<String, Object> reported = base.runTask("melden");
        assertEquals("second.txt", reported.get("input"));
        assertEquals(Path.of("summary.txt"), reported.get("summary"));
        assertEquals(4, base.evaluationCount());
        assertEquals(java.util.List.of("melden"), base.defaultTargets());
    }

    @Test
    void changedExternalScriptIsReloadedAndExecutedWithoutRebuildingTheProduct() throws Exception {
        Path script = copyProbe("m0-probe.groovy");
        M0ProbeScriptBase before = load(script);
        assertEquals("input.txt", before.runTask("messen").get("input"));

        String changedSource = Files.readString(script, StandardCharsets.UTF_8)
                .replace("defaultValue: 'input.txt'", "defaultValue: 'changed.txt'");
        Files.writeString(script, changedSource, StandardCharsets.UTF_8);

        M0ProbeScriptBase after = load(script);
        assertNotSame(before.getClass(), after.getClass(),
                "a fresh Groovy class loader must compile the changed source");
        assertEquals("changed.txt", after.parameters().get("quelle").options().get("defaultValue"));
        assertEquals("changed.txt", after.runTask("messen").get("input"));
        assertEquals(1, after.evaluationCount());
    }

    @Test
    void unknownConfigurationFieldIsRejectedByDelegateOnlyScope() throws Exception {
        Path script = writeScript("""
                parameter('quelle', required: true, defaultValue: 'input.txt',
                          description: 'Quelldatei')
                task('messen', 'FileSize') {
                    unknownField = 'must fail'
                }
                defaults 'messen'
                """);
        M0ProbeScriptBase base = load(script);
        assertThrows(MissingPropertyException.class, () -> base.runTask("messen"));
    }

    @Test
    void closureWithTwoParametersIsRejected() throws Exception {
        Path script = writeScript("""
                parameter('quelle', required: true, defaultValue: 'input.txt',
                          description: 'Quelldatei')
                task('messen', 'FileSize') { a, b ->
                    input = a
                }
                defaults 'messen'
                """);
        M0ProbeScriptBase base = load(script);
        assertThrows(IllegalArgumentException.class, () -> base.runTask("messen"));
    }

    @Test
    void explicitZeroParameterClosureAndUnsetDefaultPropertyWork() throws Exception {
        Path script = writeScript("""
                parameter('quelle', required: true, defaultValue: 'input.txt',
                          description: 'Quelldatei')
                task('messen', 'FileSize') { ->
                    input = parameter('quelle')
                    summary = note
                }
                defaults 'messen'
                """);
        M0ProbeScriptBase base = load(script);
        Map<String, Object> values = base.runTask("messen");
        assertEquals("input.txt", values.get("input"));
        assertEquals("default-note", values.get("summary"));
    }

    private Path copyProbe(String resource) throws IOException {
        Path target = tempDir.resolve(resource);
        try (InputStream input = getClass().getResourceAsStream("/probes/" + resource)) {
            assertNotNull(input, "probe resource /probes/" + resource);
            Files.copy(input, target);
        }
        return target;
    }

    private Path writeScript(String source) throws IOException {
        Path target = tempDir.resolve("inline-" + System.nanoTime() + ".groovy");
        Files.writeString(target, source, StandardCharsets.UTF_8);
        return target;
    }

    private M0ProbeScriptBase load(Path script) throws IOException {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setScriptBaseClass(M0ProbeScriptBase.class.getName());
        configuration.setSourceEncoding(StandardCharsets.UTF_8.name());
        configuration.setTargetBytecode("25");
        Map<String, byte[]> compiledClasses = new HashMap<>();
        configuration.setBytecodePostprocessor((name, bytes) -> {
            compiledClasses.put(name, bytes);
            return bytes;
        });
        ImportCustomizer imports = new ImportCustomizer();
        imports.addStarImports("ch.so.agi.gretl.runtime.qualification.groovy");
        configuration.addCompilationCustomizers(imports);

        try (groovy.lang.GroovyClassLoader loader = new groovy.lang.GroovyClassLoader(
                getClass().getClassLoader(), configuration)) {
            groovy.lang.GroovyShell shell = new groovy.lang.GroovyShell(loader, new groovy.lang.Binding(), configuration);
            Script scriptInstance = shell.parse(script.toFile());
            assertTrue(scriptInstance instanceof M0ProbeScriptBase);
            assertEquals("25", configuration.getTargetBytecode());
            scriptInstance.run();
            assertJava25Class(compiledClasses.get(scriptInstance.getClass().getName()),
                    scriptInstance.getClass().getName());
            return (M0ProbeScriptBase) scriptInstance;
        }
    }

    private static void assertJava25Class(byte[] bytecode, String className) throws IOException {
        assertNotNull(bytecode, "compiled script bytecode: " + className);
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytecode))) {
            assertEquals(0xCAFEBABE, data.readInt());
            data.readUnsignedShort();
            assertEquals(69, data.readUnsignedShort(),
                    "Groovy script bytecode must target Java 25");
        }
    }
}
