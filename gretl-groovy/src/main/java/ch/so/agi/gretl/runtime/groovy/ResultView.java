package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import java.util.List;

/**
 * Read-only Groovy view over explicitly declared OutputFields. No bean/record
 * reflection and no implicit serialization are used.
 */
public final class ResultView<R> extends GroovyObjectSupport {
    private final OperationType<?, R> operation;
    private final R result;
    private final List<OutputField<R, ?>> outputs;

    @SuppressWarnings("unchecked")
    public ResultView(OperationType<?, R> operation, R result) {
        this.operation = operation;
        this.result = result;
        this.outputs = (List<OutputField<R, ?>>) (List<?>) operation.outputs();
    }

    @Override
    public Object getProperty(String name) {
        if ("metaClass".equals(name)) {
            return super.getMetaClass();
        }
        if ("class".equals(name)) {
            return getClass();
        }
        OutputField<R, ?> field = outputs.stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new GroovyDslException(
                        "UNKNOWN_OUTPUT_FIELD",
                        "Taskart " + operation.symbol() + " bietet kein Ergebnisfeld " + name,
                        null, name, SourceLocations.fallback(null),
                        outputs.stream().map(OutputField::name).toList(), null));
        Object value = field.read().apply(result);
        if (value == null && !field.nullable()) {
            throw new GroovyDslException("NULL_OUTPUT_FIELD",
                    "Ergebnisfeld " + name + " ist nicht nullable, wurde aber null",
                    null, name, SourceLocations.fallback(null), List.of(), null);
        }
        return value;
    }

    @Override
    public void setProperty(String name, Object value) {
        throw new GroovyDslException("READ_ONLY_RESULT",
                "Ergebnisse sind lesend; " + name + " kann nicht zugewiesen werden");
    }

    @Override
    public Object invokeMethod(String name, Object args) {
        throw new GroovyDslException("READ_ONLY_RESULT",
                "ResultView bietet keine Methode " + name + "; nutze result(...).feld");
    }

    @Override
    public String toString() {
        return "ResultView[" + operation.symbol() + "]";
    }

    /** Groovy can ask for a missing property through MissingPropertyException. */
    @SuppressWarnings("unused")
    private void propertyMissing(String name) {
        throw new MissingPropertyException(name, ResultView.class);
    }
}
