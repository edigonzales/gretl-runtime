package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JsonReadLocalJobIT {
    @Test
    void curlResponseSuppliesTheNextRequestBody() throws Exception {
        String name = "Grüezi";
        var posted = new AtomicReference<String>();
        var result = run("json-read", "{\"name\":\"" + name + "\"}", posted);
        assertEquals(0, result.code(), result.err());
        assertEquals(name, posted.get());
    }

    @Test
    void malformedResponseStopsTheDependentTaskWithStructuredDiagnostic() throws Exception {
        String marker = "private_invalid_payload";
        var posted = new AtomicReference<String>();
        var result = run("json-read-invalid", marker, posted);
        assertEquals(10, result.code(), result.err());
        String report = Files.readString(result.report());
        assertTrue(report.contains("JSON_READ_INVALID"), report);
        assertFalse(report.contains(marker), report);
        assertNull(posted.get());
    }

    private static ArtifactJobRunner.Result run(String id, String document, AtomicReference<String> posted)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] response = document.getBytes(StandardCharsets.UTF_8);
        server.createContext("/request", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.createContext("/complete", exchange -> {
            try (exchange) {
                posted.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
            }
        });
        server.start();
        try {
            Path root = ArtifactJobRunner.directory(id);
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            return ArtifactJobRunner.run(new ArtifactJobRunner.Request(id, ArtifactJobRunner.fixture("json-read"),
                    root, List.of(), Map.of("url", url), Map.of(), JobRunner.baseConfig(root), Map.of(),
                    Duration.ofSeconds(60)));
        } finally {
            server.stop(0);
        }
    }
}
