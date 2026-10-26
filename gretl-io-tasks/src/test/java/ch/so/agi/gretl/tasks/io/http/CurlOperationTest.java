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
    void sendsMultipartFormDataWithTextAndFile() throws Exception {
        Path upload = tempDir.resolve("upload.txt");
        Files.writeString(upload, "file-content", StandardCharsets.UTF_8);
        server.createContext("/form", exchange -> {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            assertTrue(contentType.startsWith("multipart/form-data; boundary="), contentType);
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(body.contains("name=\"topic\""), body);
            assertTrue(body.contains("npl"), body);
            assertTrue(body.contains("filename=\"upload.txt\""), body);
            assertTrue(body.contains("file-content"), body);
            respond(exchange, 200, "ok");
        });

        CurlOperation.CurlRequest request = request("/form", 200)
                .setFormData(Map.of(
                        "topic", CurlOperation.FormValue.ofText("npl"),
                        "file", CurlOperation.FormValue.ofFile(upload)))
                .build();
        CurlOperation.CurlResult result = new CurlOperation.CurlHandler()
                .execute(request, context());

        assertEquals(200, result.status());
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
