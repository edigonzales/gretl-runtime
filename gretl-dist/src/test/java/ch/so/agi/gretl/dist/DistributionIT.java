package ch.so.agi.gretl.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DistributionIT {
    @TempDir
    Path tempDir;

    @Test
    void extractedDistributionStartsAndLoadsStandardAndExtensionTasks() throws Exception {
        Path zip = Path.of("target", "gretl-runtime-0.1.0-SNAPSHOT-bin.zip");
        assertTrue(Files.isRegularFile(zip), "distribution zip missing: " + zip);
        Path root = tempDir.resolve("dist");
        unzip(zip, root);
        Path base = Files.list(root).filter(Files::isDirectory).findFirst().orElseThrow();
        Path checksums = base.resolve("SHA256SUMS");
        assertTrue(Files.isRegularFile(checksums));
        String manifest = Files.readString(checksums);
        assertTrue(manifest.contains("bin/gretl"));
        String line = manifest.lines()
                .filter(candidate -> candidate.endsWith("  bin/gretl"))
                .findFirst().orElseThrow();
        assertEquals(ChecksumManifest.sha256(base.resolve("bin/gretl")),
                line.substring(0, 64));
        assertApiArtifact(base);

        ProcessResult version = run(base, "--version");
        assertEquals(0, version.code(), version.err());
        assertTrue(version.out().contains("Java: 25"));
        assertTrue(version.out().contains("Groovy: 6.0.0"));
        ProcessResult operations = run(base, "operations");
        assertEquals(0, operations.code(), operations.err());
        assertTrue(operations.out().contains("HttpDownload"));
        assertTrue(operations.out().contains("Extract"));
        assertTrue(operations.out().contains("SqlExecutor"));
        assertTrue(operations.out().contains("Curl"));
        assertTrue(operations.out().contains("Gzip"));
        assertTrue(operations.out().contains("XslTransformer"));
        assertTrue(operations.out().contains("FtpList"));
        assertTrue(operations.out().contains("FtpDownload"));
        assertTrue(operations.out().contains("FtpUpload"));
        assertTrue(operations.out().contains("FtpDelete"));
        assertTrue(operations.out().contains("IliValidator"));
        assertTrue(!operations.out().contains("FileSize"));

        Path extension = Path.of(Class.forName("example.ExampleProvider")
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.copy(extension, base.resolve("lib/extensions/file-size-extension.jar"));
        ProcessResult extended = run(base, "operations");
        assertEquals(0, extended.code(), extended.err());
        assertTrue(extended.out().contains("FileSize"));

        Path job = tempDir.resolve("extension-job");
        Files.createDirectories(job);
        Files.writeString(job.resolve("data.txt"), "12345");
        Files.writeString(job.resolve("job.groovy"), """
                task('size', FileSize) {
                    input = file('data.txt')
                }
                defaults 'size'
                """);
        ProcessResult size = run(base, "run", job.resolve("job.groovy").toString(),
                "--work-dir", tempDir.resolve("work").toString(),
                "--report", tempDir.resolve("extension-report.json").toString());
        assertEquals(0, size.code(), size.err());
        assertTrue(Files.isRegularFile(tempDir.resolve("extension-report.json")));
    }

    @Test
    void extractedDistributionDownloadsAndExtractsWithLocalServer() throws Exception {
        Path zip = Path.of("target", "gretl-runtime-0.1.0-SNAPSHOT-bin.zip");
        Path root = tempDir.resolve("dist2");
        unzip(zip, root);
        Path base = Files.list(root).filter(Files::isDirectory).findFirst().orElseThrow();
        byte[] archive = zipArchive();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/data.zip", exchange -> {
            exchange.sendResponseHeaders(200, archive.length);
            exchange.getResponseBody().write(archive);
            exchange.close();
        });
        server.start();
        try {
            Path job = tempDir.resolve("download-job");
            Files.createDirectories(job);
            Files.writeString(job.resolve("job.groovy"), """
                    parameter('quelle', required: true, description: 'ZIP')
                    parameter('ziel', type: 'path', required: true, description: 'Ziel')
                    task('download', HttpDownload) {
                        url = parameter('quelle')
                        output = workPath('data.zip')
                    }
                    task('extract', Extract, dependsOn: 'download') {
                        archive = result('download').file
                        compression = 'ZIP'
                        outputDirectory = parameter('ziel')
                    }
                    defaults 'extract'
                    """);
            Path out = tempDir.resolve("out");
            ProcessResult result = run(base, "run", job.resolve("job.groovy").toString(),
                    "--param", "quelle=http://127.0.0.1:" + server.getAddress().getPort()
                            + "/data.zip",
                    "--param", "ziel=" + out,
                    "--work-dir", tempDir.resolve("work2").toString(),
                    "--report", tempDir.resolve("dist-report.json").toString());
            assertEquals(0, result.code(), result.err());
            assertEquals("distribution", Files.readString(out.resolve("a.txt")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractedDistributionRunsOptionalAndNoDataExamples() throws Exception {
        Path zip = Path.of("target", "gretl-runtime-0.1.0-SNAPSHOT-bin.zip");
        Path root = tempDir.resolve("dist4");
        unzip(zip, root);
        Path base = Files.list(root).filter(Files::isDirectory).findFirst().orElseThrow();
        Path report = tempDir.resolve("nodata-report.json");
        ProcessResult nodata = run(base, "run",
                base.resolve("examples/action-nodata/nodata.groovy").toString(),
                "--work-dir", tempDir.resolve("nodata-work").toString(),
                "--report", report.toString());
        assertEquals(0, nodata.code(), nodata.err());
        assertTrue(Files.readString(report).contains("NO_DATA"));

        ProcessResult skipped = run(base, "run",
                base.resolve("examples/action-nodata/optional.groovy").toString(),
                "--work-dir", tempDir.resolve("optional-work").toString(),
                "--report", tempDir.resolve("optional-report.json").toString());
        assertEquals(0, skipped.code(), skipped.err());
        assertTrue(Files.readString(tempDir.resolve("optional-report.json"))
                .contains("SKIPPED"));
    }

    @Test
    void extractedDistributionRunsTokenCatalogExample() throws Exception {
        Path zip = Path.of("target", "gretl-runtime-0.1.0-SNAPSHOT-bin.zip");
        Path root = tempDir.resolve("dist5");
        unzip(zip, root);
        Path base = Files.list(root).filter(Files::isDirectory).findFirst().orElseThrow();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        byte[] archive = zipArchive();
        server.createContext("/token", exchange -> {
            byte[] body = "{\"access_token\":\"dynamic-token\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/catalog", exchange -> {
            byte[] body = ("{\"files\":[{\"date\":\"2026-01-01\",\"url\":\""
                    + baseUrl + "/data.zip\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/data.zip", exchange -> {
            exchange.sendResponseHeaders(200, archive.length);
            exchange.getResponseBody().write(archive);
            exchange.close();
        });
        server.start();
        try {
            Path configDir = Files.createDirectories(tempDir.resolve("token-config"));
            Files.writeString(configDir.resolve("auth.txt"), "client-secret\n");
            Files.writeString(configDir.resolve("runtime.json"), """
                    {"schemaVersion":1,
                     "secrets":{"sourceAuth":{"file":"auth.txt"}},
                     "runtime":{"workRoot":"work","reportRoot":"reports"}}
                    """);
            Path report = tempDir.resolve("token-report.json");
            ProcessResult result = run(base, "run",
                    base.resolve("examples/token-catalog/job.groovy").toString(),
                    "--config", configDir.resolve("runtime.json").toString(),
                    "--param", "tokenUrl=" + baseUrl + "/token",
                    "--param", "catalogUrl=" + baseUrl + "/catalog",
                    "--keep-work", "always",
                    "--report", report.toString());
            assertEquals(0, result.code(), result.err());
            Path data = Files.walk(configDir.resolve("work"))
                    .filter(path -> path.getFileName().toString().equals("a.txt"))
                    .findFirst().orElseThrow();
            assertEquals("distribution", Files.readString(data));
            assertTrue(Files.readString(report).contains("SUCCEEDED"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractedDistributionRunsRealInterlisValidator() throws Exception {
        Path zip = Path.of("target", "gretl-runtime-0.1.0-SNAPSHOT-bin.zip");
        Path root = tempDir.resolve("dist3");
        unzip(zip, root);
        Path base = Files.list(root).filter(Files::isDirectory).findFirst().orElseThrow();
        Path report = tempDir.resolve("ili-report.json");
        ProcessResult valid = run(base, "run",
                base.resolve("examples/ili-validator/job.groovy").toString(),
                "--work-dir", tempDir.resolve("ili-work").toString(),
                "--report", report.toString());
        assertEquals(0, valid.code(), valid.err());
        assertTrue(Files.isRegularFile(report));

        Path jobDir = tempDir.resolve("ili-invalid");
        Files.createDirectories(jobDir.resolve("models"));
        Files.createDirectories(jobDir.resolve("data"));
        Files.copy(base.resolve("examples/ili-validator/models/GretlM0Fixture.ili"),
                jobDir.resolve("models/GretlM0Fixture.ili"));
        Files.copy(base.resolve("examples/ili-validator/data/invalid.xtf"),
                jobDir.resolve("data/invalid.xtf"));
        Files.writeString(jobDir.resolve("job.groovy"), """
                task('validate', IliValidator) {
                    dataFile = file('data/invalid.xtf')
                    modelDirectory = file('models')
                }
                defaults 'validate'
                """);
        ProcessResult invalid = run(base, "run", jobDir.resolve("job.groovy").toString(),
                "--work-dir", tempDir.resolve("ili-invalid-work").toString(),
                "--report", tempDir.resolve("ili-invalid-report.json").toString());
        assertEquals(10, invalid.code());
        assertTrue(Files.readString(tempDir.resolve("ili-invalid-report.json"))
                .contains("VALIDATION_FAILED"));
    }

    @Test
    void extractedDistributionPackagesAndRunsIliValidatorJar() throws Exception {
        Path zip = Path.of("target", "gretl-runtime-0.1.0-SNAPSHOT-bin.zip");
        Path root = tempDir.resolve("dist6");
        unzip(zip, root);
        Path base = Files.list(root).filter(Files::isDirectory).findFirst().orElseThrow();
        Path jar = tempDir.resolve("ili-validator-job.jar");
        Path descriptor = base.resolve("examples/ili-validator/job-package.json");
        ProcessResult packaged = run(base, "package", descriptor.toString(),
                "--output", jar.toString());
        assertEquals(0, packaged.code(), packaged.err());
        assertTrue(Files.isRegularFile(jar));

        Path foreignCwd = Files.createDirectories(tempDir.resolve("foreign-cwd"));
        ProcessResult version = runJava(foreignCwd, jar, "--version");
        assertEquals(0, version.code(), version.err());
        assertTrue(version.out().contains("ili-validator 0.1.0-SNAPSHOT"));

        ProcessResult help = runJava(foreignCwd, jar, "--help");
        assertEquals(0, help.code(), help.err());
        assertTrue(help.out().contains("Job: job.groovy"));
        assertTrue(help.out().contains("validate"));

        ProcessResult plan = runJava(foreignCwd, jar, "--plan");
        assertEquals(0, plan.code(), plan.err());
        assertTrue(plan.out().contains("validate"));

        Path report = tempDir.resolve("jar-report.json");
        ProcessResult run = runJava(foreignCwd, jar,
                "--report", report.toString());
        assertEquals(0, run.code(), run.err());
        assertTrue(Files.isRegularFile(report));
        assertTrue(Files.readString(report).contains("SUCCEEDED"));
    }

    @Test
    void generatedAsciiDocReferenceMatchesCheckedInDocs() throws Exception {
        Path zip = Path.of("target", "gretl-runtime-0.1.0-SNAPSHOT-bin.zip");
        Path root = tempDir.resolve("dist-docs");
        unzip(zip, root);
        Path base = Files.list(root).filter(Files::isDirectory).findFirst().orElseThrow();
        Path generated = tempDir.resolve("generated-docs");
        ProcessResult result = run(base, "operations", "--format", "adoc",
                "--lang", "de", "--output", generated.toString());
        assertEquals(0, result.code(), result.err());

        Path expected = Path.of("..", "docs", "reference", "generated")
                .toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(expected), "checked-in reference missing: " + expected);
        List<String> expectedFiles = fileNames(expected);
        List<String> actualFiles = fileNames(generated);
        assertEquals(expectedFiles, actualFiles,
                "generated reference file set differs; run scripts/generate-task-docs.sh");
        for (String file : expectedFiles) {
            assertEquals(Files.readString(expected.resolve(file), StandardCharsets.UTF_8),
                    Files.readString(generated.resolve(file), StandardCharsets.UTF_8),
                    "generated reference is stale: " + file
                            + "; run scripts/generate-task-docs.sh");
        }

        Path master = expected.getParent().resolve("reference.adoc");
        assertTrue(Files.isRegularFile(master), "editorial master missing: " + master);
        Set<String> included = new HashSet<>();
        Matcher matcher = Pattern.compile(
                        "include::generated/(task-[A-Za-z0-9._-]+\\.adoc)\\[\\]")
                .matcher(Files.readString(master, StandardCharsets.UTF_8));
        while (matcher.find()) {
            included.add(matcher.group(1));
        }
        assertEquals(new HashSet<>(expectedFiles), included,
                "reference.adoc must include every generated table and no missing table; "
                        + "run scripts/generate-task-docs.sh and update reference.adoc");
    }

    private static List<String> fileNames(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    private static void assertApiArtifact(Path base) throws Exception {
        Path runtime = base.resolve("lib/runtime");
        Path apiJar;
        try (java.util.stream.Stream<Path> files = Files.list(runtime)) {
            apiJar = files.filter(path -> path.getFileName().toString()
                            .startsWith("gretl-api-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .findFirst().orElseThrow();
        }
        assertTrue(Files.isRegularFile(apiJar), "API JAR missing: " + apiJar);
        try (JarFile jar = new JarFile(apiJar.toFile())) {
            List<JarEntry> classes = jar.stream()
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !entry.getName().startsWith("META-INF/"))
                    .toList();
            assertEquals(29, classes.size(), "API surface: " + classes);
            for (JarEntry entry : classes) {
                try (DataInputStream data = new DataInputStream(
                        jar.getInputStream(entry))) {
                    assertEquals(0xCAFEBABE, data.readInt());
                    data.readUnsignedShort();
                    assertEquals(69, data.readUnsignedShort(), entry.getName());
                }
            }
            assertTrue(jar.stream().noneMatch(entry ->
                    entry.getName().startsWith("META-INF/services/")));
            assertEquals("ch.so.agi.gretl.runtime.api",
                    jar.getManifest().getMainAttributes()
                            .getValue("Automatic-Module-Name"));
        }
    }

    private ProcessResult runJava(Path cwd, Path jar, String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        String javaHome = System.getenv("JAVA_HOME");
        command.add(javaHome == null || javaHome.isBlank()
                ? "java" : Path.of(javaHome, "bin", "java").toString());
        command.add("-jar");
        command.add(jar.toString());
        command.addAll(List.of(args));
        return execute(cwd, command);
    }

    @Test
    void packagedJarDoesNotExecuteAtBuildAndSupportsParametersAndCustomTasks() throws Exception {
        Path zip = Path.of("target", "gretl-runtime-0.1.0-SNAPSHOT-bin.zip");
        Path root = tempDir.resolve("dist7");
        unzip(zip, root);
        Path base = Files.list(root).filter(Files::isDirectory).findFirst().orElseThrow();
        Path extension = Path.of(Class.forName("example.ExampleProvider")
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.copy(extension, base.resolve("lib/extensions/file-size-extension.jar"));

        Path jobDir = Files.createDirectories(tempDir.resolve("package-param-job"));
        Path marker = tempDir.resolve("toplevel-marker.txt");
        Files.writeString(jobDir.resolve("data.txt"), "12345");
        Files.writeString(jobDir.resolve("job.groovy"), """
                parameter('wert', required: true, description: 'Wert')
                new java.io.File('%s').text = 'top-level'
                task('size', FileSize) { input = file('data.txt') }
                action('run', dependsOn: 'size') {
                    println 'WERT=' + parameter('wert')
                }
                defaults 'run'
                """.formatted(marker.toString().replace("\\", "\\\\")));
        Path descriptor = jobDir.resolve("job-package.json");
        Files.writeString(descriptor, """
                {
                  "schemaVersion": 1,
                  "name": "parameter-demo",
                  "version": "2026.10.18",
                  "runtimeVersion": "0.1.0-SNAPSHOT",
                  "entryScript": "job.groovy",
                  "sources": [],
                  "resources": ["data.txt"],
                  "dependencies": [
                    "ch.so.agi.gretl.examples:file-size-extension:0.1.0-SNAPSHOT"
                  ]
                }
                """);
        Path jar = tempDir.resolve("parameter-job.jar");
        ProcessResult packaged = run(base, "package", descriptor.toString(),
                "--output", jar.toString());
        assertEquals(0, packaged.code(), packaged.err());
        assertTrue(!Files.exists(marker), "packaging must not execute the job");

        Path foreignCwd = Files.createDirectories(tempDir.resolve("param-cwd"));
        ProcessResult help = runJava(foreignCwd, jar, "--help");
        assertEquals(0, help.code(), help.err());
        assertTrue(help.out().contains("wert"));

        ProcessResult graph = runJava(foreignCwd, jar, "--plan", "--graph", "mermaid");
        assertEquals(0, graph.code(), graph.err());
        assertTrue(graph.out().startsWith("flowchart TB"), graph.out());

        ProcessResult missing = runJava(foreignCwd, jar);
        assertEquals(2, missing.code(), missing.err());
        assertTrue(missing.err().toLowerCase().contains("required parameter")
                || missing.err().toLowerCase().contains("missing required"));

        ProcessResult first = runJava(foreignCwd, jar,
                "--param", "wert=A",
                "--report", tempDir.resolve("param-report-a.json").toString());
        assertEquals(0, first.code(), first.err());
        assertTrue(Files.isRegularFile(marker));
        assertTrue(first.err().contains("WERT=A"));

        ProcessResult second = runJava(foreignCwd, jar,
                "--param", "wert=B",
                "--report", tempDir.resolve("param-report-b.json").toString());
        assertEquals(0, second.code(), second.err());
        assertTrue(second.err().contains("WERT=B"));
        assertTrue(!Files.readString(tempDir.resolve("param-report-a.json"))
                .equals(Files.readString(tempDir.resolve("param-report-b.json"))));
    }

    private ProcessResult run(Path base, String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>();
        command.add(base.resolve("bin/gretl").toString());
        command.addAll(List.of(args));
        return execute(tempDir, command);
    }

    private ProcessResult execute(Path cwd, List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(false);
        Process process = builder.start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "distribution command timed out");
        return new ProcessResult(process.exitValue(), out, err);
    }

    private static void unzip(Path zip, Path target) throws IOException {
        Files.createDirectories(target);
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path path = target.resolve(entry.getName()).normalize();
                if (!path.startsWith(target)) {
                    throw new IOException("zip traversal");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(path);
                } else {
                    Files.createDirectories(path.getParent());
                    Files.copy(input, path);
                    if (entry.getName().endsWith("/bin/gretl") || entry.getName().equals("bin/gretl")) {
                        Files.setPosixFilePermissions(path, java.util.EnumSet.of(
                                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                                java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                                java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                                java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                                java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
                                java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE));
                    }
                }
            }
        }
    }

    private static byte[] zipArchive() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("a.txt"));
            zip.write("distribution".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private record ProcessResult(int code, String out, String err) {
    }
}
