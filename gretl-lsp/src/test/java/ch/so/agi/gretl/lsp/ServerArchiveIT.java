package ch.so.agi.gretl.lsp;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.zip.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ServerArchiveIT {
    @TempDir Path temporary;
    @ParameterizedTest
    @CsvSource({"operations.json, CATALOG_BUNDLE_HASH_MISMATCH", "catalog-manifest.json, INVALID_REQUEST"})
    void standaloneArchiveHasNoRuntimeClassesAndBrokenCatalogFailsBeforeTransport(
            String damagedResource, String diagnostic) throws Exception {
        Path original=LspProcessHarness.packagedServer(),broken=temporary.resolve("broken bundle.jar");
        try(var jar=new JarFile(original.toFile())) {
            assertEquals("ch.so.agi.gretl.lsp.GretlServerLauncher",jar.getManifest().getMainAttributes().getValue("Main-Class"));
            assertTrue(jar.stream().noneMatch(entry->entry.getName().startsWith("ch/so/agi/gretl/runtime/")||entry.getName().startsWith("org/gradle/")||entry.getName().endsWith(".jar")));
        }
        try(var input=new ZipFile(original.toFile());var output=new ZipOutputStream(Files.newOutputStream(broken))) {
            for(var entry:Collections.list(input.entries())) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                try (var content = input.getInputStream(entry)) {
                    if (!entry.getName().equals(damagedResource)) {
                        content.transferTo(output);
                    } else if (damagedResource.equals("catalog-manifest.json")) {
                        var mapper = new JsonMapper();
                        var manifest = (ObjectNode) mapper.readTree(content);
                        manifest.remove("catalogId");
                        output.write(mapper.writeValueAsBytes(manifest));
                    } else {
                        output.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
                output.closeEntry();
            }
        }
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-jar",broken.toString(),"--stdio").directory(temporary.toFile()).start();
        try {
            assertTrue(process.waitFor(8,TimeUnit.SECONDS));assertEquals(1,process.exitValue());
            assertEquals("",new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            String stderr = new String(process.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(stderr.contains(diagnostic), stderr);
        }finally{if(process.isAlive())process.destroyForcibly();}
    }
}
