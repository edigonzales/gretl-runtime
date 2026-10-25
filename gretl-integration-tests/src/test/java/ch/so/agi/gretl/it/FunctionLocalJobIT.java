package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FunctionLocalJobIT {
    @Test void standardDistributionExecutesAdditionalFunctionAndDiagnosesMissingSelectedPlugin() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
        server.createContext("/exists", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext("/absent", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(404, -1); exchange.close(); });
        server.start();
        try {
            String host = "http://127.0.0.1:" + server.getAddress().getPort();
            String image = "http://" + System.getProperty("gretl.test.httpImageHost", "services") + ":" + server.getAddress().getPort();
            var endpoints = Map.of("http", new ArtifactJobRunner.Endpoint(host, image));
            for (String target : List.of("validate", "ili-function")) {
                int beforeValid = calls.get();
                var valid = run(target, host + "/exists", Map.of(), endpoints);
                assertEquals(0, valid.code(), valid.err()); assertTrue(calls.get() > beforeValid, "Function must perform its HTTP check");
                int previous = calls.get();
                var invalid = run(target, host + "/absent", Map.of(), endpoints);
                assertEquals(10, invalid.code(), invalid.err()); assertTrue(calls.get() > previous);
                assertTrue(diagnostics(invalid, target).contains("Constraint"));
                var missing = run(target, host + "/exists", Map.of("JAVA_TOOL_OPTIONS", "-Dch.ehi.iox-ili.pluginClasses="), endpoints);
                assertEquals(10, missing.code(), missing.err());
                String diagnostic = diagnostics(missing, target);
                assertTrue(diagnostic.contains("isHttpResource"), diagnostic); assertTrue(diagnostic.toLowerCase().contains("function"), diagnostic);
                var broken = run(target, host + "/exists", Map.of("JAVA_TOOL_OPTIONS", "-Dch.ehi.iox-ili.pluginClasses=missing.SelectedIoxPlugin"), endpoints);
                assertEquals(10, broken.code(), broken.err());
                String report = Files.readString(broken.report()); assertTrue(report.contains(target.equals("validate") ? "VALIDATION_FUNCTION" : "VALIDATION_ENGINE_FAILED"), report); assertTrue(report.contains("missing.SelectedIoxPlugin"), report);
                var duplicate = run(target, host + "/exists", Map.of("JAVA_TOOL_OPTIONS", "-Dch.ehi.iox-ili.pluginClasses=ch.so.agi.ilivalidator.ext.IsHttpResourceIoxPlugin,ch.so.agi.ilivalidator.ext.IsHttpResourceIoxPlugin"), endpoints);
                assertEquals(10, duplicate.code(), duplicate.err()); report = Files.readString(duplicate.report());
                assertTrue(report.contains(target.equals("validate") ? "VALIDATION_FUNCTION_DUPLICATE" : "Duplicate qualified INTERLIS function"), report); assertTrue(report.contains("SO_FunctionsExt.isHttpResource"), report);
            }
        } finally { server.stop(0); }
    }
    private String diagnostics(ArtifactJobRunner.Result result, String target) throws Exception {
        if (target.equals("validate")) return Files.readString(result.directory().resolve("output/validation.log"));
        StringBuilder text = new StringBuilder();
        try (var paths = Files.walk(result.directory().resolve("work"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().equals("validation-report.txt")).toList()) text.append(Files.readString(path));
        }
        return text.toString();
    }
    private ArtifactJobRunner.Result run(String target, String url, Map<String, String> environment, Map<String, ArtifactJobRunner.Endpoint> endpoints) throws Exception {
        Path root = ArtifactJobRunner.directory("additional-function");
        return ArtifactJobRunner.run(new ArtifactJobRunner.Request("additional-function", ArtifactJobRunner.fixture("functions"), root, List.of(target),
                Map.of("output", root.resolve("output").toString(), "url", url), environment, JobRunner.baseConfig(root), endpoints, Duration.ofSeconds(60)));
    }
}
