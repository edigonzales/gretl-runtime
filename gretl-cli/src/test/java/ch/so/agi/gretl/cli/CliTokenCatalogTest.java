package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTokenCatalogTest {
    @TempDir
    Path tempDir;
    private HttpServer server;
    private String base;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            paths.add("/token");
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            respond(exchange, "{\"access_token\":\"dynamic-token\"}");
        });
        server.createContext("/catalog", exchange -> {
            paths.add("/catalog");
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, "{\"files\":["
                    + "{\"date\":\"2026-01-01\",\"url\":\"" + base + "/data-old.zip\"},"
                    + "{\"date\":\"2026-02-01\",\"url\":\"" + base + "/data-new.zip\"}]}");
        });
        server.createContext("/data-old.zip", exchange -> {
            paths.add("/data-old.zip");
            respondZip(exchange, "old.txt", "old");
        });
        server.createContext("/data-new.zip", exchange -> {
            paths.add("/data-new.zip");
            respondZip(exchange, "new.txt", "new-content");
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void tokenCatalogAndLatestDownloadRunInOrderWithSecretsMasked() throws Exception {
        Path auth = tempDir.resolve("source-auth.txt");
        Files.writeString(auth, "client-secret\n");
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"secrets\":{\"sourceAuth\":{\"file\":\"source-auth.txt\"}},"
                + "\"runtime\":{\"workRoot\":\"" + tempDir.resolve("work")
                .toString().replace("\\", "\\\\") + "\",\"reportRoot\":\""
                + tempDir.resolve("reports").toString().replace("\\", "\\\\") + "\"}}");
        Path script = tempDir.resolve("token-catalog.groovy");
        Files.copy(Path.of("..", "examples", "token-catalog", "job.groovy"), script);
        Path report = tempDir.resolve("token-report.json");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new GretlCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), Map.of(), tempDir,
                getClass().getClassLoader()).run(new String[]{"run", script.toString(),
                "--config", config.toString(),
                "--param", "tokenUrl=" + base + "/token",
                "--param", "catalogUrl=" + base + "/catalog",
                "--report", report.toString(), "--keep-work", "always"});
        String errText = err.toString(StandardCharsets.UTF_8);
        assertEquals(0, code, errText);
        assertEquals(List.of("/token", "/catalog", "/data-new.zip"), paths);
        assertEquals("client-secret", authorizations.get(0));
        assertEquals("Bearer dynamic-token", authorizations.get(1));
        assertEquals("grant_type=client_credentials", bodies.get(0));
        assertEquals("new-content", Files.readString(
                findFile(tempDir.resolve("work"), "new.txt")));
        String reportText = Files.readString(report);
        assertFalse(reportText.contains("client-secret"));
        assertFalse(reportText.contains("dynamic-token"));
        assertFalse(errText.contains("client-secret"));
        assertFalse(errText.contains("dynamic-token"));
    }

    private Path findFile(Path root, String name) throws Exception {
        try (var files = Files.walk(root)) {
            return files.filter(path -> path.getFileName().toString().equals(name))
                    .findFirst().orElseThrow();
        }
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void respondZip(com.sun.net.httpserver.HttpExchange exchange,
                            String entry, String content) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] archive = bytes.toByteArray();
        exchange.sendResponseHeaders(200, archive.length);
        exchange.getResponseBody().write(archive);
        exchange.close();
    }
}
