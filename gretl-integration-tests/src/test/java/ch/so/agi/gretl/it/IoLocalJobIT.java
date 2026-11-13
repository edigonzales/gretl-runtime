package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IoLocalJobIT {
    @Test void extractArchiveBytes() throws Exception {
        var result = run("extract", "file", "http://unused.invalid", Map.of()); assertEquals(0, result.code(), result.err());
        assertArrayEquals(Files.readAllBytes(ArtifactJobRunner.fixture("io").resolve("payload.txt")), Files.readAllBytes(result.directory().resolve("output/nested/payload.txt")));
    }
    @Test void filtersRenamesBundlesAndProcessesSeveralArchives() throws Exception {
        Path root = ArtifactJobRunner.directory("archive-delivery", ArtifactJobRunner.Mode.SCRIPT);
        Path input = Files.createDirectory(root.resolve("input"));
        Path output = root.resolve("output");
        writeZip(input.resolve("survey.zip"), Map.of(
                "nested/survey.xtf", "<survey/>", "backup/old.xtf", "<old/>", "note.txt", "exclude"));
        writeZip(input.resolve("region-a.zip"), Map.of("region.xtf", "<region id='a'/>"));
        writeZip(input.resolve("region-b.zip"), Map.of("nested/region.xtf", "<region id='b'/>"));
        Files.writeString(output.resolve("previous.xtf"), "obsolete");
        var result = ArtifactJobRunner.run(new ArtifactJobRunner.Request(
                "archive-delivery", ArtifactJobRunner.fixture("archive-delivery"), root, List.of(),
                Map.of("input", input, "output", output), Map.of(), JobRunner.baseConfig(root),
                Map.of(), Duration.ofSeconds(60)));
        assertEquals(0, result.code(), result.out() + result.err());
        try (var archive = new java.util.zip.ZipFile(output.resolve("delivery.zip").toFile())) {
            assertEquals(List.of("data.xtf", "delivery.toml"), archive.stream().map(java.util.zip.ZipEntry::getName).toList());
            try (var xtf = archive.getInputStream(archive.getEntry("data.xtf"));
                 var toml = archive.getInputStream(archive.getEntry("delivery.toml"))) {
                assertArrayEquals("<survey/>".getBytes(java.nio.charset.StandardCharsets.UTF_8), xtf.readAllBytes());
                assertArrayEquals(Files.readAllBytes(ArtifactJobRunner.fixture("archive-delivery").resolve("delivery.toml")),
                        toml.readAllBytes());
            }
        }
        assertEquals("<region id='a'/>", Files.readString(output.resolve("region-a.xtf")));
        assertEquals("<region id='b'/>", Files.readString(output.resolve("region-b.xtf")));
        assertFalse(Files.exists(output.resolve("previous.xtf")));
    }

    private static void writeZip(Path file, Map<String, String> entries) throws Exception {
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"file", "resource", "set"})
    void originalXsltVariantsAndBusinessContent(String variant) throws Exception {
        var result = run("xsl", variant, "http://unused.invalid", Map.of()); assertEquals(0, result.code(), result.err());
        String content = Files.readString(result.directory().resolve("output/MeldungAnGeometer_G-0098981_20230214_104054_Koordinaten.xtf"));
        for (String expected : List.of("<SO_AGI_SGV_Meldungen_20221109.Meldungen BID=\"SO_AGI_SGV_Meldungen_20221109.Meldungen\">", "<Grundstuecksnummer>1505</Grundstuecksnummer>",
                "<Gebaeudebezeichnung>Reine Wohngebäude (Wohnnutzung ausschliesslich)</Gebaeudebezeichnung>")) assertTrue(content.contains(expected), expected);
        if (variant.equals("set")) assertTrue(Files.isRegularFile(result.directory().resolve("output/MeldungAnGeometer_mehrere_gebaeude_mehrere_grundstuecke.xtf")));
    }
    static void imageHttpDownloadAndFailure() throws Exception {
        byte[] payload = Files.readAllBytes(ArtifactJobRunner.fixture("io").resolve("payload.txt"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        server.createContext("/download", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.createContext("/failure", exchange -> { exchange.sendResponseHeaders(401, -1); exchange.close(); });
        server.start();
        try {
            String host = "http://127.0.0.1:" + server.getAddress().getPort();
            String image = "http://" + System.getProperty("gretl.test.httpImageHost", "services") + ":" + server.getAddress().getPort();
            var endpoints = Map.of("http", new ArtifactJobRunner.Endpoint(host, image));
            var download = run("download", "file", host + "/download", endpoints, ArtifactJobRunner.Mode.IMAGE);
            assertEquals(0, download.code(), download.err());
            assertArrayEquals(payload, Files.readAllBytes(download.directory().resolve("output/download.bin")));
            var failure = run("curl", "get", host + "/failure", endpoints, ArtifactJobRunner.Mode.IMAGE);
            assertEquals(10, failure.code(), failure.err());
            assertEquals("FAILED", JobTestJson.execution(JobTestJson.read(failure.report())).get("status"));
            assertFalse(Files.exists(failure.directory().resolve("output/response.txt")));
        } finally { server.stop(0); }
    }

    @Test void localHttpDownloadCurlRequestVariantsAndStatusFailure() throws Exception {
        var requests = new CopyOnWriteArrayList<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        byte[] payload = Files.readAllBytes(ArtifactJobRunner.fixture("io").resolve("payload.txt"));
        server.createContext("/download", exchange -> { exchange.sendResponseHeaders(200, payload.length); exchange.getResponseBody().write(payload); exchange.close(); });
        server.createContext("/curl", exchange -> { requests.add(exchange.getRequestMethod() + " " + new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] body = "{\"success\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close(); });
        server.createContext("/failure", exchange -> { exchange.sendResponseHeaders(401, -1); exchange.close(); }); server.start();
        try {
            String host = "http://127.0.0.1:" + server.getAddress().getPort();
            var endpoints = Map.of("http", new ArtifactJobRunner.Endpoint(host, "http://" + System.getProperty("gretl.test.httpImageHost", "services") + ":" + server.getAddress().getPort()));
            var download = run("download", "file", host + "/download", endpoints); assertEquals(0, download.code(), download.err());
            assertArrayEquals(payload, Files.readAllBytes(download.directory().resolve("output/download.bin")));
            for (String variant : List.of("get", "data", "form", "binary")) {
                var curl = run("curl", variant, host + "/curl", endpoints); assertEquals(0, curl.code(), curl.err());
                assertEquals("{\"success\":true}", Files.readString(curl.directory().resolve("output/response.txt")));
            }
            assertEquals(4, requests.size()); assertTrue(requests.get(0).startsWith("GET"));
            assertTrue(requests.get(1).contains("grant_type=client_credentials")); assertTrue(requests.get(2).contains("original payload ü")); assertTrue(requests.get(3).contains("original payload ü"));
            var failure = run("curl", "get", host + "/failure", endpoints); assertEquals(10, failure.code(), failure.err());
            String report = Files.readString(failure.report()); assertTrue(report.contains("CurlTaskException"), report); assertTrue(report.contains("HTTP-Status entspricht nicht expectedStatusCode"), report);
            assertFalse(Files.exists(failure.directory().resolve("output/response.txt")));
        } finally { server.stop(0); }
    }
    @Test void originalCurlBasicAuthenticationMultipartBinaryAndBodyFailure() throws Exception {
        record Received(String path, String authorization, byte[] body) {}
        var requests = new CopyOnWriteArrayList<Received>();
        byte[] readme = Files.readAllBytes(ArtifactJobRunner.fixture("io").resolve("download.md"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(new Received(path, exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestBody().readAllBytes()));
            byte[] body = path.equals("/README.md") ? readme : (path.endsWith("failure") ? "\"success\":false" : "\"success\":true").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(path.equals("/typo3/api/digiplan") ? 202 : 200, body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        try {
            String host = "http://127.0.0.1:" + server.getAddress().getPort();
            var endpoints = Map.of("http", new ArtifactJobRunner.Endpoint(host, "http://" + System.getProperty("gretl.test.httpImageHost", "services") + ":" + server.getAddress().getPort()));
            var form = run("curl-original", "form", host + "/data_agg/interlis/import", endpoints); assertEquals(0, form.code(), form.err());
            var received = requests.getLast(); assertEquals("/data_agg/interlis/import", received.path());
            assertEquals("Basic " + java.util.Base64.getEncoder().encodeToString("fooUser:barPwd".getBytes(java.nio.charset.StandardCharsets.UTF_8)), received.authorization());
            assertTrue(received.body().length > 500); String multipart = new String(received.body(), java.nio.charset.StandardCharsets.ISO_8859_1);
            for (String expected : List.of("name=\"topic\"", "npl_waldgrenzen", "name=\"lv95_file\"; filename=\"test.xtf.zip\"")) assertTrue(multipart.contains(expected), multipart);
            var failed = run("curl-original", "form", host + "/failure", endpoints); assertEquals(10, failed.code(), failed.err());
            assertTrue(Files.readString(failed.report()).contains("expectedBody"));
            var binary = run("curl-original", "planregister", host + "/typo3/api/digiplan", endpoints); assertEquals(0, binary.code(), binary.err());
            received = requests.getLast(); assertEquals("/typo3/api/digiplan", received.path()); assertEquals("Basic Zm9vVXNlcjpiYXJQd2Q=", received.authorization()); assertTrue(received.body().length > 600);
            assertArrayEquals(Files.readAllBytes(ArtifactJobRunner.fixture("io").resolve("foo.xml.gz")), received.body());
            var download = run("curl-original", "download", host + "/README.md", endpoints); assertEquals(0, download.code(), download.err());
            String text = Files.readString(download.directory().resolve("output/README.md")); assertTrue(text.contains("_GRETL_")); assertTrue(text.contains("License"));
            assertArrayEquals(readme, Files.readAllBytes(download.directory().resolve("output/README.md")));
        } finally { server.stop(0); }
    }

    private static ArtifactJobRunner.Result run(String target, String variant, String url, Map<String, ArtifactJobRunner.Endpoint> endpoints) throws Exception {
        return run(target, variant, url, endpoints, ArtifactJobRunner.Mode.SCRIPT);
    }

    private static ArtifactJobRunner.Result run(String target, String variant, String url,
                                                 Map<String, ArtifactJobRunner.Endpoint> endpoints, ArtifactJobRunner.Mode mode) throws Exception {
        Path root = ArtifactJobRunner.directory("io-" + target + "-" + variant, mode);
        var config = JobRunner.baseConfig(root); JobRunner.secret(root, config, "curlPassword", "barPwd");
        return ArtifactJobRunner.run(new ArtifactJobRunner.Request("io-" + target + "-" + variant, ArtifactJobRunner.fixture("io"), root, List.of(target),
                Map.of("output", root.resolve("output"), "url", url, "variant", variant), Map.of(), config, endpoints, Duration.ofSeconds(60)), mode);
    }
}
