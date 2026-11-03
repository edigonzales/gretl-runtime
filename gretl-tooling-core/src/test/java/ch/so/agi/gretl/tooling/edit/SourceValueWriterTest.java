package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.syntax.GretlDocumentParser;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceValueWriterTest {
    @Test void numericKindsAndDecimalScaleSurviveTheActualParserRoundTrip() {
        var writer = new SourceValueWriter(id -> id.qualifiedName());
        for (Object value : List.of(1, -1L, Long.MAX_VALUE, new java.math.BigInteger("9223372036854775808"),
                new java.math.BigDecimal("1"), new java.math.BigDecimal("1.00"), new java.math.BigDecimal("1E+2"))) {
            var document = new GretlDocumentParser().parse(SourceText.job("task('a', Echo) { input=" + writer.write(new EditableValue.Literal(value)) + " }"));
            assertTrue(document.problems().isEmpty(), document.problems().toString());
            Object actual = assertInstanceOf(LiteralValue.class, document.declarations().getFirst().fields().getFirst().value()).value();
            assertEquals(value.getClass(), actual.getClass());
            assertEquals(value, actual);
        }
    }
    @Test void valuesRoundTripThroughRealParserWithoutExecutingInterpolation() {
        for (String value : List.of("a'b\"c\\d", "${throw new Error('must not run')}", "line\r\n\t😀", "lone\uD83D")) {
            for (var quote : QuoteKind.values()) {
                String token = SourceValueWriter.string(value, quote);
                var document = new GretlDocumentParser().parse(SourceText.job("task('a', Echo) { input=" + token + " }"));
                assertTrue(document.problems().isEmpty(), document.problems().toString());
                var literal = assertInstanceOf(LiteralValue.class, document.declarations().getFirst().fields().getFirst().value());
                assertEquals(value, literal.value(), token);
                assertEquals(token, document.source().slice(literal.literal().token()));
            }
        }
    }

    @Test void structuredDataHasNoRawGroovyEscapeHatchAndPreservesEmptyMap() {
        var writer = new SourceValueWriter(id -> id.qualifiedName());
        var value = new EditableValue.Mapping(Map.of("key", new EditableValue.Sequence(List.of(
                new EditableValue.Literal(true), new EditableValue.JobFileRef("sql/a'b.sql"),
                new EditableValue.Mapping(Map.of())))));
        String text = writer.write(value);
        var document = new GretlDocumentParser().parse(SourceText.job("task('a', Echo) { input=" + text + " }"));
        assertTrue(document.problems().isEmpty(), document.problems().toString());
        var map = assertInstanceOf(MapValue.class, document.declarations().getFirst().fields().getFirst().value());
        var values = assertInstanceOf(ListValue.class, map.entries().getFirst().value());
        assertEquals(true, assertInstanceOf(LiteralValue.class, values.items().getFirst()).value());
        assertEquals("sql/a'b.sql", assertInstanceOf(LiteralValue.class, assertInstanceOf(CallValue.class, values.items().get(1)).arguments().getFirst()).value());
        assertTrue(assertInstanceOf(MapValue.class, values.items().get(2)).entries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new EditableValue.Literal(new Object() {
            @Override public String toString() { throw new AssertionError("arbitrary conversion"); }
        }));
    }

    @Test void repeatedSharedContainersStopAtGlobalOutputBudget() {
        EditableValue value = new EditableValue.Literal("x");
        for (int i = 0; i < 10; i++) value = new EditableValue.Sequence(java.util.Collections.nCopies(1024, value));
        var writer = new SourceValueWriter(id -> id.qualifiedName());
        EditableValue sharedTree = value;
        assertThrows(IllegalArgumentException.class, () -> writer.write(sharedTree));
        assertThrows(IllegalArgumentException.class, () -> new EditableValue.Literal(java.math.BigInteger.ONE.shiftLeft(20_000)));
    }
}
