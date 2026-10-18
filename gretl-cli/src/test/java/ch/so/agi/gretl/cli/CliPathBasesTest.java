package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliPathBasesTest {
    @TempDir
    Path tempDir;

    @Test
    void jobRootStartCwdConfigDirAndWorkPathStayDistinct() throws Exception {
        Path jobs = Files.createDirectories(tempDir.resolve("jobs"));
        Path configDir = Files.createDirectories(tempDir.resolve("conf"));
        Files.writeString(jobs.resolve("local.txt"), "job-file");
        Files.writeString(tempDir.resolve("external.txt"), "start-cwd");
        Files.writeString(configDir.resolve("secret.txt"), "secret-from-config\n");
        Files.writeString(jobs.resolve("job.groovy"), """
                parameter('input', type: 'path', required: true, description: 'extern')
                action('check') {
                    assert file('local.txt').toFile().text == 'job-file'
                    assert parameter('input').getFileName().toString() == 'external.txt'
                    assert ctx.path('external.txt').toFile().text == 'start-cwd'
                    assert secret('s').reveal() == 'secret-from-config'
                    assert workPath('sub/inside.txt').parent.toFile().exists()
                    return 'ok'
                }
                defaults 'check'
                """);
        Files.writeString(configDir.resolve("runtime.json"), """
                {
                  "schemaVersion": 1,
                  "runtime": {
                    "workRoot": "work",
                    "reportRoot": "reports"
                  },
                  "secrets": {"s": {"file": "secret.txt"}}
                }
                """);
        Result result = run("run", "jobs/job.groovy",
                "--config", "conf/runtime.json",
                "--param", "input=external.txt",
                "--report", "report.json");
        assertEquals(0, result.code(), result.err());
        assertTrue(Files.exists(configDir.resolve("work")));
        assertTrue(Files.exists(configDir.resolve("reports")));
        assertTrue(Files.exists(tempDir.resolve("report.json")));
    }

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new GretlCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), Map.of(), tempDir,
                getClass().getClassLoader()).run(args);
        return new Result(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int code, String out, String err) {
    }
}
