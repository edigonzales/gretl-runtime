package ch.so.agi.gretl.job.runtime;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectHashTest {
    @TempDir Path temp;
    @Test
    void hashesOnlyFramedDefinitionSourcesAndIsIndependentOfAbsoluteRoot() throws Exception {
        Path first = tree(temp.resolve("one")); Path second = tree(temp.resolve("two"));
        var source = new ProjectJobSource(first);
        String initial = load(source).sha256();
        assertTrue(initial.matches("[a-f0-9]{64}"));
        assertEquals(initial, load(source).sha256());
        assertEquals(initial, load(new ProjectJobSource(second)).sha256());
        Files.writeString(first.resolve("a/data.txt"), "changed data");
        Files.writeString(first.resolve("a/settings.gretl"), "ignored nested settings");
        Files.createDirectories(first.resolve("notIncluded"));
        Files.writeString(first.resolve("notIncluded/job.groovy"), "ignored job");
        assertEquals(initial, load(source).sha256());
        Files.writeString(first.resolve("a/job.groovy"), "action('run') { 'changed' }\n");
        String changed = load(source).sha256(); assertNotEquals(initial, changed);
        Files.writeString(first.resolve("settings.gretl"), "// changed\ninclude 'a'\ndefaults ':a:run'\n");
        assertNotEquals(changed, load(source).sha256());
        assertEquals(first.toRealPath(), load(source).definition().jobRoot());
        assertEquals("one", source.jobName());
    }
    private JobSource.Loaded load(ProjectJobSource source) {
        return source.load(OperationRegistry.fromProviders(List.of()), temp, UserMessageSink.NO_OP, Locale.ENGLISH);
    }

    private Path tree(Path root) throws Exception {
        Files.createDirectories(root.resolve("a"));
        Files.writeString(root.resolve("settings.gretl"), "include 'a'\ndefaults ':a:run'\n");
        Files.writeString(root.resolve("a/job.groovy"), "action('run') { throw new AssertionError('not executed by hash') }\n");
        Files.writeString(root.resolve("a/data.txt"), "initial data");
        return root;
    }
}
