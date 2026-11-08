package ch.so.agi.gretl.tasks.io.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.tasks.io.TestOperationContext;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CurlMultipartProcessTest {
    private static final int BLOCK_BYTES = 64 * 1024;
    private static final int FILE_BYTES = 128 * 1024 * 1024;

    @TempDir
    Path root;

    @Test
    void streamsLargerThanHeapAndHandlesEarlyConnectionClose() throws Exception {
        Path upload = root.resolve("payload.bin");
        byte[] block = new byte[BLOCK_BYTES];
        new java.util.Random(51).nextBytes(block);
        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        try (var output = Files.newOutputStream(upload)) {
            for (int remaining = FILE_BYTES; remaining > 0; remaining -= block.length) {
                output.write(block);
                expected.update(block);
            }
        }

        var received = new CompletableFuture<byte[]>();
        var aborted = new CompletableFuture<Integer>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/upload", exchange -> {
            try (exchange) {
                String type = exchange.getRequestHeaders().getFirst("Content-Type");
                String prefix = "multipart/form-data; boundary=";
                assertTrue(type.startsWith(prefix), type);
                String delimiter = "--" + type.substring(prefix.length());
                byte[] preamble = (delimiter + "\r\nContent-Disposition: form-data; "
                        + "name=\"file\"; filename=\"payload.bin\"\r\n"
                        + "Content-Type: application/octet-stream\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8);
                var body = exchange.getRequestBody();
                assertArrayEquals(preamble, body.readNBytes(preamble.length));
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[BLOCK_BYTES];
                long bytes = 0;
                while (bytes < FILE_BYTES) {
                    int count = body.read(buffer, 0, (int) Math.min(buffer.length, FILE_BYTES - bytes));
                    if (count < 0) {
                        throw new AssertionError("File part ended after " + bytes + " bytes");
                    }
                    digest.update(buffer, 0, count);
                    bytes += count;
                }
                assertEquals(FILE_BYTES, bytes);
                byte[] ending = ("\r\n" + delimiter + "--\r\n").getBytes(StandardCharsets.UTF_8);
                assertArrayEquals(ending, body.readNBytes(ending.length));
                assertEquals(-1, body.read());
                exchange.sendResponseHeaders(200, -1);
                received.complete(digest.digest());
            } catch (Exception | AssertionError failure) {
                received.completeExceptionally(failure);
            }
        });
        server.createContext("/abort", exchange -> {
            try (exchange) {
                aborted.complete(exchange.getRequestBody().read());
            } catch (Exception failure) {
                aborted.completeExceptionally(failure);
            }
        });
        server.start();
        try {
            Path java = Path.of(System.getProperty("java.home"), "bin", "java");
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            Path log = root.resolve("curl-child.log");
            Process child = new ProcessBuilder(java.toString(), "-Xmx48m", "-cp", classpath,
                    CurlMultipartProcessTest.class.getName(), upload.toString(),
                    "http://127.0.0.1:" + server.getAddress().getPort())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                assertTrue(child.waitFor(45, TimeUnit.SECONDS), "Curl child exceeded its test deadline");
                assertEquals(0, child.exitValue(), () -> readLog(log));
                assertArrayEquals(expected.digest(), received.get(5, TimeUnit.SECONDS));
                assertEquals((int) '-', aborted.get(5, TimeUnit.SECONDS));
            } finally {
                if (child.isAlive()) {
                    child.destroyForcibly();
                    child.waitFor(5, TimeUnit.SECONDS);
                }
            }
        } finally {
            server.stop(0);
        }
    }

    public static void main(String[] args) throws Exception {
        Path upload = Path.of(args[0]);
        var context = new TestOperationContext(upload.getParent(), Map.of(), Map.of(), Map.of());
        var handler = new CurlOperation.CurlHandler();
        CurlOperation.CurlResult result = handler.execute(request(args[1] + "/upload", upload), context);
        assertEquals(200, result.status());
        CurlTaskException failure = org.junit.jupiter.api.Assertions.assertThrows(CurlTaskException.class,
                () -> handler.execute(request(args[1] + "/abort", upload), context));
        assertEquals("CURL_TRANSPORT", failure.code());
    }

    private static CurlOperation.CurlRequest request(String url, Path upload) {
        return new CurlOperation.CurlBuilder().setServerUrl(URI.create(url)).setExpectedStatusCode(200)
                .setMaxBytes(1L)
                .setFormData(Map.of("file", CurlOperation.FormValue.ofFile(upload))).build();
    }

    private static String readLog(Path log) {
        try {
            return Files.readString(log);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
