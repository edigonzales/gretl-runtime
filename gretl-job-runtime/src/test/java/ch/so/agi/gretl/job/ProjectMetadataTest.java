package ch.so.agi.gretl.job;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

class ProjectMetadataTest {
    @TempDir Path temp;
    @Test void projectWithoutIncludesRetainsDefaultTargets() throws Exception {
        var bundle = read(valid()).bundle();
        assertTrue(bundle.projectMode());
        assertEquals(List.of(":a:run"), bundle.defaultTargets());
        assertEquals(List.of(), bundle.edges());
    }

    @Test void resourcePathsMustBeUniqueAndNormalized() {
        for (var paths : List.of(List.of("../outside"), List.of("C:/outside"),
                List.of("x//y"), List.of("x", "x"))) {
            var metadata = valid();
            metadata.put("resourceFiles", paths);
            assertThrows(JobJarMain.JobJarException.class, () -> read(metadata));
        }
    }

    @Test void normalizedResourcesAllowDotsWithinNamesAndUseOwnedRunRoot() throws Exception {
        var input=valid(); input.put("resourceFiles",List.of("a/data..txt"));
        var metadata=read(input); Path run=Files.createDirectory(temp.resolve("run"));
        var loader=new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {
                return name.equals(JobJarMain.RESOURCE_ROOT+"a/data..txt")?new ByteArrayInputStream("synthetic".getBytes(StandardCharsets.UTF_8)):null;
            }
        };
        var source=new JobJarMain.PackagedJobSource(metadata,temp,loader,Locale.ENGLISH);
        Path resources=source.prepareRun(run);
        assertEquals(run.resolve("job-resources"),resources);
        assertEquals("synthetic",Files.readString(resources.resolve("a/data..txt")));
        assertFalse(Files.exists(temp.resolve("a/data..txt")));
    }

    private Map<String,Object> valid() {
        String id = "a/job.groovy";
        String virtual = "GretlSource_612f6a6f622e67726f6f7679.groovy";
        var bundle = Map.of("settingsId", "settings.gretl", "sha256", "a".repeat(64),
                "defaultTargets", List.of(":a:run"), "entries", List.of(Map.of("scopeId", ":a", "sourceId", id)),
                "sources", List.of(Map.of("sourceId", id, "className", virtual.replace(".groovy", ""))), "edges", List.of());
        return new LinkedHashMap<>(Map.of("schemaVersion", 1, "name", "project", "version", "1",
                "runtimeVersion", "6.0.0-SNAPSHOT", "bundle", bundle, "resourceFiles", List.of()));
    }

    private JobJarMain.JobMetadata read(Map<String,Object> metadata) throws Exception {
        byte[] bytes=Json.write(metadata).getBytes(StandardCharsets.UTF_8);
        return JobJarMain.JobMetadata.load(new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {return name.equals(JobJarMain.METADATA_RESOURCE)?new ByteArrayInputStream(bytes):null;}
        },Locale.ENGLISH);
    }
}
