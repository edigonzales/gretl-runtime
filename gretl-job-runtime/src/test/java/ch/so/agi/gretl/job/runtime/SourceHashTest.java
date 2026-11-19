package ch.so.agi.gretl.job.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources;
import ch.so.agi.gretl.runtime.runner.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SourceHashTest {
    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void singleDefinitionAndHashDescribeTheSameBytesAcrossLoads(boolean include) throws Exception {
        Path job = temp.resolve("job.groovy");
        Path changed = job;
        String original = "action('run') { 'first' }\ndefaults 'run'\n";
        String updated = original.replace("first", "second");
        if (include) {
            Files.writeString(job, "apply from: 'fragment.groovy'\ndefaults 'run'\n");
            changed = temp.resolve("fragment.groovy");
            original = "action('run') { 'first' }\n";
            updated = original.replace("first", "second");
        }
        Files.writeString(changed, original);
        String initialHash = include ? ScriptBundleSources.single(job, Locale.ENGLISH).bundle().sha256() : sha256(original);
        var source = new ScriptJobSource(job);
        var first = load(source);

        Files.writeString(changed, updated);
        var second = load(source);

        assertEquals(initialHash, first.sha256());
        assertEquals("first", run(first.definition(), "run"));
        assertNotEquals(initialHash, second.sha256());
        assertEquals(include ? ScriptBundleSources.single(job, Locale.ENGLISH).bundle().sha256() : sha256(updated),
                second.sha256());
        assertEquals("second", run(second.definition(), "run"));
    }

    @Test
    void projectDefinitionAndHashIncludeCapturedSettingsAndFragments() throws Exception {
        Path settings = Files.writeString(temp.resolve("settings.gretl"), "include 'a'\ndefaults ':a:read'\n");
        Path child = Files.createDirectory(temp.resolve("a")).resolve("job.groovy");
        Files.writeString(child, "apply from: '../fragment.groovy'");
        Path fragment = Files.writeString(temp.resolve("fragment.groovy"), "action('read') { 'first' }");
        var source = new ProjectJobSource(temp);
        var first = load(source);

        Files.writeString(settings, "include 'a'\ndefaults ':a:read', ':a:other'\n");
        Files.writeString(fragment, "action('read') { 'second' }\naction('other') { 'other' }");
        var second = load(source);

        assertNotEquals(first.sha256(), second.sha256());
        assertEquals(List.of(":a:read"), first.definition().defaultTargets());
        assertEquals("first", run(first.definition(), ":a:read"));
        assertEquals(List.of(":a:read", ":a:other"), second.definition().defaultTargets());
        assertEquals("second", run(second.definition(), ":a:read"));
    }

    @Test
    void capturingSourceForHashDoesNotExecuteTopLevelCode() throws Exception {
        Path marker = temp.resolve("marker");
        Path job = Files.writeString(temp.resolve("job.groovy"),
                "new File(new URI('" + marker.toUri() + "')).text = 'executed'");
        ScriptBundleSources.single(job, Locale.ENGLISH);
        assertFalse(Files.exists(marker));
    }

    private JobSource.Loaded load(JobSource source) {
        return source.load(OperationRegistry.fromProviders(List.of()), temp, UserMessageSink.NO_OP, Locale.ENGLISH);
    }

    private Object run(JobDefinition definition, String task) {
        var plan = new ExecutionPlanner().plan(definition, List.of());
        var outcome = new SequentialJobRunner().run(definition, plan, RunInput.builder()
                .jobRoot(definition.jobRoot()).startCwd(temp).workRoot(temp.resolve("work")).build());
        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        return ((ResultSlot.Value) outcome.task(task).orElseThrow().slot()).value();
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
