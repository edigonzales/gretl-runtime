package ch.so.agi.gretl.tooling.syntax;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import ch.so.agi.gretl.tooling.*;
import ch.so.agi.gretl.tooling.source.*;
import static org.junit.jupiter.api.Assertions.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

class ParseBoundsAndCoordinatesTest {
    @Test void realGroovyCodepointPositionsMapToUtf16WithoutFirstOccurrenceSearch(){
        var source=SourceText.job("😀same\r\n\t😀same");var mapper=new ParserCoordinateMapper(source);
        assertEquals(2,mapper.lexerOffset(1));assertEquals(2,mapper.parserOffset(1,2));assertEquals(source.text().lastIndexOf("same"),mapper.parserOffset(2,3));
        assertEquals(new TextPosition(1,3),source.lines().positionAt(source.text().lastIndexOf("same")));
        assertThrows(IllegalArgumentException.class,()->source.lines().offsetAt(new TextPosition(2,0)));
        assertThrows(IllegalArgumentException.class,()->source.lines().offsetAt(new TextPosition(0,99)));
        assertEquals("\n",SourceText.job("a\nb\rc").preferredEol());
    }
    @Test void sourceTokenNestingAndGStringBudgetsFailWithExplicitLimit(){
        var parser=new GretlDocumentParser();
        var source=SourceText.job("action('a') {} ".repeat(100));
        var size=parser.parse(source,DocumentKind.JOB,new ParseOptions(10,10000,32),CancellationToken.NONE);
        assertEquals("PARSE_LIMIT",size.problems().getFirst().code());assertEquals(source.text(),size.source().text());
        var tokens=parser.parse(source,DocumentKind.JOB,new ParseOptions(10000,5,32),CancellationToken.NONE);assertTrue(tokens.problems().stream().anyMatch(p->p.code().equals("PARSE_LIMIT")));
        String deep="def x = "+"(".repeat(200)+"1"+")".repeat(200);
        var nesting=assertTimeout(Duration.ofSeconds(3),()->parser.parse(SourceText.job(deep)));assertTrue(nesting.problems().stream().anyMatch(p->p.code().equals("PARSE_LIMIT")));
        String g="def x=\"${"+"(".repeat(200)+"1"+")".repeat(200)+"}\"";
        var interpolated=parser.parse(SourceText.job(g));assertTrue(interpolated.problems().stream().anyMatch(p->p.code().equals("PARSE_LIMIT")));
        assertTrue(interpolated.problems().stream().noneMatch(p->p.code().equals("LEXICAL_ERROR")),interpolated.problems().toString());
    }
    @Test void cancellationIsNotTurnedIntoAnEmptySuccessfulDocument(){
        var parser=new GretlDocumentParser();assertThrows(java.util.concurrent.CancellationException.class,()->parser.parse(SourceText.job("action('a') {}"),DocumentKind.JOB,ParseOptions.DEFAULT,()->true));
        var count=new AtomicInteger();assertThrows(java.util.concurrent.CancellationException.class,()->parser.parse(SourceText.job("action('a') {} ".repeat(100)),DocumentKind.JOB,ParseOptions.DEFAULT,()->count.incrementAndGet()>8));
    }
    @Test void malformedNoTaskAndGStringCasesHaveRealDiagnosticsAndNoPhantoms(){
        var parser=new GretlDocumentParser();
        for(String text:new String[]{"def x =", "def s = 'unfinished", "def s = \"${unclosed", "/* unfinished task('fake', Echo) {}"}){
            var doc=parser.parse(SourceText.job(text));assertFalse(doc.problems().isEmpty(),text);assertTrue(doc.declarations().isEmpty(),text);
        }
        var before=parser.parse(SourceText.job("action('before') {}\ndef s=\"${unfinished task('fake', Echo) { }"));
        assertFalse(before.problems().isEmpty());assertEquals(1,before.declarations().size());assertEquals("before",before.declarations().getFirst().name());
    }
    @Test void boundedLargeValidFixtureRetainsAllDeclarations(){
        StringBuilder text=new StringBuilder();for(int i=0;i<1000;i++)text.append("action('t").append(i).append("') { println 'safe' }\n");
        var doc=assertTimeout(Duration.ofSeconds(10),()->new GretlDocumentParser().parse(SourceText.job(text.toString())));
        assertTrue(doc.problems().isEmpty(),doc.problems().toString());assertEquals(1000,doc.declarations().size());
    }
}
