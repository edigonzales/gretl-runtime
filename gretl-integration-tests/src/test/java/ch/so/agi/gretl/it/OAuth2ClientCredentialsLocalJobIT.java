package ch.so.agi.gretl.it;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class OAuth2ClientCredentialsLocalJobIT {
    @ParameterizedTest
    @ValueSource(ints = {200, 401})
    void scriptPassesTheBearerObjectAndStopsOnAuthFailure(int status) throws Exception {
        verify(status, ArtifactJobRunner.Mode.SCRIPT);
    }

    static void verify(int status, ArtifactJobRunner.Mode mode) throws Exception {
        String secret = "client:" + UUID.randomUUID() + "+ %";
        String token = "bearer-" + UUID.randomUUID();
        String sentinel = "endpoint-error-" + UUID.randomUUID();
        String basic = "Basic " + Base64.getEncoder().encodeToString(
                ("job-client:" + URLEncoder.encode(secret, UTF_8)).getBytes(UTF_8));
        var tokenRequests = new AtomicInteger();
        var backendRequests = new AtomicInteger();

        String address = mode == ArtifactJobRunner.Mode.IMAGE ? "0.0.0.0" : "127.0.0.1";
        HttpServer server = HttpServer.create(new InetSocketAddress(address, 0), 0);
        server.createContext("/token", exchange -> {
            try (exchange) {
                tokenRequests.incrementAndGet();
                boolean valid = "POST".equals(exchange.getRequestMethod())
                        && "/token".equals(exchange.getRequestURI().toString())
                        && basic.equals(exchange.getRequestHeaders().getFirst("Authorization"))
                        && "application/x-www-form-urlencoded".equals(
                                exchange.getRequestHeaders().getFirst("Content-Type"))
                        && "grant_type=client_credentials".equals(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
                if (!valid) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                String json = status == 200
                        ? "{\"token_type\":\"Bearer\",\"access_token\":\"%s\",\"expires_in\":300}".formatted(token)
                        : "{\"error_description\":\"%s %s %s %s\"}".formatted(sentinel, token, basic, secret);
                reply(exchange, status, json);
            }
        });
        server.createContext("/api", exchange -> {
            try (exchange) {
                backendRequests.incrementAndGet();
                boolean valid = ("Bearer " + token).equals(exchange.getRequestHeaders().getFirst("Authorization"))
                        && "POST".equals(exchange.getRequestMethod())
                        && "{\"probe\":true}".equals(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
                exchange.sendResponseHeaders(valid ? 204 : 403, -1);
            }
        });
        server.start();
        try {
            String id = "oauth2-client-credentials-" + status;
            Path root = ArtifactJobRunner.directory(id, mode);
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            String imageUrl = "http://" + System.getProperty("gretl.test.httpImageHost", "services")
                    + ":" + server.getAddress().getPort();
            var endpoints = Map.of("oauth", new ArtifactJobRunner.Endpoint(url, imageUrl));
            var config = JobRunner.baseConfig(root);
            config.put("secrets", Map.of("oauthClientSecret", Map.of("environment", "GRETL_TEST_OAUTH_SECRET")));
            config.put("runtime", Map.of("workRoot", root.resolve("work").toString(),
                    "reportRoot", root.resolve("reports").toString(), "keepWork", "always"));
            var request = new ArtifactJobRunner.Request(id, ArtifactJobRunner.fixture("oauth2-client-credentials"),
                    root, List.of("redactionProbe"),
                    Map.of("tokenEndpoint", url + "/token", "apiEndpoint", url + "/api", "localHttp", "true"),
                    Map.of("GRETL_TEST_OAUTH_SECRET", secret), config, endpoints, Duration.ofSeconds(60));
            var prepared = ArtifactJobRunner.prepare(request, mode);
            var running = ArtifactJobRunner.start(prepared);
            try {
                assertTrue(running.process().waitFor(60, TimeUnit.SECONDS), mode + " did not exit");
                // Scan raw product output before ArtifactJobRunner applies its own evidence redactor.
                assertNoCredentials(root, List.of(token, secret, basic, basic.substring("Basic ".length()), sentinel));
                assertEquals(1, tokenRequests.get());
                assertEquals(status == 200 ? 1 : 0, backendRequests.get());
                var report = JobTestJson.read(prepared.report());
                if (status == 200) {
                    assertEquals(0, running.process().exitValue());
                    assertEquals("SUCCEEDED", JobTestJson.execution(report).get("status"));
                    assertSucceeded(report, "gretl.oauth2-client-credentials");
                    assertSucceeded(report, "gretl.curl");
                    String console = Files.readString(running.err());
                    try (var paths = Files.list(root.resolve("reports"))) {
                        var events = paths.filter(path -> path.toString().endsWith(".events.jsonl")).toList();
                        assertEquals(1, events.size());
                        String eventLog = Files.readString(events.getFirst());
                        for (String label : List.of("handle", "token", "client", "basic")) {
                            String redacted = "oauth2-masked-" + label + " [REDACTED]";
                            assertTrue(console.contains(redacted), label + " console marker");
                            assertTrue(eventLog.contains(redacted), label + " event marker");
                        }
                    }
                } else {
                    assertEquals(10, running.process().exitValue());
                    assertTrue(Files.readString(prepared.report()).contains("OAUTH2_STATUS"));
                    assertTrue(Files.readString(prepared.report()).contains("HTTP 401"));
                }
            } finally {
                if (running.process().isAlive()) ArtifactJobRunner.stop(running);
                ArtifactJobRunner.finish(running, false);
            }
        } finally {
            server.stop(0);
        }
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void assertNoCredentials(Path root, List<String> values) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String contents = new String(Files.readAllBytes(file), UTF_8);
                assertTrue(values.stream().noneMatch(contents::contains),
                        () -> "Credential or endpoint sentinel in " + root.relativize(file));
            }
        }
    }

    private static void assertSucceeded(Map<?, ?> report, String operation) {
        List<?> invocations = assertInstanceOf(List.class, JobTestJson.execution(report).get("invocations"));
        assertTrue(invocations.stream().map(JobTestJson::object).anyMatch(invocation ->
                operation.equals(invocation.get("operationId")) && "SUCCEEDED".equals(invocation.get("status"))),
                () -> "Missing successful invocation of " + operation);
    }
}
