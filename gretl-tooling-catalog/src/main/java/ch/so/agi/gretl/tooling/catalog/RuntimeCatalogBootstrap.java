package ch.so.agi.gretl.tooling.catalog;

import ch.so.agi.gretl.runtime.api.*;
import java.util.*;
import java.nio.file.Path;

/** Build entry point: uses exactly the OperationProvider service resources on the trusted build classpath. */
public final class RuntimeCatalogBootstrap {
    private RuntimeCatalogBootstrap() { }
    public static Map<String,List<OperationType<?,?>>> collectOperations(ClassLoader loader) {
        var result = new TreeMap<String,List<OperationType<?,?>>>();
        for (var provider : ServiceLoader.load(OperationProvider.class,loader)) {
            var operations = List.copyOf(provider.operations());
            if (result.putIfAbsent(provider.getClass().getName(),operations) != null) throw new IllegalArgumentException("Duplicate provider");
        }
        if (result.isEmpty()) throw new IllegalStateException("No trusted operation providers");
        return Collections.unmodifiableMap(result);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: RuntimeCatalogBootstrap <output-directory> <runtime-version>");
        var loader = RuntimeCatalogBootstrap.class.getClassLoader();
        var exporter = new OperationCatalogExporter();
        exporter.write(exporter.export(collectOperations(loader),args[1],new MetadataSupplementLoader().loadFromOwnedResources(loader)),Path.of(args[0]));
    }
}
