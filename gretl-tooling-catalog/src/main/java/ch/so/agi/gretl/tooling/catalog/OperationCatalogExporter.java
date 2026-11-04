package ch.so.agi.gretl.tooling.catalog;

import ch.so.agi.gretl.runtime.api.*;
import java.util.*;
import java.nio.file.*;
import static ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;

/** Trusted build-time bridge. Only descriptive SPI accessors are used. */
public final class OperationCatalogExporter {
    public OperationCatalog export(Map<String,List<OperationType<?,?>>> providers, String runtimeVersion,
            List<MetadataSupplementLoader.Supplement> supplements) {
        var all = providers.values().stream().flatMap(Collection::stream).toList();
        var hints = new MetadataSupplementLoader().validateAgainstOperations(supplements, all);
        var operations = new ArrayList<OperationDescriptor>();
        for (var provider : new TreeMap<>(providers).entrySet()) for (var op : provider.getValue()) {
            var inputs = new ArrayList<InputDescriptor>();
            for (var f : op.inputs()) {
                var hint = hints.get(op.id() + "/" + f.name());
                if (hint != null && hint.shape() != null && (!hint.shape().runtimeType().equals(f.valueType())
                        || !hint.shape().kind().equals(ValueShape.fromMetadata(f.valueType()).kind())))
                    throw new IllegalArgumentException("Supplement cannot override SPI value type: " + hint.key());
                inputs.add(new InputDescriptor(f.name(), f.description(), f.required(), f.defaultValue().isPresent(),
                        f.defaultValue().map(OperationCatalogExporter::dataValue).orElse(null), f.allowedValues(), f.example(),
                        hint != null && hint.shape() != null ? hint.shape() : ValueShape.fromMetadata(f.valueType()),
                        hint == null ? null : hint.pathRole(), f.valueType().equals("secret") || hint != null && hint.sensitive(),
                        hint == null ? Map.of() : hint.hints()));
            }
            var outputs = op.outputs().stream().map(f -> new OutputDescriptor(f.name(), f.description(), ValueShape.fromMetadata(f.valueType()), f.nullable())).toList();
            operations.add(new OperationDescriptor(op.id(),op.symbol(),op.description(),provider.getKey(),inputs,outputs,op.example(),op.resultType() == Void.class));
        }
        operations.sort(Comparator.comparing(OperationDescriptor::operationId));
        var codec = new CatalogJsonCodec();
        var catalog = codec.identify(new OperationCatalog(1,runtimeVersion,"1","",providers.keySet().stream().sorted().toList(),operations,"COMPLETE"));
        var errors = codec.validate(catalog);
        if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid exported catalog: " + errors);
        return catalog;
    }
    public void write(OperationCatalog catalog, Path output) throws java.io.IOException {
        var codec = new CatalogJsonCodec();
        if (codec.decode(codec.encodeCanonical(catalog),catalog.catalogId()).catalog().isEmpty()) throw new IllegalArgumentException("Invalid catalog");
        Files.createDirectories(output);
        byte[] bytes = codec.encodeCanonical(catalog);
        Files.write(output.resolve("operations.json"),bytes);
        try (var in = CatalogJsonCodec.class.getResourceAsStream("operations.schema.json")) {
            if (in == null) throw new java.io.IOException("Missing catalog schema");
            Files.write(output.resolve("operations.schema.json"),in.readAllBytes());
        }
        var manifest = Map.of("schemaVersion",1,"runtimeVersion",catalog.runtimeVersion(),"catalogId",catalog.catalogId(),
                "operationsSha256",CatalogJsonCodec.hash(bytes),"schemaSha256",CatalogJsonCodec.hash(Files.readAllBytes(output.resolve("operations.schema.json"))));
        Files.write(output.resolve("catalog-manifest.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(manifest));
        Files.writeString(output.resolve("operations.sha256"),CatalogJsonCodec.hash(bytes) + "  operations.json\n");
    }
    public static DataValue dataValue(Object value) {
        return dataValue(value, 0);
    }
    private static DataValue dataValue(Object value, int depth) {
        if (depth > 16) throw new IllegalArgumentException("Metadata default nesting limit");
        if (value == null) return new DataValue("Null",null,List.of(),Map.of());
        if (value instanceof List<?> list) return new DataValue("List",null,list.stream().map(v -> dataValue(v,depth+1)).toList(),Map.of());
        if (value instanceof Map<?,?> map) {
            var entries = new TreeMap<String,DataValue>();
            for (var e : map.entrySet()) {
                if (!(e.getKey() instanceof String key)) throw new IllegalArgumentException("Default map key must be text");
                entries.put(key,dataValue(e.getValue(),depth+1));
            }
            return new DataValue("Map",null,List.of(),entries);
        }
        String kind;
        String scalar;
        // Only JDK values and Enum.name; no arbitrary getters or toString on user objects.
        if (value instanceof String s) { kind="Text"; scalar=s; }
        else if (value instanceof Boolean b) { kind="Boolean"; scalar=b.toString(); }
        else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger) { kind="Integer"; scalar=value.toString(); }
        else if (value instanceof java.math.BigDecimal || value instanceof Float || value instanceof Double) { kind="Decimal"; scalar=value.toString(); }
        else if (value instanceof Enum<?> e) { kind="Enum"; scalar=e.name(); }
        else if (value instanceof Path p) { kind="Path"; scalar=p.toString(); }
        else if (value instanceof java.net.URI u) { kind="Uri"; scalar=u.toString(); }
        else if (value instanceof java.time.Duration d) { kind="Duration"; scalar=d.toString(); }
        else throw new IllegalArgumentException("Unsupported descriptive default type");
        return new DataValue(kind,scalar,List.of(),Map.of());
    }
}
