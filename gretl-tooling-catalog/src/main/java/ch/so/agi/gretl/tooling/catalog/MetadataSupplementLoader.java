package ch.so.agi.gretl.tooling.catalog;

import ch.so.agi.gretl.runtime.api.OperationType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.util.*;

/** Additive owned metadata only. Existing SPI facts cannot be overridden. */
public final class MetadataSupplementLoader {
    public record Supplement(String operationId, String field, OperationCatalog.ValueShape shape,
            String pathRole, boolean sensitive, Map<String,String> hints) {
        public Supplement { hints = Map.copyOf(hints); }
        public String key() { return operationId + "/" + field; }
    }
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    public List<Supplement> loadFromOwnedResources(ClassLoader loader) throws IOException {
        // Provider modules use a shared resource name so ClassLoader enumeration discovers all.
        var result = new ArrayList<Supplement>();
        var urls = loader.getResources("META-INF/gretl/tooling/metadata.json");
        while (urls.hasMoreElements()) {
            try (var in = urls.nextElement().openStream()) { result.addAll(read(in)); }
        }
        return List.copyOf(result);
    }
    public List<Supplement> read(InputStream in) throws IOException {
        byte[] data = in.readNBytes(1024 * 1024 + 1);
        if (data.length > 1024 * 1024) throw new IOException("Supplement too large");
        JsonNode root = mapper.readTree(data);
        if (root == null || root.path("schemaVersion").asInt() != 1 || !root.path("fields").isArray()) throw new IOException("Supplement schema");
        requireKeys(root, Set.of("schemaVersion", "fields"));
        var result = new ArrayList<Supplement>();
        for (var row : root.get("fields")) {
            requireKeys(row, Set.of("operationId","field","shape","pathRole","sensitive","hints"));
            if (!row.path("operationId").isTextual() || !row.path("field").isTextual()) throw new IOException("Supplement target");
            result.add(new Supplement(row.get("operationId").textValue(), row.get("field").textValue(),
                    row.has("shape") ? mapper.treeToValue(row.get("shape"), OperationCatalog.ValueShape.class) : null,
                    row.has("pathRole") ? row.get("pathRole").textValue() : null,
                    row.path("sensitive").asBoolean(false), row.has("hints") ? mapper.convertValue(row.get("hints"),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>() {}) : Map.of()));
        }
        return List.copyOf(result);
    }
    private static void requireKeys(JsonNode node, Set<String> allowed) throws IOException {
        if (!node.isObject()) throw new IOException("Supplement object required");
        var it = node.fieldNames(); while (it.hasNext()) if (!allowed.contains(it.next())) throw new IOException("Unknown supplement property");
    }
    public Map<String,Supplement> validateAgainstOperations(List<Supplement> supplements, Collection<OperationType<?,?>> operations) {
        var fields = new HashSet<String>();
        operations.forEach(o -> o.inputs().forEach(f -> fields.add(o.id() + "/" + f.name())));
        var result = new TreeMap<String,Supplement>();
        for (var s : supplements) {
            if (!fields.contains(s.key()) || result.putIfAbsent(s.key(),s) != null) throw new IllegalArgumentException("Unknown or duplicate supplement: " + s.key());
        }
        return Map.copyOf(result);
    }
}
