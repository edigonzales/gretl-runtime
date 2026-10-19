package ch.so.agi.gretl.tasks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import org.junit.jupiter.api.Test;

class CoreTaskProviderTest {
    @Test
    void serviceLoaderExposesTheCoreTaskFamily() {
        OperationRegistry registry = OperationRegistry.load(getClass().getClassLoader());

        assertEquals(9, registry.all().size());
        assertEquals("FtpList", registry.requireSymbol("FtpList").symbol());
        assertEquals("FtpDownload", registry.requireSymbol("FtpDownload").symbol());
        assertEquals("FtpUpload", registry.requireSymbol("FtpUpload").symbol());
        assertEquals("FtpDelete", registry.requireSymbol("FtpDelete").symbol());
        assertEquals("S3Download", registry.requireSymbol("S3Download").symbol());
        assertEquals("S3Upload", registry.requireSymbol("S3Upload").symbol());
        assertEquals("S3Delete", registry.requireSymbol("S3Delete").symbol());
        assertEquals("S3Bucket2Bucket",
                registry.requireSymbol("S3Bucket2Bucket").symbol());
        assertEquals("XslTransformer", registry.requireSymbol("XslTransformer").symbol());
        assertTrue(registry.requireSymbol("FtpDownload").inputs().stream()
                .anyMatch(field -> field.name().equals("localDir") && field.required()));
    }
}
