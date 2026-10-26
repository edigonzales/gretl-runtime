package ch.so.agi.gretl.it;

import ch.so.agi.gretl.cli.GretlCli;
import ch.so.agi.gretl.runtime.logging.Json;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runs a real Groovy job through the public CLI with a generated runtime config. */
final class JobRunner {
    private JobRunner() {
    }

    static Result run(Path root, String script, Map<String, Object> config)
            throws Exception {
        Path job = root.resolve("job.groovy");
        Files.writeString(job, script, StandardCharsets.UTF_8);
        Path configFile = root.resolve("runtime.json");
        Files.writeString(configFile, Json.write(config), StandardCharsets.UTF_8);
        Path report = root.resolve("report.json");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        GretlCli cli = new GretlCli(
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                Map.of("GRETL_LANGUAGE", "de"), root,
                JobRunner.class.getClassLoader());
        int code = cli.run(new String[] {
                "run", job.toString(), "--config", configFile.toString(),
                "--report", report.toString()
        });
        return new Result(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8), report);
    }

    static Map<String, Object> baseConfig(Path root) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("schemaVersion", 1L);
        config.put("runtime", Map.of(
                "workRoot", root.resolve("work").toString(),
                "reportRoot", root.resolve("reports").toString()));
        config.put("secrets", new LinkedHashMap<String, Object>());
        config.put("taskConfig", new LinkedHashMap<String, Object>());
        return config;
    }

    @SuppressWarnings("unchecked")
    static void secret(Path root, Map<String, Object> config, String alias, String value)
            throws Exception {
        String file = "secret-" + alias + ".txt";
        Files.writeString(root.resolve(file), value, StandardCharsets.UTF_8);
        try { Files.setPosixFilePermissions(root.resolve(file), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
        catch (UnsupportedOperationException ignored) { }
        ((Map<String, Object>) config.get("secrets")).put(alias, Map.of("file", file));
    }

    @SuppressWarnings("unchecked")
    static void taskConfig(Map<String, Object> config, String alias,
                           Map<String, Object> values) {
        ((Map<String, Object>) config.get("taskConfig")).put(alias, values);
    }

    record Result(int code, String out, String err, Path report) {
    }
}
