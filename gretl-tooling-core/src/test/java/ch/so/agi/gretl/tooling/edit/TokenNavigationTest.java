package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.syntax.GretlDocumentParser;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.Channel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenNavigationTest {
    @Test void indexedLookupsRetainExactTokenAndTriviaBoundariesInUtf16() {
        var document=new GretlDocumentParser().parse(SourceText.job("// 😀\r\ntask('a', Echo) { input='x' }\r\n"));
        var source=document.source();
        var tokens=document.tokens();
        var declaration=document.declarations().getFirst();
        assertEquals(Channel.COMMENT,tokens.at(source.text().indexOf("😀")+1).orElseThrow().channel());
        assertEquals("'a'",tokens.exact(declaration.nameLiteral().token()).orElseThrow().text());
        assertTrue(tokens.exact(declaration.nameLiteral().content()).isEmpty());
        assertEquals(source.slice(declaration.span()),tokens.within(declaration.span()).stream().map(t->t.text()).collect(java.util.stream.Collectors.joining()));
        assertTrue(tokens.within(new TextSpan(0,0)).isEmpty());
        assertTrue(tokens.at(source.text().length()).isEmpty());
        assertTrue(tokens.significant().stream().noneMatch(t->t.channel()==Channel.COMMENT||t.channel()==Channel.WHITESPACE));
    }
}
