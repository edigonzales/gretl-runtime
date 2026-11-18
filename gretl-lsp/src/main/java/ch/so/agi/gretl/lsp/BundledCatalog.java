package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.catalog.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;

/** Reads only bounded bundled data, validates both manifest hashes and the catalog's own identity. */
final class BundledCatalog {
    static CatalogRepository load() throws IOException {
        byte[] operations=resource("operations.json",4_194_304),schema=resource("operations.schema.json",262_144),manifest=resource("catalog-manifest.json",16_384);
        JsonNode metadata;
        try {
            metadata = new JsonMapper().readTree(manifest);
        } catch (JacksonException exception) {
            throw new IOException("CATALOG_BUNDLE_INVALID", exception);
        }
        if(!CatalogJsonCodec.hash(operations).equals(metadata.path("operationsSha256").stringValue(null))
                ||!CatalogJsonCodec.hash(schema).equals(metadata.path("schemaSha256").stringValue(null)))throw new IOException("CATALOG_BUNDLE_HASH_MISMATCH");
        var repository=new CatalogRepository();
        var result=repository.loadBundled(operations,metadata.path("catalogId").stringValue(""));
        if(result.result().catalog().isEmpty())throw new IOException("CATALOG_BUNDLE_INVALID: "+result.result().problems());
        return repository;
    }
    private static byte[] resource(String name,int limit) throws IOException {
        try(var stream=BundledCatalog.class.getResourceAsStream("/"+name)) {
            if(stream==null)throw new IOException("CATALOG_BUNDLE_MISSING: "+name);
            byte[] bytes=stream.readNBytes(limit+1);if(bytes.length>limit)throw new IOException("CATALOG_BUNDLE_LIMIT");return bytes;
        }
    }
}
