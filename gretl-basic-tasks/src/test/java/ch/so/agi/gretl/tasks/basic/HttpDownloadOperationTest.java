package ch.so.agi.gretl.tasks.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.SecretValue;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpDownloadOperationTest {
    @TempDir
    Path tempDir;
    private HttpServer server;
    private String base;
    private final AtomicInteger dataCalls = new AtomicInteger();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/data", exchange -> {
            dataCalls.incrementAndGet();
            lastMethod.set(exchange.getRequestMethod());
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "hello-http".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/post", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "posted".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/status", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/data");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/large", exchange -> {
            byte[] body = new byte[4096];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
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
    void getWritesCompleteTargetAndReportsMetadata() throws Exception {
        Path target = tempDir.resolve("out.bin");
        TestOperationContext context = context(Map.of());
        HttpDownloadOperation.HttpDownloadBuilder builder = builder(
                base + "/data?token=query-secret", target);
        builder.setHeaders(Map.of("Authorization",
                new HttpDownloadOperation.HeaderValue("Authorization", "Bearer header-secret", null)));
        HttpDownloadResult result = new HttpDownloadOperation.HttpDownloadHandler()
                .execute(builder.build(), context);

        assertEquals("hello-http", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(10, result.bytes());
        assertEquals(200, result.status());
        assertEquals(64, result.sha256().length());
        assertEquals("GET", lastMethod.get());
        assertEquals("Bearer header-secret", lastAuthorization.get());
        assertEquals(64, result.sha256().length());
        assertFalse(result.file().toString().contains("query-secret"));
        assertEquals(1, dataCalls.get());
    }

    @Test
    void postSendsBodyAndSecretIsRegisteredNotLogged() throws Exception {
        Path target = tempDir.resolve("post.bin");
        TestOperationContext context = context(Map.of());
        HttpDownloadOperation.HttpDownloadBuilder builder = builder(base + "/post", target);
        builder.setMethod("POST");
        builder.setBodyText("grant_type=client_credentials");
        builder.setHeaders(Map.of("Authorization",
                new HttpDownloadOperation.HeaderValue("Authorization", null,
                        SecretValue.deferred(() -> "Bearer secret-token"))));

        HttpDownloadResult result = new HttpDownloadOperation.HttpDownloadHandler()
                .execute(builder.build(), context);

        assertEquals("posted", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals("POST", lastMethod.get());
        assertEquals("grant_type=client_credentials", lastBody.get());
        assertEquals("Bearer secret-token", lastAuthorization.get());
        assertTrue(context.sensitive().contains("Bearer secret-token"));
    }

    @Test
    void nonSuccessLimitRedirectAndExistingTargetDoNotProduceSuccess() throws Exception {
        HttpDownloadOperation.HttpDownloadHandler handler =
                new HttpDownloadOperation.HttpDownloadHandler();
        TestOperationContext context = context(Map.of());
        assertThrows(HttpTaskException.class, () -> handler.execute(
                builder(base + "/status", tempDir.resolve("status.bin")).build(), context));
        assertFalse(Files.exists(tempDir.resolve("status.bin")));

        HttpDownloadOperation.HttpDownloadBuilder limited = builder(
                base + "/large", tempDir.resolve("large.bin"));
        limited.setMaxBytes(100L);
        assertThrows(HttpTaskException.class, () -> handler.execute(limited.build(), context));
        assertFalse(Files.exists(tempDir.resolve("large.bin")));

        assertThrows(HttpTaskException.class, () -> handler.execute(
                builder(base + "/redirect", tempDir.resolve("redirect.bin")).build(), context));
        assertFalse(Files.exists(tempDir.resolve("redirect.bin")));

        Path existing = tempDir.resolve("existing.bin");
        Files.writeString(existing, "keep");
        int before = dataCalls.get();
        assertThrows(HttpTaskException.class, () -> handler.execute(
                builder(base + "/data", existing).build(), context));
        assertEquals("keep", Files.readString(existing));
        assertEquals(before, dataCalls.get());
    }

    @Test
    void overwriteReplacesExistingTargetOnlyAfterSuccess() throws Exception {
        Path target = tempDir.resolve("replace.bin");
        Files.writeString(target, "old-content");
        HttpDownloadOperation.HttpDownloadBuilder builder = builder(base + "/data", target);
        builder.setOverwrite(true);

        HttpDownloadResult result = new HttpDownloadOperation.HttpDownloadHandler()
                .execute(builder.build(), context(Map.of()));

        assertEquals("hello-http", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(target.toAbsolutePath().normalize(), result.file());
        assertEquals(1, dataCalls.get());
    }

    @Test
    void failedOverwriteKeepsExistingTarget() throws Exception {
        Path target = tempDir.resolve("keep.bin");
        Files.writeString(target, "old-content");
        HttpDownloadOperation.HttpDownloadBuilder builder = builder(base + "/status", target);
        builder.setOverwrite(true);

        assertThrows(HttpTaskException.class, () ->
                new HttpDownloadOperation.HttpDownloadHandler()
                        .execute(builder.build(), context(Map.of())));
        assertEquals("old-content", Files.readString(target, StandardCharsets.UTF_8));
        assertTrue(Files.list(tempDir).noneMatch(path -> path.getFileName()
                .toString().startsWith("keep.bin.")));
    }

    @Test
    void overwriteRejectsDirectoriesAndSymlinks() throws Exception {
        HttpDownloadOperation.HttpDownloadHandler handler =
                new HttpDownloadOperation.HttpDownloadHandler();
        Path directory = Files.createDirectories(tempDir.resolve("target-dir"));
        HttpDownloadOperation.HttpDownloadBuilder directoryBuilder =
                builder(base + "/data", directory);
        directoryBuilder.setOverwrite(true);
        HttpTaskException directoryError = assertThrows(HttpTaskException.class,
                () -> handler.execute(directoryBuilder.build(), context(Map.of())));
        assertTrue(directoryError.getMessage().startsWith("HTTP_TARGET_UNSAFE:"));

        Path real = tempDir.resolve("real.bin");
        Files.writeString(real, "real");
        Path link = tempDir.resolve("link.bin");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException exception) {
            return;
        }
        HttpDownloadOperation.HttpDownloadBuilder linkBuilder = builder(base + "/data", link);
        linkBuilder.setOverwrite(true);
        HttpTaskException linkError = assertThrows(HttpTaskException.class,
                () -> handler.execute(linkBuilder.build(), context(Map.of())));
        assertTrue(linkError.getMessage().startsWith("HTTP_TARGET_UNSAFE:"));
        assertEquals("real", Files.readString(real));
    }

    @Test
    void invalidRequestsFailBeforeNetwork() {
        TestOperationContext context = context(Map.of());
        HttpDownloadOperation.HttpDownloadBuilder getWithBody = builder(base + "/data",
                tempDir.resolve("x"));
        getWithBody.setBodyText("not allowed");
        assertThrows(IllegalArgumentException.class, getWithBody::build);

        HttpDownloadOperation.HttpDownloadBuilder badUrl = new HttpDownloadOperation.HttpDownloadBuilder();
        badUrl.setUrl(URI.create("ftp://example.invalid/file"));
        badUrl.setOutput(tempDir.resolve("x"));
        assertThrows(IllegalArgumentException.class, badUrl::build);
    }

    private HttpDownloadOperation.HttpDownloadBuilder builder(String url, Path target) {
        HttpDownloadOperation.HttpDownloadBuilder builder =
                new HttpDownloadOperation.HttpDownloadBuilder();
        builder.setUrl(URI.create(url));
        builder.setOutput(target);
        return builder;
    }

    private TestOperationContext context(Map<String, Object> parameters) {
        return new TestOperationContext(tempDir, parameters, Map.of(), Map.of());
    }
}
