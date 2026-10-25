package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Explicit JAR packaging qualification, additional to the common backend-neutral business suite. */
class SelectedPackageLocalJobIT {
    @Test void ioOnlyPackageRunsDownloadArchiveAndClasspathXsltOutsideCheckout() throws Exception {
        byte[] payload = Files.readAllBytes(ArtifactJobRunner.fixture("io").resolve("payload.txt"));
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/download", exchange -> { exchange.sendResponseHeaders(200, payload.length); exchange.getResponseBody().write(payload); exchange.close(); }); server.start();
        try {
            Path root = ArtifactJobRunner.directory("selected-io"); Path source = SelectedPackageSupport.fixture(root, "io", Map.of(), List.of("io"), false);
            var result = SelectedPackageSupport.detached(new ArtifactJobRunner.Request("selected-io", source, root, List.of("download", "extract", "xsl"),
                    Map.of("output", root.resolve("output"), "variant", "resource", "url", "http://127.0.0.1:" + server.getAddress().getPort() + "/download"),
                    Map.of(), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(90)));
            assertEquals(0, result.code(), result.err());
            assertArrayEquals(payload, Files.readAllBytes(root.resolve("output/download.bin")));
            assertArrayEquals(payload, Files.readAllBytes(root.resolve("output/nested/payload.txt")));
            String text = Files.readString(root.resolve("output/MeldungAnGeometer_G-0098981_20230214_104054_Koordinaten.xtf"));
            assertTrue(text.contains("<Grundstuecksnummer>1505</Grundstuecksnummer>"));
            assertTrue(text.contains("<Gebaeudebezeichnung>Reine Wohngebäude (Wohnnutzung ausschliesslich)</Gebaeudebezeichnung>"));
        } finally { server.stop(0); }
    }
    @Test void interlisOnlyPackageRunsFormatNativeGeoPackageAndSelectedFunctionWithoutBuildEnvironment() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/exists", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); }); server.start();
        try {
            Path root = ArtifactJobRunner.directory("selected-interlis");
            Path source = SelectedPackageSupport.fixture(root, "packages/interlis", Map.of(
                    "ili/Ili2gpkgImport", "original-gpkg", "ili/models", "models", "formats/Csv2Excel", "original-csv",
                    "functions/FunctionProbe.ili", "FunctionProbe.ili", "functions/SO_FunctionsExt.ili", "SO_FunctionsExt.ili"), List.of("interlis"), true);
            var result = SelectedPackageSupport.detached(new ArtifactJobRunner.Request("selected-interlis", source, root, List.of(),
                    Map.of("output", root.resolve("output"), "url", "http://127.0.0.1:" + server.getAddress().getPort() + "/exists"),
                    Map.of(), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(120)));
            assertEquals(0, result.code(), result.err()); assertTrue(calls.get() > 0);
            assertTrue(Files.readString(root.resolve("output/validation.log")).contains("validation done"));
            try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + root.resolve("output/result.gpkg")); var statement = connection.createStatement();
                 var rows = statement.executeQuery("select aname from nachfuehrungsgeometer")) {
                Set<String> names = new HashSet<>(); while (rows.next()) names.add(rows.getString(1));
                assertEquals(Set.of("Kägi", "Cantaluppi", "Schor", "Meile", "Weber"), names);
            }
            try (var input = Files.newInputStream(root.resolve("output/converted.xlsx")); var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(input)) {
                assertEquals(308, workbook.getSheetAt(0).getLastRowNum()); assertEquals(7, workbook.getSheetAt(0).getRow(0).getLastCellNum());
            }
        } finally { server.stop(0); }
    }
    @Test void externalDependencyIsResolvedBeforeCompilationAndHostClasspathCannotSupplyItImplicitly() throws Exception {
        Path root = ArtifactJobRunner.directory("selected-external");
        var external = new ExternalPackageFixture(root);
        Path pollution = external.pollution();
        assertThrows(ClassNotFoundException.class, () -> Class.forName("external.probe.ExampleProvider", false, getClass().getClassLoader()));
        assertFalse(Files.exists(Path.of(System.getProperty("user.home"), ".m2/repository", external.group.replace('.', '/'), "external-file-size")));
        Path source = Files.createDirectories(root.resolve("external source with spaces"));
        Files.writeString(source.resolve("data.txt"), "12345");
        Files.writeString(source.resolve("job.groovy"), """
                import external.probe.ExampleProvider.Result
                def marker = System.getProperty('external.marker')
                if (marker) new java.io.File(marker).text = 'packaging-executed-job'
                parameter('output', type: 'path', required: true, description: 'Result directory')
                task('size', FileSize) { input = file('data.txt') }
                action('record', dependsOn: 'size') {
                    Result measured = new Result(result('size').bytes)
                    java.nio.file.Files.writeString(parameter('output').resolve('bytes.txt'), measured.bytes().toString())
                    java.nio.file.Files.writeString(parameter('output').resolve('extension.txt'), 'bytes'.stamp())
                }
                defaults 'record'
                """);
        SelectedPackageSupport.descriptor(source, List.of(), false, List.of());
        var missing = external.pack(source.resolve("job-package.json"), root.resolve("missing.jar"), "missing-declaration",
                List.of(external.provider, external.helper, pollution), false);
        assertEquals(2, missing.code(), missing.err());
        assertTrue(missing.err().contains("PACKAGE_COMPILE_FAILED"), missing.err());
        assertTrue(missing.err().contains("external.probe.ExampleProvider.Result"), missing.err());
        assertFalse(Files.exists(root.resolve("missing.jar"))); assertFalse(Files.exists(root.resolve("must-not-execute")));
        SelectedPackageSupport.descriptor(source, List.of(), false, List.of(external.coordinate()));
        Path jar = root.resolve("job.jar");
        var clean = external.pack(source.resolve("job-package.json"), jar, "clean", List.of(), false);
        assertEquals(0, clean.code(), clean.err());
        var polluted = external.pack(source.resolve("job-package.json"), root.resolve("polluted.jar"), "polluted",
                List.of(external.provider, external.helper, pollution), false);
        assertEquals(0, polluted.code(), polluted.err());
        assertEquals(inventory(jar), inventory(root.resolve("polluted.jar")));
        assertFalse(Files.exists(root.resolve("must-not-execute")));
        try (var archive = new java.util.jar.JarFile(jar.toFile())) {
            assertNotNull(archive.getJarEntry("external/probe/ExampleProvider.class"));
            assertNotNull(archive.getJarEntry("external/dependency/ProbeHelper.class"));
            assertNull(archive.getJarEntry("external/unrelated/NotSelected.class"));
        }
        String inputs = Json.write(inventory(jar)); assertTrue(inputs.contains(external.coordinate())); assertTrue(inputs.contains(external.helperCoordinate()));
        PackageArtifactAssertions.verify(jar, source.resolve("job-package.json"));
        var request = new ArtifactJobRunner.Request("selected-external", source, root, List.of(), Map.of("output", root.resolve("output")),
                Map.of(), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(45));
        var prepared = new ArtifactJobRunner.Prepared(request, source, jar, hash(jar), root.resolve("reports/report.json"), null);
        var result = SelectedPackageSupport.detached(prepared);
        assertEquals(0, result.code(), result.err()); assertEquals("5", Files.readString(root.resolve("output/bytes.txt")));
        assertTrue(Files.readString(result.report()).contains("example.file-size"));
        assertEquals("bytes-" + external.nonce, Files.readString(root.resolve("output/extension.txt")));
    }

    @Test void unknownArtifactsVersionConflictsAndResourceConflictsHavePackagingDiagnostics() throws Exception {
        Path root = ArtifactJobRunner.directory("selected-packaging-errors"); var external = new ExternalPackageFixture(root);
        Path source = SelectedPackageSupport.fixture(root, "packages/action", Map.of(), List.of(), false);
        for (var entry : Map.of("ch.so.agi.gretl:gretl-api:99.0", "PACKAGE_DEPENDENCY_CONFLICT",
                external.group + ":missing-artifact:9.9", "PACKAGE_DEPENDENCY_RESOLVE").entrySet()) {
            SelectedPackageSupport.descriptor(source, List.of(), false, List.of(entry.getKey()));
            var result = external.pack(source.resolve("job-package.json"), root.resolve("failed.jar"), entry.getValue(), List.of(), true);
            assertEquals(2, result.code(), result.err()); assertTrue(result.err().contains(entry.getValue()), result.err());
            assertFalse(Files.exists(root.resolve("failed.jar")));
        }
        external.publish("resource-collision", Files.createDirectories(root.resolve("empty-classes")),
                Map.of("ch/so/agi/gretl/i18n/messages.properties", "conflicting-resource"), "");
        SelectedPackageSupport.descriptor(source, List.of(), false, List.of(external.group + ":resource-collision:1.0"));
        var conflict = external.pack(source.resolve("job-package.json"), root.resolve("conflict.jar"), "resource-conflict", List.of(), false);
        assertEquals(2, conflict.code(), conflict.err()); assertTrue(conflict.err().contains("PACKAGE_RESOURCE_CONFLICT"), conflict.err());
        assertTrue(conflict.err().contains("messages.properties"), conflict.err()); assertTrue(conflict.err().contains(external.group), conflict.err());
    }

    private static Object inventory(Path file) throws Exception {
        try (var jar = new java.util.jar.JarFile(file.toFile())) {
            return Json.parse(new String(jar.getInputStream(jar.getJarEntry("META-INF/gretl/dependencies.json")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private static String hash(Path file) throws Exception {
        try (var stream = Files.newInputStream(file)) {
            var digest = java.security.MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[65536]; int count;
            while ((count = stream.read(buffer)) >= 0) digest.update(buffer, 0, count);
            return java.util.HexFormat.of().formatHex(digest.digest());
        }
    }
    @Test void minimalActionSuccessAndFailureRunWithoutSourceCheckoutOrBuildTools() throws Exception {
        for (String target : List.of("success", "failure")) {
            Path root = ArtifactJobRunner.directory("selected-minimal-" + target);
            Path source = SelectedPackageSupport.fixture(root, "packages/action", Map.of(), List.of(), false);
            var result = SelectedPackageSupport.detached(new ArtifactJobRunner.Request("selected-minimal-" + target, source, root, List.of(target),
                    Map.of("output", root.resolve("output")), Map.of("GRETL_CORRELATION_ID", "/packages/minimal/42"), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(45)));
            assertEquals(target.equals("success") ? 0 : 10, result.code(), result.err());
            if (target.equals("success")) assertEquals("minimal package ü\n", Files.readString(root.resolve("output/message.txt")));
            else assertTrue(Files.readString(result.report()).contains("selected-package-business-failure"));
            assertEquals("cleaned", Files.readString(root.resolve("output/cleaned.txt")));
            assertTrue(result.err().contains("/packages/minimal/42"));
            assertTrue(Files.readString(root.resolve("isolation.json")).contains("\"emptyMavenCache\":true"));
            assertFalse(Files.exists(root.resolve("source")));
        }
    }
    @Test void narrowValidatorPackageHasNoIli2dbAvOrFormatEngines() throws Exception {
        Path root = ArtifactJobRunner.directory("selected-narrow-validator");
        Path source = SelectedPackageSupport.fixture(root, "packages/slim-ili", Map.of("ili/IliValidator", "original/IliValidator"), List.of("ilivalidator"), false);
        var result = SelectedPackageSupport.detached(new ArtifactJobRunner.Request("selected-narrow-validator", source, root, List.of(),
                Map.of("output", root.resolve("output")), Map.of(), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(90)));
        assertEquals(0, result.code(), result.err());
        assertTrue(Files.readString(root.resolve("output/validation.txt")).contains("validation done"));
        assertFalse(Files.exists(root.resolve("source")));
    }
}
