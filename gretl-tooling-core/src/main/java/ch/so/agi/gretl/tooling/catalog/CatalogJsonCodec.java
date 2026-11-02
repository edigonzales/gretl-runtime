package ch.so.agi.gretl.tooling.catalog;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.security.MessageDigest;
import java.util.*;
import static ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;

/** Strict bounded codec. An incompatible or absent catalog is never an empty success. */
public final class CatalogJsonCodec {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private final ObjectMapper mapper = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
            .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(1_000_000).build()).build())
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    public enum State { AVAILABLE, PARTIAL, UNAVAILABLE, INCOMPATIBLE }
    public record CatalogLoadResult(State state, Optional<OperationCatalog> catalog, List<String> problems) {
        public CatalogLoadResult { catalog = Objects.requireNonNull(catalog); problems = List.copyOf(problems); }
    }
    public byte[] encodeCanonical(OperationCatalog catalog) {
        try { return mapper.writeValueAsBytes(catalog); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Catalog serialization failed", e); }
    }
    public String digest(OperationCatalog catalog) {
        var payload = new OperationCatalog(catalog.schemaVersion(), catalog.runtimeVersion(), catalog.dslVersion(),
                "", catalog.producers(), catalog.operations(), catalog.coverage());
        return hash(encodeCanonical(payload));
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public OperationCatalog identify(OperationCatalog c) {
        return new OperationCatalog(c.schemaVersion(), c.runtimeVersion(), c.dslVersion(), digest(c), c.producers(), c.operations(), c.coverage());
    }
    public CatalogLoadResult decode(byte[] bytes, String expectedDigest) {
        if (bytes == null) return failure(State.UNAVAILABLE, "CATALOG_MISSING");
        if (bytes.length == 0 || bytes.length > MAX_BYTES) return failure(State.INCOMPATIBLE, "CATALOG_SIZE");
        try {
            var c = mapper.readValue(bytes, OperationCatalog.class);
            var problems = new ArrayList<>(validate(c));
            if (!digest(c).equals(c.catalogId())) problems.add("CATALOG_DIGEST");
            if (expectedDigest != null && !expectedDigest.equals(c.catalogId())) problems.add("CATALOG_IDENTITY");
            if (!problems.isEmpty()) return new CatalogLoadResult(State.INCOMPATIBLE, Optional.empty(), problems);
            return new CatalogLoadResult(c.coverage().equals("COMPLETE") ? State.AVAILABLE : State.PARTIAL, Optional.of(c), List.of());
        } catch (java.io.IOException | IllegalArgumentException | NullPointerException e) {
            return failure(State.INCOMPATIBLE, "CATALOG_SCHEMA");
        }
    }
    private CatalogLoadResult failure(State state, String code) { return new CatalogLoadResult(state, Optional.empty(), List.of(code)); }
    public List<String> validate(OperationCatalog c) {
        var errors = new ArrayList<String>();
        if (c.schemaVersion() != 1) errors.add("schemaVersion");
        if (c.runtimeVersion().isBlank() || c.dslVersion().isBlank()) errors.add("version");
        if (!Set.of("COMPLETE", "PARTIAL").contains(c.coverage())) errors.add("coverage");
        if (new HashSet<>(c.producers()).size() != c.producers().size()) errors.add("producers");
        if (c.operations().isEmpty() || c.operations().size() > 10000) errors.add("operations.size");
        var ids = new HashSet<String>(); var symbols = new HashSet<String>();
        for (var op : c.operations()) {
            String p = "operations." + op.operationId();
            if (!text(op.operationId()) || !ids.add(op.operationId())) errors.add(p + ".id");
            if (!text(op.symbol()) || !symbols.add(op.symbol())) errors.add(p + ".symbol");
            if (!text(op.description()) || !text(op.example()) || !c.producers().contains(op.origin())) errors.add(p + ".metadata");
            if (op.voidResult() && !op.outputs().isEmpty()) errors.add(p + ".voidResult");
            var fields = new HashSet<String>();
            for (var f : op.inputs()) {
                if (!text(f.name()) || !fields.add(f.name()) || !text(f.description()) || !text(f.example())) errors.add(p + ".input");
                if (f.hasDefault() != (f.defaultValue() != null)) errors.add(p + ".default");
                if (new HashSet<>(f.allowedValues()).size() != f.allowedValues().size()) errors.add(p + ".allowedValues");
                shape(f.shape(), errors, p, 0);
                if (f.defaultValue() != null) value(f.defaultValue(), errors, p, 0);
                if (f.pathRole() != null && !Set.of("JOB_RESOURCE", "OUTPUT", "WORK", "EXTERNAL").contains(f.pathRole())) errors.add(p + ".pathRole");
            }
            fields.clear();
            for (var f : op.outputs()) {
                if (!text(f.name()) || !fields.add(f.name()) || !text(f.description())) errors.add(p + ".output");
                shape(f.shape(), errors, p, 0);
            }
        }
        return List.copyOf(errors);
    }
    private static boolean text(String s) { return s != null && !s.isBlank(); }
    private static void shape(ValueShape s, List<String> errors, String p, int depth) {
        if (s == null || depth > 16) { errors.add(p + ".shape"); return; }
        var counts = Map.of("Text",0,"Boolean",0,"Integer",0,"Decimal",0,"Path",0,"Opaque",0,"ListOf",1,"MapOf",2);
        if (!text(s.runtimeType()) || !counts.containsKey(s.kind()) || counts.get(s.kind()) != s.children().size()) errors.add(p + ".shape");
        for (var child : s.children()) shape(child, errors, p, depth + 1);
    }
    private static void value(DataValue v, List<String> errors, String p, int depth) {
        if (depth > 16 || !Set.of("Null","Text","Boolean","Integer","Decimal","Path","Enum","Uri","Duration","List","Map").contains(v.kind())) { errors.add(p + ".value"); return; }
        boolean compound = v.kind().equals("List") || v.kind().equals("Map");
        if (compound || v.kind().equals("Null")) {
            if (v.scalar() != null) errors.add(p + ".scalar");
        } else if (v.scalar() == null || !v.items().isEmpty() || !v.entries().isEmpty()) errors.add(p + ".scalar");
        if (!v.kind().equals("List") && !v.items().isEmpty()) errors.add(p + ".items");
        if (!v.kind().equals("Map") && !v.entries().isEmpty()) errors.add(p + ".entries");
        if (v.kind().equals("Integer") && (v.scalar() == null || !v.scalar().matches("[+-]?[0-9]+"))) errors.add(p + ".integer");
        if (v.kind().equals("Boolean") && !Set.of("true","false").contains(v.scalar() == null ? "" : v.scalar())) errors.add(p + ".boolean");
        if (v.kind().equals("Decimal")) try { new java.math.BigDecimal(v.scalar()); } catch (RuntimeException e) { errors.add(p + ".decimal"); }
        for (var child : v.items()) value(child, errors, p, depth + 1);
        for (var child : v.entries().values()) value(child, errors, p, depth + 1);
    }
}
