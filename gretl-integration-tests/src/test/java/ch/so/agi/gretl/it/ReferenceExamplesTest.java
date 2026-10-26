package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.gretl.cli.GretlCli;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Definition checks complement, rather than replace, the real fixture job ITs. */
class ReferenceExamplesTest {
    @TempDir Path directory;

    @Test void everyPublishedTaskHasALoadableCompleteExample() throws Exception {
        Path reference = Path.of("../docs/reference/reference.adoc").toAbsolutePath().normalize();
        String document = Files.readString(reference);
        var registry = OperationRegistry.load(getClass().getClassLoader());
        var source = Pattern.compile("\\[source,groovy]\\R----\\R(.*?)\\R----", Pattern.DOTALL);
        for (var operation : registry.all()) {
            String anchor = "[[task-" + operation.symbol().toLowerCase(java.util.Locale.ROOT) + "]]";
            int start = document.indexOf(anchor);
            assertTrue(start >= 0, operation.symbol());
            int end = document.indexOf("\n[[task-", start + anchor.length());
            String chapter = document.substring(start, end < 0 ? document.length() : end);
            var example = source.matcher(chapter);
            assertTrue(example.find(), "Missing example: " + operation.symbol());
            String script = expandIncludes(example.group(1), reference.getParent());
            assertTrue(script.contains("defaults "), "Missing target: " + operation.symbol());
            // A script named Csv2Excel.groovy would introduce a Groovy class
            // that shadows the operation symbol in the snippet itself.
            Path exampleDirectory = directory.resolve(operation.symbol());
            Files.createDirectories(exampleDirectory);
            Path job = exampleDirectory.resolve("job.groovy");
            Files.writeString(job, script);
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            var cli = new GretlCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8),
                    Map.of("GRETL_LANGUAGE", "de"), directory, getClass().getClassLoader());
            assertEquals(0, cli.run(new String[] {"check", job.toString()}),
                    operation.symbol() + ": " + err.toString(StandardCharsets.UTF_8));
        }
        assertFalse(Files.exists(directory.resolve("gretl-reports")));
    }

    /** Only the two include forms used by the reference's executable recipes. */
    private static String expandIncludes(String snippet, Path base) throws Exception {
        var include = Pattern.compile("^include::([^\\[]+)\\[(?:tags=([^]]+))?]$");
        StringBuilder expanded = new StringBuilder();
        for (String line : snippet.lines().toList()) {
            var match = include.matcher(line);
            if (!match.matches()) {
                expanded.append(line).append('\n');
                continue;
            }
            String content = Files.readString(base.resolve(match.group(1)).normalize());
            if (match.group(2) == null) {
                expanded.append(content).append('\n');
                continue;
            }
            Set<String> wanted = Set.of(match.group(2).split(";"));
            Set<String> active = new HashSet<>();
            Set<String> found = new HashSet<>();
            var marker = Pattern.compile("// (tag|end)::([^\\[]+)\\[]");
            for (String includedLine : content.lines().toList()) {
                var tag = marker.matcher(includedLine);
                if (tag.matches()) {
                    if (tag.group(1).equals("tag")) {
                        active.add(tag.group(2));
                        found.add(tag.group(2));
                    } else {
                        active.remove(tag.group(2));
                    }
                } else if (active.stream().anyMatch(wanted::contains)) {
                    expanded.append(includedLine).append('\n');
                }
            }
            assertTrue(found.containsAll(wanted), "Missing include tags: " + wanted);
        }
        return expanded.toString();
    }
}
