package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.catalog.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/** Reads only bounded bundled data, validates both manifest hashes and the catalog's own identity. */
final class BundledCatalog {
    static CatalogRepository load() throws IOException {
        byte[] operations=resource("operations.json",4_194_304),schema=resource("operations.schema.json",262_144),manifest=resource("catalog-manifest.json",16_384);
        var metadata=new ObjectMapper().readTree(manifest);
        if(!CatalogJsonCodec.hash(operations).equals(metadata.path("operationsSha256").asText())
                ||!CatalogJsonCodec.hash(schema).equals(metadata.path("schemaSha256").asText()))throw new IOException("CATALOG_BUNDLE_HASH_MISMATCH");
        var repository=new CatalogRepository();
        var result=repository.loadBundled(operations,metadata.path("catalogId").asText());
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
