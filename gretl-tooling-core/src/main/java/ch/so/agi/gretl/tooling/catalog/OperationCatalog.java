package ch.so.agi.gretl.tooling.catalog;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Data-only catalog. No runtime type, reflection, callback or class loader is retained. */
public record OperationCatalog(int schemaVersion, String runtimeVersion, String dslVersion,
        String catalogId, List<String> producers, List<OperationDescriptor> operations, String coverage) {
    public OperationCatalog {
        producers = List.copyOf(producers); operations = List.copyOf(operations);
        Objects.requireNonNull(runtimeVersion); Objects.requireNonNull(dslVersion);
        Objects.requireNonNull(catalogId); Objects.requireNonNull(coverage);
    }
    public record ValueShape(String kind, String runtimeType, List<ValueShape> children) {
        public ValueShape { Objects.requireNonNull(kind); Objects.requireNonNull(runtimeType); children = List.copyOf(children); }
        public static ValueShape fromMetadata(String type) {
            String kind = switch (type) {
                case "text" -> "Text"; case "boolean" -> "Boolean"; case "integer" -> "Integer";
                case "decimal" -> "Decimal"; case "path" -> "Path";
                // Element conversions must be supplied explicitly, never inferred from a class name.
                case "list" -> "ListOf"; case "map" -> "MapOf"; default -> "Opaque";
            };
            return new ValueShape(kind, type, type.equals("list") ? List.of(opaque())
                    : type.equals("map") ? List.of(opaque(), opaque()) : List.of());
        }
        private static ValueShape opaque() { return new ValueShape("Opaque", "unknown", List.of()); }
    }
    /** Numeric defaults use strings and an explicit kind, preserving 64-bit precision. */
    public record DataValue(String kind, String scalar, List<DataValue> items, Map<String, DataValue> entries) {
        public DataValue { Objects.requireNonNull(kind); items = List.copyOf(items); entries = Map.copyOf(entries); }
    }
    public record InputDescriptor(String name, String description, boolean required, boolean hasDefault,
            DataValue defaultValue, List<String> allowedValues, String example, ValueShape shape,
            String pathRole, boolean sensitive, Map<String, String> hints) {
        public InputDescriptor { allowedValues = List.copyOf(allowedValues); hints = Map.copyOf(hints); }
    }
    public record OutputDescriptor(String name, String description, ValueShape shape, boolean nullable) { }
    public record OperationDescriptor(String operationId, String symbol, String description, String origin,
            List<InputDescriptor> inputs, List<OutputDescriptor> outputs, String example, boolean voidResult) {
        public OperationDescriptor { inputs = List.copyOf(inputs); outputs = List.copyOf(outputs); }
    }
}
