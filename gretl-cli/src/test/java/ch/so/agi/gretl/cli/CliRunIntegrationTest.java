package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.logging.Json;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliRunIntegrationTest {
    @TempDir
    Path tempDir;
    private HttpServer server;
    private String base;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        byte[] zip = zip();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hello", exchange -> {
            byte[] body = "hello-http".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            lastPath.set(exchange.getRequestURI().getPath());
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (exchange.getRequestURI().getPath().equals("/status")) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, zip.length);
            exchange.getResponseBody().write(zip);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void runsHttpAndExtractAndWritesJSONReportAndEvents() throws Exception {
        Path script = downloadExtractJob();
        Path out = tempDir.resolve("out");
        Path report = tempDir.resolve("report.json");
        Result result = cli().run("run", script.toString(),
                "--param", "url=" + base + "/data.zip",
                "--param", "ziel=" + out,
                "--work-dir", tempDir.resolve("work").toString(),
                "--report", report.toString());

        assertEquals(0, result.code(), result.err());
        assertEquals("zip-content", Files.readString(out.resolve("a.txt")));
        Map<?, ?> json = (Map<?, ?>) Json.parse(Files.readString(report));
        assertEquals("SUCCEEDED", ((Map<?, ?>) json.get("execution")).get("status"));
        assertEquals("zip-content",
                Files.readString(out.resolve("a.txt")));
        assertEquals(1, calls.get());
        assertEquals("/data.zip", lastPath.get());
        assertTrue(Files.exists(report));
        Path events = findEventFile();
        assertTrue(Files.exists(events));
        List<String> lines = Files.readAllLines(events);
        assertTrue(lines.size() >= 5);
        for (String line : lines) {
            Json.parse(line);
        }
        assertFalse(Files.readString(report).contains("zip-content"));
    }

    @Test
    void parameterPrecedenceIsRuntimeFileCliAndFirstEqualsWins() throws Exception {
        Path script = downloadExtractJob();
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"params\":{\"url\":\""
                + base + "/runtime\"},\"runtime\":{\"workRoot\":\""
                + tempDir.resolve("work").toString().replace("\\", "\\\\")
                + "\",\"reportRoot\":\"" + tempDir.resolve("reports").toString()
                .replace("\\", "\\\\") + "\"}}");
        Path params = tempDir.resolve("params.json");
        Files.writeString(params, "{\"url\":\"" + base + "/file\"}");

        Result fromCli = cli().run("run", script.toString(),
                "--config", config.toString(), "--params-file", params.toString(),
                "--param", "url=" + base + "/cli?x=a=b", "--param",
                "ziel=" + tempDir.resolve("out"));
        assertEquals(0, fromCli.code(), fromCli.err());
        assertEquals("/cli", lastPath.get());

        Result fromFile = cli().run("run", script.toString(),
                "--config", config.toString(), "--params-file", params.toString(),
                "--param", "ziel=" + tempDir.resolve("out2"));
        assertEquals(0, fromFile.code(), fromFile.err());
        assertEquals("/file", lastPath.get());

        Result fromRuntime = cli().run("run", script.toString(),
                "--config", config.toString(),
                "--param", "ziel=" + tempDir.resolve("out3"));
        assertEquals(0, fromRuntime.code(), fromRuntime.err());
        assertEquals("/runtime", lastPath.get());
    }

    @Test
    void secretsAreSentButNeverReportedOrLogged() throws Exception {
        Path secret = tempDir.resolve("auth.txt");
        Files.writeString(secret, "Bearer secret-value\n");
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"secrets\":{\"auth\":{\"file\":\"auth.txt\"}},"
                + "\"runtime\":{\"workRoot\":\"" + tempDir.resolve("work")
                .toString().replace("\\", "\\\\") + "\",\"reportRoot\":\""
                + tempDir.resolve("reports").toString().replace("\\", "\\\\") + "\"}}");
        Path script = tempDir.resolve("secret.groovy");
        Files.writeString(script, """
                parameter('url', required: true, description: 'URL')
                task('download', HttpDownload) {
                    url = parameter('url')
                    headers = ['Authorization': secret('auth')]
                    output = workPath('data.bin')
                }
                defaults 'download'
                """);
        Path report = tempDir.resolve("secret-report.json");
        Result result = cli().run("run", script.toString(),
                "--config", config.toString(),
                "--param", "url=" + base + "/data",
                "--report", report.toString(),
                "--log-format", "json");
        assertEquals(0, result.code(), result.err());
        assertEquals("Bearer secret-value", lastAuthorization.get());
        assertFalse(result.err().contains("secret-value"));
        assertFalse(Files.readString(report).contains("secret-value"));
        Path events = findEventFile();
        assertFalse(Files.readString(events).contains("secret-value"));
        assertTrue(Files.size(events) > 0);
    }

    @Test
    void failureRetainsWorkAndReportHasSafeError() throws Exception {
        Path script = downloadExtractJob();
        Path work = tempDir.resolve("work");
        Path report = tempDir.resolve("failed.json");
        Result result = cli().run("run", script.toString(),
                "--param", "url=" + base + "/status",
                "--param", "ziel=" + tempDir.resolve("out"),
                "--work-dir", work.toString(),
                "--report", report.toString());
        assertEquals(10, result.code());
        Map<?, ?> json = (Map<?, ?>) Json.parse(Files.readString(report));
        assertEquals("FAILED", ((Map<?, ?>) json.get("execution")).get("status"));
        assertFalse(Files.readString(report).contains(base + "/status"));
        assertTrue(Files.exists(work));
        assertTrue(Files.list(work).findAny().isPresent());
    }

    @Test
    void dynamicSensitiveValueIsMaskedInConsoleEventsAndReport() throws Exception {
        Path script = tempDir.resolve("dynamic.groovy");
        Files.writeString(script, """
                action('run') {
                    def token = ctx.sensitive('dynamic-token-123')
                    log().info("token=${token}")
                    println "script=${token}"
                    return token
                }
                defaults 'run'
                """);
        Path report = tempDir.resolve("dynamic-report.json");
        Result result = cli().run("run", script.toString(),
                "--work-dir", tempDir.resolve("dynamic-work").toString(),
                "--report", report.toString());
        assertEquals(0, result.code(), result.err());
        assertFalse(result.err().contains("dynamic-token-123"));
        assertTrue(result.err().contains("[REDACTED]"));
        assertFalse(Files.readString(report).contains("dynamic-token-123"));
        assertFalse(Files.readString(findEventFile()).contains("dynamic-token-123"));
    }

    @Test
    void httpDownloadOverwriteOptionIsAvailableInGroovyJobs() throws Exception {
        Path target = tempDir.resolve("overwrite.bin");
        Files.writeString(target, "old-content");
        Path script = tempDir.resolve("http-overwrite.groovy");
        Files.writeString(script, """
                task('download', HttpDownload) {
                    url = '%s/hello'
                    output = path('%s')
                    overwrite = true
                }
                defaults 'download'
                """.formatted(base, target.toString().replace("\\", "\\\\")));

        Result result = cli().run("run", script.toString(),
                "--work-dir", tempDir.resolve("overwrite-work").toString());

        assertEquals(0, result.code(), result.err());
        assertEquals("hello-http", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void failedConfigurationReportKeepsSafeSourceAndLine() throws Exception {
        Path script = tempDir.resolve("bad-config.groovy");
        Files.writeString(script, """
                task('bad', HttpDownload) {
                    unknownSetting = 'x'
                }
                defaults 'bad'
                """);
        Path report = tempDir.resolve("bad-report.json");
        Result result = cli().run("run", script.toString(),
                "--work-dir", tempDir.resolve("bad-work").toString(),
                "--report", report.toString());
        assertEquals(10, result.code(), result.err());
        Map<?, ?> json = (Map<?, ?>) Json.parse(Files.readString(report));
        List<?> errors = (List<?>) json.get("errors");
        assertTrue(!errors.isEmpty());
        Map<?, ?> error = (Map<?, ?>) errors.get(0);
        Map<?, ?> source = (Map<?, ?>) error.get("source");
        assertEquals("bad-config.groovy", source.get("file"));
        assertTrue(((Number) source.get("line")).intValue() > 0);
        assertFalse(Files.readString(report).contains("unknownSetting = 'x'"));
    }

    @Test
    void jsonConsoleContainsOneParseableObjectPerLine() throws Exception {
        Path script = tempDir.resolve("json.groovy");
        Files.writeString(script, """
                println 'top-level-message'
                action('run') { return 'ok' }
                defaults 'run'
                """);
        Result result = cli().run("run", script.toString(),
                "--log-format", "json",
                "--work-dir", tempDir.resolve("json-work").toString(),
                "--report", tempDir.resolve("json-report.json").toString());
        assertEquals(0, result.code(), result.err());
        List<String> lines = result.err().lines().toList();
        assertTrue(lines.size() >= 4);
        for (String line : lines) {
            assertTrue(line.startsWith("{"));
            Json.parse(line);
        }
        Path events = findEventFile();
        List<String> eventLines = Files.readAllLines(events);
        assertTrue(eventLines.size() >= 4);
        assertTrue(Files.readString(events).contains("gretl.script.message"));
        assertTrue(Files.readString(events).contains("top-level-message"));
        for (String line : eventLines) {
            Json.parse(line);
        }
        Map<?, ?> report = (Map<?, ?>) Json.parse(
                Files.readString(tempDir.resolve("json-report.json")));
        assertEquals("json", ((Map<?, ?>) report.get("logging")).get("format"));
    }

    @Test
    void cooperativeTimeoutReturnsCodeTwentyAndWritesReport() throws Exception {
        Path script = tempDir.resolve("timeout.groovy");
        Files.writeString(script, """
                action('wait') {
                    while (true) {
                        ctx.cancellation().throwIfCancellationRequested()
                        Thread.sleep(20)
                    }
                }
                defaults 'wait'
                """);
        Path report = tempDir.resolve("timeout.json");
        Result result = cli().run("run", script.toString(),
                "--timeout", "PT0.2S",
                "--work-dir", tempDir.resolve("timeout-work").toString(),
                "--report", report.toString());
        assertEquals(20, result.code(), result.err());
        Map<?, ?> json = (Map<?, ?>) Json.parse(Files.readString(report));
        assertEquals("CANCELLED", ((Map<?, ?>) json.get("execution")).get("status"));
    }

    @Test
    void keepWorkAlwaysAndCustomLogLevel() throws Exception {
        Path script = downloadExtractJob();
        Path work = tempDir.resolve("keep-work");
        Result result = cli().run("run", script.toString(),
                "--param", "url=" + base + "/data.zip",
                "--param", "ziel=" + tempDir.resolve("out"),
                "--work-dir", work.toString(),
                "--report", tempDir.resolve("keep.json").toString(),
                "--keep-work", "always",
                "--log-level", "ERROR");
        assertEquals(0, result.code(), result.err());
        assertTrue(Files.list(work).findAny().isPresent());
        assertFalse(result.err().contains("gretl.run.started"));
        Path events = findEventFile();
        assertTrue(Files.readString(events).contains("gretl.run.started"));
    }

    private Path downloadExtractJob() throws IOException {
        Path script = tempDir.resolve("download-extract.groovy");
        Files.writeString(script, """
                parameter('url', required: true, description: 'ZIP-URL')
                parameter('ziel', type: 'path', required: true, description: 'Ziel')
                task('download', HttpDownload) {
                    url = parameter('url')
                    output = workPath('data.zip')
                }
                task('extract', Extract, dependsOn: 'download') {
                    archive = result('download').file
                    compression = 'ZIP'
                    outputDirectory = parameter('ziel')
                }
                defaults 'extract'
                """, StandardCharsets.UTF_8);
        return script;
    }

    private Path findEventFile() throws IOException {
        Path reports = tempDir.resolve("reports");
        if (!Files.isDirectory(reports)) {
            reports = tempDir.resolve("gretl-reports");
        }
        try (var files = Files.list(reports)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".events.jsonl"))
                    .findFirst().orElseThrow();
        }
    }

    private static byte[] zip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("a.txt"));
            zip.write("zip-content".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private Cli cli() {
        return new Cli();
    }

    private final class Cli {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();

        Result run(String... args) {
            int code = new GretlCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8),
                    Map.of(), tempDir, getClass().getClassLoader()).run(args);
            return new Result(code, out.toString(StandardCharsets.UTF_8),
                    err.toString(StandardCharsets.UTF_8));
        }
    }

    private record Result(int code, String out, String err) {
    }
}
