package ch.so.agi.gretl.job.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectHashTest {
    @TempDir Path temp;
    @Test
    void prj115HashesOnlyFramedDefinitionSourcesAndIsIndependentOfAbsoluteRoot() throws Exception {
        Path first = tree(temp.resolve("one")); Path second = tree(temp.resolve("two"));
        var source = new ProjectJobSource(first, Locale.ENGLISH);
        String initial = source.sha256();
        assertTrue(initial.matches("[a-f0-9]{64}"));
        assertEquals(initial, source.sha256());
        assertEquals(initial, new ProjectJobSource(second, Locale.ENGLISH).sha256());
        Files.writeString(first.resolve("a/data.txt"), "changed data");
        Files.writeString(first.resolve("a/settings.gretl"), "ignored nested settings");
        Files.createDirectories(first.resolve("notIncluded"));
        Files.writeString(first.resolve("notIncluded/job.groovy"), "ignored job");
        assertEquals(initial, source.sha256());
        Files.writeString(first.resolve("a/job.groovy"), "action('run') { 'changed' }\n");
        String changed = source.sha256(); assertNotEquals(initial, changed);
        Files.writeString(first.resolve("settings.gretl"), "// changed\ninclude 'a'\ndefaults ':a:run'\n");
        assertNotEquals(changed, source.sha256());
        assertEquals(first.toRealPath(), source.jobRoot());
        assertEquals(first.toRealPath(), source.prepareRun(temp.resolve("unused")));
        assertFalse(Files.exists(temp.resolve("unused")));
        assertEquals("one", source.jobName());
    }
    private Path tree(Path root) throws Exception {
        Files.createDirectories(root.resolve("a"));
        Files.writeString(root.resolve("settings.gretl"), "include 'a'\ndefaults ':a:run'\n");
        Files.writeString(root.resolve("a/job.groovy"), "action('run') { throw new AssertionError('not executed by hash') }\n");
        Files.writeString(root.resolve("a/data.txt"), "initial data");
        return root;
    }
}
