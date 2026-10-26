package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Independent component qualification, explicitly separate from JAR job evidence. */
class ValidatorClasspathLocalJobIT {
    private static Path classes;
    private static String classpath;
    @BeforeAll static void compileProbeAgainstOnlyTheTaskRuntimeGraph() throws Exception {
        Path root = ArtifactJobRunner.directory("minimal-validator-compiler");
        classes = root.resolve("probe classes with spaces");
        Files.createDirectories(classes);
        Path artifact = Path.of("../gretl-interlis-tasks/target/gretl-interlis-tasks-0.1.0-SNAPSHOT.jar").toAbsolutePath().normalize();
        String dependencies = Files.readString(Path.of("../gretl-interlis-tasks/target/runtime-classpath.txt")).strip();
        // This is the module's own resolved runtime graph, not a filtered full distribution.
        classpath = artifact + java.io.File.pathSeparator + dependencies;
        assertFalse(classpath.contains("custom-functions"));
        assertFalse(classpath.contains("geow-interlis-functions"));
        assertFalse(classpath.contains("interlis-functions-ngk"));
        assertFalse(classpath.contains("standard-functions"));
        assertFalse(classpath.contains("gretl-cli"));
        assertFalse(classpath.contains("gretl-packager"));
        Path source = Path.of(ValidatorClasspathLocalJobIT.class.getResource("/probes/MinimalFormatValidation.java").toURI());
        run(List.of(tool("javac"), "-encoding", "UTF-8", "-cp", classpath, "-d", classes.toString(), source.toString()), root, "compile");
        Files.writeString(root.resolve("component-classpath.txt"), classpath);
        classpath = classes + java.io.File.pathSeparator + classpath;
    }

    @ParameterizedTest @CsvSource({"csv,CsvValidator,data1.csv", "json,JsonValidatorOk,unused",
            "gpkg,GpkgValidator,unused", "shp,ShpValidator,unused"})
    void validatesAllFourFormatsWithoutOrganizationBundles(String format, String variant, String file) throws Exception {
        Path root = ArtifactJobRunner.directory("minimal-" + format);
        Path log = root.resolve("output/validation.log");
        int exit = run(List.of(tool("java"), "-cp", classpath, "probe.MinimalFormatValidation", format,
                ArtifactJobRunner.fixture("validators").resolve(variant).toString(), log.toString(), file), root, "run");
        assertEquals(0, exit, Files.readString(root.resolve("run.stderr.log")));
        assertTrue(Files.readString(root.resolve("run.stdout.log")).contains("minimal-validation=true"));
        assertTrue(Files.readString(log).contains("validation done"));
    }

    @Test void businessNegativeStillNeedsItsConcreteDiagnostics() throws Exception {
        Path root = ArtifactJobRunner.directory("minimal-invalid-csv");
        Path log = root.resolve("output/validation.log");
        int exit = run(List.of(tool("java"), "-cp", classpath, "probe.MinimalFormatValidation", "csv",
                ArtifactJobRunner.fixture("validators").resolve("CsvValidatorFail").toString(), log.toString(), "dataFail.csv"), root, "run");
        assertEquals(10, exit, Files.readString(root.resolve("run.stderr.log")));
        String diagnostic = Files.readString(log);
        assertTrue(diagnostic.contains("value <x> is not a number"), diagnostic);
        assertTrue(diagnostic.contains("value gruen is not a member of the enumeration"), diagnostic);
    }

    private static String tool(String name) { return Path.of(System.getProperty("java.home"), "bin", name).toString(); }
    private static int run(List<String> args, Path root, String name) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(args).directory(root.toFile());
        for (String key : List.of("CLASSPATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS")) builder.environment().remove(key);
        Process process = builder.redirectOutput(root.resolve(name + ".stdout.log").toFile())
                .redirectError(root.resolve(name + ".stderr.log").toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Component process timed out");
            if (name.equals("compile")) assertEquals(0, process.exitValue(), Files.readString(root.resolve(name + ".stderr.log")));
            return process.exitValue();
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
        }
    }
}
