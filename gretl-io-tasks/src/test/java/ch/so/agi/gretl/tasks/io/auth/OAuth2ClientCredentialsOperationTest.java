package ch.so.agi.gretl.tasks.io.auth;

import ch.so.agi.gretl.runtime.api.SecretValue;
import ch.so.agi.gretl.runtime.api.TaskCancelledException;
import ch.so.agi.gretl.tasks.io.TestOperationContext;
import ch.so.agi.gretl.tasks.io.auth.OAuth2ClientCredentialsOperation.Failure;
import ch.so.agi.gretl.tasks.io.auth.OAuth2ClientCredentialsOperation.Handler;
import ch.so.agi.gretl.tasks.io.auth.OAuth2ClientCredentialsOperation.Request;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class OAuth2ClientCredentialsOperationTest {
    private static final String ID = "client: id+é";
    private static final String SECRET = "test:sëcret +%";
    private static final String TOKEN = "synthetic-token";
    private static final String SENTINEL = "synthetic-error-echo";
    private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString(
            "client%3A+id%2B%C3%A9:test%3As%C3%ABcret+%2B%25".getBytes(UTF_8));
    private static final String SUCCESS = "{\"access_token\":\"%s\",\"token_type\":\"bEaReR\",\"expires_in\":300}";

    @TempDir Path root;
    private HttpServer server;
    private URI endpoint;
    private TestOperationContext context;
    private final Handler handler = new Handler();
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/token");
        context = new TestOperationContext(root, Map.of(), Map.of(), Map.of("testSecret", SECRET));
        server.start();
    }

    @AfterEach void stop() {
        server.stop(0);
    }

    @Test void postsEncodedCredentialsAndReturnsOnlyAMaskedBearerValue() throws Exception {
        var incoming = new AtomicReference<List<String>>();
        server.createContext("/token", exchange -> {
            int count = requests.incrementAndGet();
            incoming.set(List.of(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), UTF_8)));
            reply(exchange, 200, SUCCESS.formatted(TOKEN + count));
        });
        Request request = request("read write+one&other");
        var first = handler.execute(request, context);
        assertEquals(List.of("POST", "/token", "application/x-www-form-urlencoded", BASIC,
                "grant_type=client_credentials&scope=read+write%2Bone%26other"), incoming.get());
        assertEquals("Bearer " + TOKEN + 1, first.authorization().reveal());
        assertEquals("[REDACTED]", first.authorization().toString());
        assertTrue(first.toString().contains("[REDACTED]"));
        assertFalse(first.toString().contains(TOKEN));
        assertFalse(request.toString().contains(SECRET));
        assertTrue(context.sensitive().containsAll(List.of(SECRET, BASIC, TOKEN + 1)));
        assertEquals(1, requests.get());

        var second = handler.execute(request(""), context);
        assertEquals("grant_type=client_credentials", incoming.get().getLast());
        assertEquals("Bearer " + TOKEN + 2, second.authorization().reveal());
        assertEquals(2, requests.get());

        var type = OAuth2ClientCredentialsOperation.type();
        assertEquals(List.of("tokenUrl", "clientId", "clientSecret", "scope", "allowInsecureHttp"),
                type.inputs().stream().map(field -> field.name()).toList());
        var output = assertDoesNotThrow(() -> type.outputs().getFirst());
        assertEquals(1, type.outputs().size());
        assertEquals("authorization", output.name());
        assertEquals("secret", output.valueType());
        assertFalse(output.nullable());
        assertSame(first.authorization(), output.read().apply(first));
        var builder = type.builderFactory().get();
        assertFalse(builder.toString().contains(SECRET));
        try (var files = Files.list(root)) {
            assertEquals(0, files.count());
        }
    }

    @Test void validatesConfigurationWithoutEchoingRejectedValues() {
        var type = OAuth2ClientCredentialsOperation.type();
        var secretField = type.inputs().stream().filter(f -> f.name().equals("clientSecret"))
                .findFirst().orElseThrow();
        assertFailure("OAUTH2_CONFIGURATION", () -> secretField.convert().apply(SECRET));
        var urlField = type.inputs().getFirst();
        assertFailure("OAUTH2_CONFIGURATION", () -> urlField.convert().apply("https://x/?x= " + SENTINEL));
        assertFailure("OAUTH2_CONFIGURATION",
                () -> new Request(endpoint, ID, context.secret("testSecret"), null, false));
        for (String url : List.of("/token", "file:///token", "https:///token",
                "https://user@localhost/token", "https://localhost/token?x=" + SENTINEL,
                "https://localhost/token#" + SENTINEL)) {
            assertFailure("OAUTH2_CONFIGURATION",
                    () -> new Request((URI) urlField.convert().apply(url), ID, context.secret("testSecret"),
                            null, true));
        }
        for (String id : List.of("", " ", "client\r\n", "a".repeat(1025))) {
            assertFailure("OAUTH2_CONFIGURATION",
                    () -> new Request(endpoint, id, context.secret("testSecret"), null, true));
        }
        assertDoesNotThrow(() -> new Request(URI.create("https://localhost/token"),
                ID, context.secret("testSecret"), null, false));
        assertEquals(0, requests.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{malformed", "{\"access_token\":42,\"token_type\":\"Bearer\"}",
            "{\"access_token\":\"x\"}", "{\"access_token\":\"x\",\"token_type\":\"MAC\"}",
            "{\"access_token\":\"\",\"token_type\":\"Bearer\"}",
            "{\"access_token\":\"x\\r\\ny\",\"token_type\":\"Bearer\"}",
            "{\"access_token\":\"x\",\"token_type\":\"Bearer\",\"expires_in\":0}",
            "{\"access_token\":\"x\",\"token_type\":\"Bearer\",\"expires_in\":\"-3\"}",
            "{\"access_token\":\"x\",\"token_type\":\"Bearer\"} {}"})
    void rejectsInvalidTokenResponses(String body) {
        server.createContext("/token", exchange -> reply(exchange, 200, body));
        assertFailure("OAUTH2_RESPONSE", () -> handler.execute(request(null), context));
    }

    @Test void rejectsOversizedResponse() {
        server.createContext("/token", exchange -> reply(exchange, 200, SUCCESS.formatted("x".repeat(65536))));
        assertFailure("OAUTH2_RESPONSE", () -> handler.execute(request(null), context));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500})
    void reportsOnlyTheHttpStatusFromAnErrorResponse(int status) {
        server.createContext("/token", exchange ->
                reply(exchange, status, "{\"error_description\":\"" + SENTINEL + "\"}"));
        assertTrue(assertFailure("OAUTH2_STATUS", () -> handler.execute(request(null), context))
                .getMessage().contains("HTTP " + status));
        assertTrue(context.logs().stream().noneMatch(line -> line.contains(SENTINEL)));
        assertTrue(context.diagnostics().stream().noneMatch(d -> d.toString().contains(SENTINEL)));
    }

    @Test void neverFollowsARedirectToAnotherOrigin() throws Exception {
        HttpServer target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            target.createContext("/", exchange -> {
                requests.incrementAndGet();
                reply(exchange, 200, SUCCESS.formatted(TOKEN));
            });
            target.start();
            server.createContext("/token", exchange -> {
                exchange.getResponseHeaders().set("Location",
                        "http://127.0.0.1:" + target.getAddress().getPort() + "/target");
                reply(exchange, 302, "");
            });
            assertTrue(assertFailure("OAUTH2_STATUS", () -> handler.execute(request(null), context))
                    .getMessage().contains("302"));
            assertEquals(0, requests.get());
        } finally {
            target.stop(0);
        }
    }

    @Test void rejectsExpiredBudgetAndCancellationBeforeResolvingCredentials() {
        context.limit(Duration.ZERO);
        var request = new Request(endpoint, ID, SecretValue.deferred(() -> fail("credential resolved")),
                null, true);
        assertFailure("OAUTH2_TIMEOUT", () -> handler.execute(request, context));
        context.cancel();
        assertThrows(TaskCancelledException.class, () -> handler.execute(request, context));
    }

    @Test void budgetIncludesTheStalledBody() {
        try (var stalled = new StalledBody()) {
            context.limit(Duration.ofSeconds(2));
            assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertFailure("OAUTH2_TIMEOUT", () -> handler.execute(request(null), context)));
            assertEquals(1, requests.get());
            assertEquals(0, stalled.started.getCount());
        }
    }

    @Test void cancellationStopsAStalledBodyWithoutRetry() throws Exception {
        try (var stalled = new StalledBody();
                var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var result = executor.submit(() -> handler.execute(request(null), context));
            try {
                assertTrue(stalled.started.await(3, java.util.concurrent.TimeUnit.SECONDS));
                context.cancel();
                var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> result.get(3, java.util.concurrent.TimeUnit.SECONDS));
                assertInstanceOf(TaskCancelledException.class, failure.getCause());
                assertEquals(1, requests.get());
            } finally {
                result.cancel(true);
            }
        }
    }

    private Request request(String scope) {
        return new Request(endpoint, ID, context.secret("testSecret"), scope, true);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
        }
    }

    private static Failure assertFailure(String code, org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(Failure.class, action);
        assertEquals(code, failure.diagnostic().code());
        assertNull(failure.getCause());
        for (String value : List.of(SECRET, BASIC, TOKEN, SENTINEL)) {
            assertFalse(failure.getMessage().contains(value));
        }
        return failure;
    }

    private final class StalledBody implements AutoCloseable {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        StalledBody() {
            server.createContext("/token", exchange -> {
                try (exchange) {
                    requests.incrementAndGet();
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().write('{');
                    exchange.getResponseBody().flush();
                    started.countDown();
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        @Override public void close() {
            release.countDown();
        }
    }
}
