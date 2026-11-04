package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Data-only values accepted by semantic editing. Raw Groovy and executable callbacks are absent. */
public sealed interface EditableValue {
    record Literal(Object value) implements EditableValue {
        public Literal {
            if (value instanceof String text && text.length() > 65_536) throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
            if (value != null && !List.of(String.class, Boolean.class, Integer.class,
                    Long.class, java.math.BigInteger.class, java.math.BigDecimal.class).contains(value.getClass())) {
                throw new IllegalArgumentException("EDIT_LITERAL_TYPE");
            }
            if (value instanceof java.math.BigInteger integer && integer.bitLength() > 13_610
                    || value instanceof java.math.BigDecimal decimal && (decimal.precision() > 4096 || Math.abs((long) decimal.scale()) > 4096)) {
                throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
            }
        }
    }
    record Sequence(List<EditableValue> items) implements EditableValue {
        public Sequence { items = List.copyOf(items); }
    }
    record Mapping(Map<String, EditableValue> entries) implements EditableValue {
        public Mapping {
            var copy = new LinkedHashMap<String, EditableValue>();
            entries.forEach((key, value) -> copy.put(Objects.requireNonNull(key), Objects.requireNonNull(value)));
            entries = Collections.unmodifiableMap(copy);
        }
    }
    record ParameterRef(SymbolId parameter) implements EditableValue {
        public ParameterRef {
            Objects.requireNonNull(parameter);
            if (parameter.namespace() != ch.so.agi.gretl.tooling.semantic.SemanticModel.Namespace.PARAMETER) throw new IllegalArgumentException("EDIT_PARAMETER_NAMESPACE");
        }
    }
    record ResultFieldRef(SymbolId producer, String outputField) implements EditableValue {
        public ResultFieldRef {
            Objects.requireNonNull(producer); Objects.requireNonNull(outputField);
            if (producer.namespace() != ch.so.agi.gretl.tooling.semantic.SemanticModel.Namespace.TASK || outputField.isBlank()) throw new IllegalArgumentException("EDIT_RESULT_NAMESPACE");
        }
    }
    record JobFileRef(String path) implements EditableValue {
        public JobFileRef { Objects.requireNonNull(path); }
    }
    record WorkPathRef(String path) implements EditableValue {
        public WorkPathRef { Objects.requireNonNull(path); }
    }
    record SecretAliasRef(String alias) implements EditableValue {
        public SecretAliasRef { Objects.requireNonNull(alias); }
    }
}
