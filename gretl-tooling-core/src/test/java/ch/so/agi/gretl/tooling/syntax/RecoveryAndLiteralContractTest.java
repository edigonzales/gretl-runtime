package ch.so.agi.gretl.tooling.syntax;

import org.junit.jupiter.api.Test;
import java.util.*;
import ch.so.agi.gretl.tooling.source.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryAndLiteralContractTest {
    private final GretlDocumentParser parser=new GretlDocumentParser();
    @Test void everyQuoteEnvelopeAndEscapeMapUsesOriginalTokenRanges(){
        var fixtures=Map.of("'single'","single","\"double\"","double","'''triple'quote'''","triple'quote","\"\"\"triple\"quote\"\"\"","triple\"quote","/slash\\/value/","slash/value","$/dollar$$value$/path/$","dollar$value/path");
        for(var fixture:fixtures.entrySet()){
            String text="action("+fixture.getKey()+") {}";var doc=parser.parse(SourceText.job(text));assertTrue(doc.problems().isEmpty(),text+doc.problems());
            var declaration=doc.declarations().getFirst();assertEquals(fixture.getValue(),declaration.name());assertEquals(fixture.getKey(),doc.source().slice(declaration.nameLiteral().token()));
            assertEquals(fixture.getValue().length()+1,declaration.nameLiteral().decodedToSource().size(),text);
        }
    }
    @Test void helperLiteralInsideInterpolationKeepsItsOwnQuoteSpan(){
        String text="action('a') { def value = \"value ${parameter('inner')}\" }";
        var doc=parser.parse(SourceText.job(text));assertTrue(doc.problems().isEmpty(),doc.problems().toString());
        var call=doc.declarations().getFirst().references().stream().filter(r->r.name().equals("parameter")).findFirst().orElseThrow();
        var literal=(LiteralValue)call.arguments().getFirst();assertNotNull(literal.literal());assertEquals("'inner'",doc.source().slice(literal.literal().token()));
    }
    @Test void resultPropertiesInActionsAndInterpolationKeepPreciseAnchors(){
        String text="action('consumer') { def a=result('producer').file; def b=\"value \u0024{result('producer').status}\" }";
        var doc=parser.parse(SourceText.job(text));assertTrue(doc.problems().isEmpty(),doc.problems().toString());
        assertEquals(List.of("file","status"),doc.declarations().getFirst().properties().stream().map(PropertyValue::property).toList());
        for(var property:doc.declarations().getFirst().properties())assertEquals(property.property(),doc.source().slice(property.propertySpan()));
    }
    @Test void recoveryRetainsTaskRefBindingAndImportedShadowing(){
        String text="def broken = ?\ndef ref = task('safe', Echo) { input='ok' }\ndefaults ref";
        var doc=parser.parse(SourceText.job(text));assertFalse(doc.problems().isEmpty());var task=doc.declarations().getFirst();
        assertEquals("ref",task.bindingName());assertEquals("ref",doc.source().slice(task.bindingSpan()));assertTrue(doc.source().slice(task.statementSpan()).startsWith("def ref = task"));
        var imported=parser.parse(SourceText.job("import static java.util.Collections.emptyList as task\ndef broken = ?\ntask('fake', Echo) {}\naction('real') {}"));
        assertFalse(imported.problems().isEmpty());assertEquals(List.of("real"),imported.declarations().stream().map(Declaration::name).filter(Objects::nonNull).toList());
    }
    @Test void hugeNumbersAndLiteralsProduceLimitsWithoutGroovyConversion(){
        for(String text:List.of("def n="+"9".repeat(5000),"def s='"+"x".repeat(70000)+"'","def s=\"${1}"+"x".repeat(70000)+"\"")){
            var doc=parser.parse(SourceText.job(text));assertTrue(doc.problems().stream().anyMatch(p->p.code().equals("PARSE_LIMIT")),doc.problems().toString());
        }
    }
    @Test void unterminatedGStringChecksTheSameUtf16BudgetAtEof(){
        for(String body:List.of("x".repeat(70000),"😀".repeat(35000))){
            var source=SourceText.job("def s = \"${"+body);var doc=parser.parse(source);
            assertTrue(doc.tokens().budgetExceeded());assertEquals(Completeness.PARTIAL,doc.coverage().syntax());
            assertEquals(1,doc.problems().size());var problem=doc.problems().getFirst();assertEquals("PARSE_LIMIT",problem.code());
            assertEquals(source.text().length(),problem.span().endUtf16());assertEquals('"',source.text().charAt(problem.span().startUtf16()));
            assertEquals(source.text(),doc.tokens().all().stream().map(TokenIndex.LexToken::text).collect(java.util.stream.Collectors.joining()));
        }
    }
    @Test void primitiveLiteralsAndTypedHelperNamesRemainKnown(){
        var doc=parser.parse(SourceText.job("parameter('n', type:'integer', description:'count', defaultValue:-5); task('a', Echo) { input=parameter('n', Long) }"));
        assertTrue(doc.problems().isEmpty(),doc.problems().toString());
        var defaultValue=doc.declarations().getFirst().options().stream().filter(e->((LiteralValue)e.key()).value().equals("defaultValue")).findFirst().orElseThrow().value();
        assertInstanceOf(LiteralValue.class,defaultValue);assertEquals("-5",((LiteralValue)defaultValue).value().toString());
        assertEquals(Completeness.COMPLETE,doc.declarations().get(1).coverage().references());
        assertThrows(IllegalArgumentException.class,()->new LiteralValue(new java.util.concurrent.atomic.AtomicInteger(1),new TextSpan(0,1),null));
    }
    @Test void bareCrAndCrLfStringMappingsKeepOriginalOffsets(){
        String text="action('a'){}\raction('b'){}";var doc=parser.parse(SourceText.job(text));assertTrue(doc.problems().isEmpty(),doc.problems().toString());
        assertEquals(text.lastIndexOf("action"),doc.declarations().get(1).span().startUtf16());assertEquals(new TextPosition(1,0),doc.source().lines().positionAt(doc.declarations().get(1).span().startUtf16()));
        var multiline=parser.parse(SourceText.job("action('''a\r\nb''') {}"));assertTrue(multiline.problems().isEmpty());var literal=multiline.declarations().getFirst().nameLiteral();
        assertEquals("a\nb",multiline.declarations().getFirst().name());assertEquals(literal.content().startUtf16()+1,literal.decodedToSource().get(1));
    }
    @Test void appliedParserPositionsRoundTripExactRepeatedFieldsAcrossLines(){
        String text="task('a', Echo) {\r\n input = 'first'\r\n}\r\ntask('b', Echo) {\r\n input = 'second'\r\n}\r\n";
        var doc=parser.parse(SourceText.job(text));assertTrue(doc.problems().isEmpty());
        var first=doc.declarations().getFirst().fields().getFirst();var second=doc.declarations().get(1).fields().getFirst();
        assertTrue(second.nameSpan().startUtf16()>first.nameSpan().endUtf16());assertEquals("'second'",doc.source().slice(second.value().span()));
        assertEquals(4,doc.source().lines().positionAt(second.nameSpan().startUtf16()).line());
    }
    @Test void mergerKeepsSeparateErrorsAndCoalescesOnlyTheSameOverlappingProblem(){
        var lexical=new SyntaxProblem("LEXICAL_ERROR",new TextSpan(3,10),"unfinished token");
        var parsed=new SyntaxProblem("SYNTAX_ERROR",new TextSpan(5,6),"specific parser position");
        var separate=new SyntaxProblem("SYNTAX_ERROR",new TextSpan(20,21),"independent second error");
        var limit=new SyntaxProblem("PARSE_LIMIT",new TextSpan(0,30),"explicit bounded state");
        var merged=new ParseResultMerger().problems(List.of(lexical,limit),List.of(parsed,separate));
        assertEquals(List.of(limit,parsed,separate),merged);
        assertEquals(List.of(parsed),new ParseResultMerger().problems(List.of(parsed),List.of(parsed)));
    }
}
