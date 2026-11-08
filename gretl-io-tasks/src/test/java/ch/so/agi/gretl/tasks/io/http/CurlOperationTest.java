package ch.so.agi.gretl.tasks.io.http;

import ch.so.agi.gretl.tasks.io.TestOperationContext;

import ch.so.agi.gretl.tasks.io.http.*;
import ch.so.agi.gretl.tasks.io.archive.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.SecretValue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CurlOperationTest {
    @TempDir
    Path tempDir;

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void refusesTargetCreatedDuringResponse() throws Exception {
        Path target = tempDir.resolve("response.txt");
        server.createContext("/late", exchange -> {
            Files.writeString(target, "foreign");
            respond(exchange, 200, "payload");
        });

        CurlTaskException failure = assertThrows(CurlTaskException.class,
                () -> new CurlOperation.CurlHandler().execute(
                        request("/late", 200).setOutputFile(target).build(), context()));
        assertEquals("CURL_TARGET_EXISTS", failure.code());
        assertEquals("publish", failure.phase());
        assertEquals("foreign", Files.readString(target));
        try (var entries = Files.list(tempDir)) {
            assertEquals(List.of(target), entries.toList());
        }
    }

    @Test
    void getWithoutBodyReturnsStatusAndBytes() throws Exception {
        server.createContext("/hello", exchange -> respond(exchange, 200, "hello"));

        CurlOperation.CurlResult result = new CurlOperation.CurlHandler().execute(
                request("/hello", 200).build(), context());

        assertEquals(200, result.status());
        assertEquals(5, result.bytes());
        assertNull(result.outputFile());
    }

    @Test
    void postWithTextBodyAndExpectedBody() throws Exception {
        server.createContext("/echo", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            respond(exchange, 200, "method=" + exchange.getRequestMethod() + ";body=" + body);
        });

        CurlOperation.CurlRequest request = request("/echo", 200)
                .setMethod("POST")
                .setData("grant_type=client_credentials")
                .setExpectedBody("body=grant_type=client_credentials")
                .build();
        CurlOperation.CurlResult result = new CurlOperation.CurlHandler()
                .execute(request, context());

        assertEquals(200, result.status());
        assertTrue(result.bytes() > 0);
    }

    @Test
    void sendsExactMultipartPartsWithSafeFilenameParameters() throws Exception {
        Path upload = Files.writeString(tempDir.resolve("über \"test\"\\.txt"), "file-content");
        Path empty = Files.createFile(tempDir.resolve("empty.bin"));
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> payload = new AtomicReference<>();
        server.createContext("/form", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "ok");
        });

        CurlOperation.CurlRequest request = request("/form", 200)
                .setFormData(Map.of(
                        "topic", CurlOperation.FormValue.ofText("npl"),
                        "file", CurlOperation.FormValue.ofFile(upload),
                        "empty", CurlOperation.FormValue.ofFile(empty)))
                .build();
        CurlOperation.CurlResult result = new CurlOperation.CurlHandler()
                .execute(request, context());

        assertEquals(200, result.status());
        String prefix = "multipart/form-data; boundary=";
        assertTrue(contentType.get().startsWith(prefix));
        String delimiter = "--" + contentType.get().substring(prefix.length());
        String[] parts = payload.get().split(Pattern.quote(delimiter), -1);
        assertEquals(5, parts.length);
        assertEquals("", parts[0]);
        assertEquals("--\r\n", parts[4]);
        assertEquals(Set.of(
                "\r\nContent-Disposition: form-data; name=\"topic\"\r\n\r\nnpl\r\n",
                "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"über _test_\\\\.txt\"\r\n"
                        + "Content-Type: application/octet-stream\r\n\r\nfile-content\r\n",
                "\r\nContent-Disposition: form-data; name=\"empty\"; filename=\"empty.bin\"\r\n"
                        + "Content-Type: application/octet-stream\r\n\r\n\r\n"),
                Set.of(parts[1], parts[2], parts[3]));
    }

    @Test
    void rejectsFilenameAndFieldHeaderInjectionBeforeSending() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/form", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, "ok");
        });
        for (String name : List.of("file\r.txt", "file\n.txt", "file\r\nX-Injected: yes.txt")) {
            Path upload = Files.writeString(tempDir.resolve(name), "payload");
            CurlTaskException failure = assertThrows(CurlTaskException.class,
                    () -> new CurlOperation.CurlHandler().execute(request("/form", 200)
                            .setFormData(Map.of("file", CurlOperation.FormValue.ofFile(upload)))
                            .build(), context()));
            assertEquals("CURL_FILE", failure.code());
            assertEquals("prepare", failure.phase());
            assertEquals("Multipart filename contains a forbidden control character",
                    failure.getCause().getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> request("/form", 200)
                .setFormData(Map.of("field\r\nX-Injected", CurlOperation.FormValue.ofText("x")))
                .build());
        assertEquals(0, requests.get());
    }

    @Test
    void sendsBinaryBodyFromFile() throws Exception {
        Path binary = tempDir.resolve("request.bin");
        byte[] content = {1, 2, 3, 4, 5};
        Files.write(binary, content);
        server.createContext("/binary", exchange -> {
            assertArrayEquals(content, exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, "ok");
        });

        CurlOperation.CurlRequest request = request("/binary", 200)
                .setMethod("POST")
                .setDataBinary(binary)
                .build();
        CurlOperation.CurlResult result = new CurlOperation.CurlHandler()
                .execute(request, context());

        assertEquals(200, result.status());
    }

    @Test
    void basicAuthUsesSecretAndRegistersTokenAsSensitive() throws Exception {
        String expectedToken = Base64.getEncoder().encodeToString(
                "api-user:api-secret".getBytes(StandardCharsets.UTF_8));
        server.createContext("/auth", exchange -> {
            assertEquals("Basic " + expectedToken,
                    exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "ok");
        });
        TestOperationContext context = context(Map.of("apiPassword", "api-secret"));

        CurlOperation.CurlRequest request = request("/auth", 200)
                .setUser("api-user")
                .setPassword(context.secret("apiPassword"))
                .build();
        new CurlOperation.CurlHandler().execute(request, context);

        assertTrue(context.sensitive().contains(expectedToken));
        assertFalse(request.toString().contains("api-secret"));
    }

    @Test
    void wrongStatusAndExpectedBodyFailWithoutWritingOutput() throws Exception {
        server.createContext("/wrong", exchange -> respond(exchange, 418, "teapot"));
        Path output = tempDir.resolve("wrong.txt");

        CurlTaskException status = assertThrows(CurlTaskException.class,
                () -> new CurlOperation.CurlHandler().execute(
                        request("/wrong", 200).setOutputFile(output).build(), context()));
        assertEquals("CURL_STATUS", status.code());
        assertEquals(418, status.status());
        assertFalse(Files.exists(output));

        server.createContext("/body", exchange -> respond(exchange, 200, "actual"));
        CurlTaskException body = assertThrows(CurlTaskException.class,
                () -> new CurlOperation.CurlHandler().execute(
                        request("/body", 200).setExpectedBody("expected").build(), context()));
        assertEquals("CURL_BODY", body.code());
        assertFalse(body.getMessage().contains("actual"));
    }

    @Test
    void writesOutputAtomicallyAndRequiresOverwriteForExistingFile() throws Exception {
        server.createContext("/output", exchange -> respond(exchange, 200, "payload"));
        Path output = tempDir.resolve("response.txt");

        CurlOperation.CurlResult first = new CurlOperation.CurlHandler().execute(
                request("/output", 200).setOutputFile(output).build(), context());
        assertEquals("payload", Files.readString(output, StandardCharsets.UTF_8));
        assertEquals(output.toAbsolutePath().normalize(), first.outputFile());

        CurlTaskException exists = assertThrows(CurlTaskException.class,
                () -> new CurlOperation.CurlHandler().execute(
                        request("/output", 200).setOutputFile(output).build(), context()));
        assertEquals("CURL_TARGET_EXISTS", exists.code());

        new CurlOperation.CurlHandler().execute(
                request("/output", 200).setOutputFile(output).setOverwrite(true).build(),
                context());
        assertEquals("payload", Files.readString(output, StandardCharsets.UTF_8));
    }

    @Test
    void enforcesMaxBytesAndDoesNotFollowRedirects() throws Exception {
        server.createContext("/large", exchange -> respond(exchange, 200, "0123456789"));
        CurlTaskException limit = assertThrows(CurlTaskException.class,
                () -> new CurlOperation.CurlHandler().execute(
                        request("/large", 200).setMaxBytes(5L).build(), context()));
        assertEquals("CURL_LIMIT", limit.code());

        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", uri("/target").toString());
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> respond(exchange, 200, "followed"));
        CurlOperation.CurlResult redirect = new CurlOperation.CurlHandler().execute(
                request("/redirect", 302).build(), context());
        assertEquals(302, redirect.status());
        assertEquals(0, redirect.bytes());
    }

    @Test
    void rejectsInvalidBuildsAndMissingBodyFiles() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> request("/x", 200).setMethod("GET").setData("body").build());
        assertThrows(IllegalArgumentException.class,
                () -> request("/x", 200).setData("body")
                        .setFormData(Map.of("field", CurlOperation.FormValue.ofText("x")))
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> request("/x", 200).setPassword(SecretValue.deferred(() -> "secret"))
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> request("/x", 200).setUser("user")
                        .setPassword(SecretValue.deferred(() -> "secret"))
                        .setHeaders(Map.of("Authorization",
                                new CurlOperation.HeaderValue("Authorization", "Basic x", null)))
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> request("/x", 200).setExpectedBody(" ").build());
        assertThrows(IllegalArgumentException.class,
                () -> request("/x", 200).setMaxBytes(0L).build());

        CurlTaskException missing = assertThrows(CurlTaskException.class,
                () -> new CurlOperation.CurlHandler().execute(
                        request("/x", 200)
                                .setDataBinary(tempDir.resolve("missing.bin")).build(),
                        context()));
        assertEquals("CURL_FILE", missing.code());
    }

    private CurlOperation.CurlBuilder request(String path, int expectedStatus) {
        CurlOperation.CurlBuilder builder = new CurlOperation.CurlBuilder();
        builder.setServerUrl(uri(path));
        builder.setExpectedStatusCode(expectedStatus);
        return builder;
    }

    private TestOperationContext context() {
        return context(Map.of());
    }

    private TestOperationContext context(Map<String, String> secrets) {
        return new TestOperationContext(tempDir, Map.of(), Map.of(), secrets);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
