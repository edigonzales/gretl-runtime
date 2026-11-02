package ch.so.agi.gretl.tooling.syntax;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import ch.so.agi.gretl.tooling.source.*;
import static org.junit.jupiter.api.Assertions.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

class ParserAdversarialTest {
    private final GretlDocumentParser parser=new GretlDocumentParser();
    @Test void malformedTokensNeverThrowOrInventTaskText(){
        var cases=List.of("'", "\"", "'''", "\"\"\"", "$/", "/**", "def x='\\uZZZZ'", "def x=\"${\"", "def x=$/test ${1}/$", "def x='a\\\n", "}\naction('safe') {}", "task('a', Echo, dependsOn: [)", "def \u0000 x", "@", "def a=\"$value", "def a = $/unterminated", "action('a'){ -> ");
        for(String text:cases){var doc=assertDoesNotThrow(()->parser.parse(SourceText.job(text)),text);assertEquals(text,doc.source().text());assertTrue(doc.declarations().stream().noneMatch(d->"fake".equals(d.name())));}
    }
    @Test void aliasesAndLocalHelperShadowingRemainScopeAware(){
        var doc=parser.parse(SourceText.job("task('a', Echo) { c -> input = c.parameter('p') }; task('b', Echo) { def parameter={v->v}; input=parameter('notAReference') }"));
        assertTrue(doc.problems().isEmpty(),doc.problems().toString());assertInstanceOf(CallValue.class,doc.declarations().getFirst().fields().getFirst().value());
        var other=doc.declarations().get(1);assertInstanceOf(OpaqueExpression.class,other.fields().getFirst().value());assertTrue(other.references().stream().noneMatch(r->r.name().equals("parameter")));
    }
    @Test void opaqueActionCallsReduceReferenceCoverageWithoutHidingOtherFields(){
        var doc=parser.parse(SourceText.job("action('a') { custom(ctx) }; task('b', Echo) { def filename='x'; input=workPath(filename) }"));
        assertTrue(doc.problems().isEmpty());assertEquals(Completeness.PARTIAL,doc.declarations().getFirst().coverage().references());
        assertEquals(Completeness.COMPLETE,doc.declarations().get(1).coverage().fieldAssignments());assertEquals(Completeness.COMPLETE,doc.declarations().get(1).coverage().references());
    }
    @Test void closureParametersAndCapturedHelpersDoNotBecomeFieldsOrReferences(){
        var doc=parser.parse(SourceText.job("task('a', Echo) { input -> input='context'; output='field' }; action('b') { result -> result('notAHelper') }; action('c') { def parameter={v->v}; ctx.run(Echo) { input=parameter('notAReference') } }"));
        assertTrue(doc.problems().isEmpty(),doc.problems().toString());assertEquals(List.of("output"),doc.declarations().getFirst().fields().stream().map(FieldAssignment::name).toList());
        assertTrue(doc.declarations().get(1).references().isEmpty());assertTrue(doc.declarations().get(2).references().stream().noneMatch(r->r.name().equals("parameter")));
    }
    @Test void staticImportsAndMethodsDoNotMasqueradeAsDsl(){
        for(String prefix:List.of("import static java.util.Collections.emptyList as task\n", "def task(a,b,c) { }\n")){
            var doc=parser.parse(SourceText.job(prefix+"task('fake', Echo) {}; action('real') {}"));
            assertTrue(doc.problems().isEmpty(),doc.problems().toString());assertEquals(List.of("real"),doc.declarations().stream().map(Declaration::name).filter(Objects::nonNull).toList());
        }
    }
    @Test void allCurrentRepositoryGroovyExamplesParseWithoutExecution() throws Exception {
        Path examples=Path.of("../examples");int count=0;
        try(var files=Files.walk(examples)){
            for(Path file:files.filter(p->p.toString().endsWith(".groovy")).toList()){
                String text=Files.readString(file);var source=new SourceText(file.toAbsolutePath().normalize().toUri(),text,1,"TEST",false);
                var doc=parser.parse(source);assertTrue(doc.problems().isEmpty(),file+": "+doc.problems());assertEquals(text,doc.source().text());count++;
            }
        }
        assertTrue(count>=11,"Missing current repository fixtures (baseline 11 Groovy sources): "+count);
    }
}
