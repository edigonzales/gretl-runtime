package ch.so.agi.gretl.tooling.syntax;

import org.junit.jupiter.api.Test;
import java.util.*;
import ch.so.agi.gretl.tooling.source.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import static org.junit.jupiter.api.Assertions.*;

class GretlDocumentParserTest {
    private final GretlDocumentParser parser=new GretlDocumentParser();
    private ParsedDocument parse(String text){return parser.parse(SourceText.job(text));}
    private Declaration named(ParsedDocument doc,String name){return doc.declarations().stream().filter(d->name.equals(d.name())).findFirst().orElseThrow(()->new AssertionError("Missing "+name+" in "+doc.declarations()+" problems="+doc.problems()));}
    @Test void currentDslMapFirstAllDefaultsActionsCleanupAndInnerInvocations(){
        String text="""
            parameter('quelle', type: 'text', required: true, description: 'URL')
            parameter('ziel', type: 'path', description: 'Directory', defaultValue: 'out')
            def downloadRef = task('download', HttpDownload) {
                url = parameter('quelle')
                output = workPath('daten.zip')
            }
            task(dependsOn: 'download', after: ['check'], when: { c -> c.parameter('on') }, 'unpack', Extract) {
                def filename = 'file.zip'
                archive = result('download').file
                outputDirectory = workPath(filename)
                compression = 'ZIP'
            }
            action('check') { ctx ->
                def data = ctx.run(HttpDownload) { url = 'http://localhost/data'; output = workPath('inner.zip') }
                println data
            }
            cleanup('finish') { println 'done' }
            defaults 'unpack', downloadRef
            """;
        var doc=parse(text);assertTrue(doc.problems().isEmpty(),doc.problems().toString());assertEquals(7,doc.declarations().size());
        var download=named(doc,"download");assertEquals("downloadRef",download.bindingName());assertEquals("downloadRef",doc.source().slice(download.bindingSpan()));assertTrue(doc.source().slice(download.statementSpan()).startsWith("def downloadRef = task"));assertEquals("HttpDownload",((NameValue)download.operation()).name());
        var unpack=named(doc,"unpack");assertEquals(List.of("archive","outputDirectory","compression"),unpack.fields().stream().map(FieldAssignment::name).toList());assertEquals("filename",unpack.locals().getFirst().name());
        assertEquals(List.of("dependsOn","after","when"),unpack.options().stream().map(e->(String)((LiteralValue)e.key()).value()).toList());
        assertTrue(unpack.references().stream().anyMatch(r->r.name().equals("parameter")&&r.receiver().equals("c")));
        var action=named(doc,"check");assertEquals(DeclarationKind.ACTION,action.kind());assertEquals(0,action.fields().size());assertEquals(1,action.invocations().size());
        assertEquals("HttpDownload",((NameValue)action.invocations().getFirst().operation()).name());assertEquals(List.of("url","output"),action.invocations().getFirst().fields().stream().map(FieldAssignment::name).toList());
        var defaults=doc.declarations().stream().filter(d->d.kind()==DeclarationKind.DEFAULTS).findFirst().orElseThrow();assertEquals(2,defaults.targets().size());
        assertEquals("unpack",((LiteralValue)defaults.targets().getFirst()).value());assertEquals("downloadRef",((NameValue)defaults.targets().get(1)).name());
        assertEquals(DeclarationKind.CLEANUP,named(doc,"finish").kind());
    }
    @Test void commentsQuoteKindsAndInterpolationsCannotInventRegistrations(){
        String text="""
            // task('lineFake', Echo) { }
            /* action('blockFake') { } */
            def a = 'task(\\'quotedFake\\', Echo) { }'
            def b = "task('doubleFake', Echo) { }"
            def c = '''task('tripleFake', Echo) { }'''
            def d = \"\"\"action('tripleDoubleFake') { }\"\"\"
            def e = /task('slashFake', Echo) { }/
            def f = $/task('dollarFake', Echo) { }/$
            def g = "${1} task('gFake', Echo) { }"
            action('real') { def text = 'Ein } ist nur Text' }
            defaults 'real'
            """;
        var doc=parse(text);assertTrue(doc.problems().isEmpty(),doc.problems().toString());assertEquals(List.of("real"),doc.declarations().stream().map(Declaration::name).filter(Objects::nonNull).toList());
        assertTrue(doc.tokens().all().stream().anyMatch(t->t.channel()==TokenIndex.Channel.COMMENT));
        String rebuilt=doc.tokens().all().stream().map(TokenIndex.LexToken::text).collect(java.util.stream.Collectors.joining());assertEquals(text,rebuilt);
    }
    @Test void utf16BomCrLfEmojiAndRepeatedWordsHaveOriginalSpans(){
        String text="\uFEFFdef icon='😀'; task('same', Echo) {\r\n\tinput = '😀 same'\r\n}\r\ndefaults 'same', 'other'\r\n";
        var doc=parse(text);assertTrue(doc.problems().isEmpty(),doc.problems().toString());var task=named(doc,"same");
        assertEquals(text.indexOf("task("),task.span().startUtf16());assertEquals("'same'",doc.source().slice(task.nameLiteral().token()));assertEquals("same",doc.source().slice(task.nameLiteral().content()));
        var input=task.fields().getFirst();assertEquals("'😀 same'",doc.source().slice(input.value().span()));assertEquals("input",doc.source().slice(input.nameSpan()));
        var literal=(LiteralValue)input.value();assertEquals("😀 same",literal.value());assertEquals(((String)literal.value()).length()+1,literal.literal().decodedToSource().size());
        assertEquals("\r\n",doc.source().preferredEol());assertTrue(doc.source().hasBom());
        for(int line=0;line<doc.source().lines().lineCount();line++){var span=doc.source().lines().contentSpan(line);for(int i=span.startUtf16();i<=span.endUtf16();i++)assertEquals(i,doc.source().lines().offsetAt(doc.source().lines().positionAt(i)));}
    }
    @Test void stringLiteralAndContentSpansDoNotConfuseQuotesOrEscapes(){
        var doc=parse("action('a\\'b') { println 'done' }; defaults 'a\\'b'");assertTrue(doc.problems().isEmpty(),doc.problems().toString());
        var action=named(doc,"a'b");assertEquals("'a\\'b'",doc.source().slice(action.nameLiteral().token()));assertEquals("a\\'b",doc.source().slice(action.nameLiteral().content()));
        assertEquals(4,action.nameLiteral().decodedToSource().size());assertEquals(action.nameLiteral().content().startUtf16()+1,action.nameLiteral().decodedToSource().get(1));
        var unicode=parse("action('a\\u0042') {}\n");assertEquals("aB",unicode.declarations().getFirst().name());assertEquals(3,unicode.declarations().getFirst().nameLiteral().decodedToSource().size());
    }
    @Test void errorWithoutTasksSurvivesAndRecoveryKeepsClosedIslands(){
        var empty=parse("def quelle =");assertEquals(0,empty.declarations().size());assertFalse(empty.problems().isEmpty());assertTrue(empty.problems().stream().allMatch(p->p.span().startUtf16()>0));
        String text="def broken = ?\n// action('fake') {}\naction('safe') { ctx.run(Echo) { input = 'x' } }\ndefaults 'safe', 'other'\n";
        var doc=parse(text);assertFalse(doc.problems().isEmpty());assertEquals(1,named(doc,"safe").invocations().size());assertTrue(named(doc,"safe").recovered());
        assertEquals(2,doc.declarations().stream().filter(d->d.kind()==DeclarationKind.DEFAULTS).findFirst().orElseThrow().targets().size());
        assertEquals(Completeness.PARTIAL,doc.coverage().syntax());assertTrue(doc.declarations().stream().noneMatch(d->"fake".equals(d.name())));
        assertEquals(doc.problems().size(),doc.problems().stream().map(p->p.code()+p.span()).distinct().count());
    }
    @Test void incompleteHeadsAndBodiesKeepKnownTextWithPartialCoverage(){
        var head=parse("task('unfinished', Http");assertFalse(head.problems().isEmpty());assertEquals("unfinished",head.declarations().getFirst().name());assertNull(head.declarations().getFirst().body());assertTrue(head.declarations().getFirst().recovered());
        var body=parse("task('a', Echo) {\n input = 'safe'\n");assertFalse(body.problems().isEmpty());assertEquals("safe",((LiteralValue)named(body,"a").fields().getFirst().value()).value());assertEquals(Completeness.PARTIAL,named(body,"a").coverage().syntax());
    }
    @Test void dynamicForeignAndShadowedDeclarationsStayConservative(){
        var doc=parse("['a','b'].each { name -> action(name) {} }; builder.task('foreign', Echo) {}; action('summary', dependsOn: 'a') {}; defaults 'summary'");
        assertTrue(doc.problems().isEmpty(),doc.problems().toString());assertEquals(List.of("summary"),doc.declarations().stream().map(Declaration::name).filter(Objects::nonNull).toList());assertEquals(Completeness.PARTIAL,doc.coverage().declarations());
        var shadowed=parse("def task = { a,b,c -> }; task('fake', Echo) {}; action('real') {}");assertTrue(shadowed.problems().isEmpty(),shadowed.problems().toString());assertEquals(List.of("real"),shadowed.declarations().stream().map(Declaration::name).filter(Objects::nonNull).toList());
        var dynamic=parse("task(name, Echo) { input = 'x' }");assertNull(dynamic.declarations().getFirst().name());assertEquals(Completeness.PARTIAL,dynamic.coverage().declarations());
    }
    @Test void localAssignmentsAndInnerReceiverShadowingAreNotConfiguration(){
        var doc=parse("task('a', Echo) { def input='local'; input='changed'; output='field'; output='again' }; action('b') { [1].each { ctx -> ctx.run(Echo) { input='x' } } }");
        assertTrue(doc.problems().isEmpty(),doc.problems().toString());assertEquals(List.of("output","output"),named(doc,"a").fields().stream().map(FieldAssignment::name).toList());assertEquals(0,named(doc,"b").invocations().size());
    }
    @Test void emptyTextAndDocumentKindsKeepStableOriginalIdentity(){
        var empty=parse("");assertTrue(empty.problems().isEmpty(),empty.problems().toString());assertTrue(empty.declarations().isEmpty());
        var source=SourceText.job("include 'a'\r\ndefaults ':a:run'");var settings=parser.parse(source,DocumentKind.SETTINGS,ParseOptions.DEFAULT,ch.so.agi.gretl.tooling.CancellationToken.NONE);
        assertEquals(DocumentKind.SETTINGS,settings.kind());assertEquals(source.stamp(),settings.source().stamp());assertEquals(List.of(DeclarationKind.INCLUDE,DeclarationKind.DEFAULTS),settings.declarations().stream().map(Declaration::kind).toList());
        assertNotEquals(SourceText.job("\uD800").stamp().contentSha256(),SourceText.job("\uD801").stamp().contentSha256());
    }
}
