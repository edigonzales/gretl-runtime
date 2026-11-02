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
    @Test void everyOptionValueUsesTheSameConservativeReferenceWalk(){
        for(String option:List.of("when: { c -> builder.check() }","when: builder.check()","description: builder.describe()")){
            var doc=parser.parse(SourceText.job("task('a', Echo, "+option+") { input='x' }"));
            assertTrue(doc.problems().isEmpty(),doc.problems().toString());var task=doc.declarations().getFirst();
            assertEquals(Completeness.PARTIAL,task.coverage().references(),option);
            assertEquals(Completeness.COMPLETE,task.coverage().fieldAssignments(),option);
            assertEquals(List.of("input"),task.fields().stream().map(FieldAssignment::name).toList());
        }
        var known=parser.parse(SourceText.job("task('a', Echo, when: { c -> c.parameter('enabled', Boolean) }) { input='x' }"));
        assertEquals(Completeness.COMPLETE,known.declarations().getFirst().coverage().references());
        assertEquals(1,known.declarations().getFirst().references().size());
        var header=parser.parse(SourceText.job("task('a', builder.choose()) { input='x' }"));
        assertEquals(Completeness.PARTIAL,header.declarations().getFirst().coverage().references());
    }
    @Test void optionInvocationsAndLocalsKeepOriginalSpansAndDoNotBecomeDagTasks(){
        String text="task('a', Echo, when: { c -> def local='flag'; c.run(Echo) { input=result('source').file } }) { input='x'; ctx.run(Echo) { input='body' } }";
        var doc=parser.parse(SourceText.job(text));assertTrue(doc.problems().isEmpty(),doc.problems().toString());
        assertEquals(1,doc.declarations().size());var task=doc.declarations().getFirst();assertEquals(2,task.invocations().size());
        assertTrue(task.invocations().getFirst().span().endUtf16()<task.invocations().get(1).span().startUtf16());
        assertTrue(doc.source().slice(task.invocations().getFirst().span()).startsWith("c.run(Echo)"));
        assertEquals("result('source').file",doc.source().slice(task.invocations().getFirst().fields().getFirst().value().span()));
        assertEquals(List.of("local"),task.locals().stream().map(LocalVariable::name).toList());
        assertEquals(List.of("input"),task.fields().stream().map(FieldAssignment::name).toList());
        assertEquals(List.of("file"),task.properties().stream().map(PropertyValue::property).toList());
        assertEquals(1,task.references().stream().filter(r->r.name().equals("result")).count());
    }
    @Test void optionLocalShadowingDoesNotLeakIntoTheTaskBody(){
        var doc=parser.parse(SourceText.job("task('a', Echo, when: { c -> def result={n->n}; result('opaque') }) { input=result('source') }"));
        assertTrue(doc.problems().isEmpty(),doc.problems().toString());var task=doc.declarations().getFirst();
        assertEquals(Completeness.PARTIAL,task.coverage().references());
        assertEquals(1,task.references().size());assertEquals("source",((LiteralValue)task.references().getFirst().arguments().getFirst()).value());
    }
    @Test void configIsTheCurrentHelperAndTaskConfigIsNotAnInventedAlias(){
        var doc=parser.parse(SourceText.job("task('a', Echo) { input=config('key') }; task('b', Echo) { input=taskConfig('key') }"));
        assertEquals(Completeness.COMPLETE,doc.declarations().getFirst().coverage().references());
        assertInstanceOf(CallValue.class,doc.declarations().getFirst().fields().getFirst().value());
        assertEquals(Completeness.PARTIAL,doc.declarations().get(1).coverage().references());
        assertInstanceOf(OpaqueExpression.class,doc.declarations().get(1).fields().getFirst().value());
    }
    @Test void capturedTopLevelVariablesCannotMasqueradeAsContextOrTaskFields(){
        var context=parser.parse(SourceText.job("def ctx=[parameter:{ n->n }]; task('a', Echo) { input=ctx.parameter('hidden') }"));
        assertTrue(context.problems().isEmpty());var task=context.declarations().getFirst();
        assertTrue(task.references().isEmpty());assertEquals(Completeness.PARTIAL,task.coverage().references());
        assertInstanceOf(OpaqueExpression.class,task.fields().getFirst().value());
        var field=parser.parse(SourceText.job("def input='captured'; task('a', Echo) { input='local assignment'; output='field' }"));
        assertTrue(field.problems().isEmpty());assertEquals(List.of("output"),field.declarations().getFirst().fields().stream().map(FieldAssignment::name).toList());
        var unknown=parser.parse(SourceText.job("task('a', Echo) { def local=foreignConfigure(); input='known' }"));
        assertEquals(List.of("input"),unknown.declarations().getFirst().fields().stream().map(FieldAssignment::name).toList());
        assertEquals(Completeness.PARTIAL,unknown.declarations().getFirst().coverage().fieldAssignments());
    }
    @Test void explicitContextParametersOverrideCapturedNamesButImplicitContextsDoNot(){
        var doc=parser.parse(SourceText.job("def ctx=[parameter:{ n->n }]; task('a', Echo) { ctx -> input=ctx.parameter('real') }; task('b', Echo, when:{ ctx.parameter('foreign') }) { input='x' }; action('c') { ctx.run(Echo) { input='notAnInvocation' } }"));
        assertTrue(doc.problems().isEmpty(),doc.problems().toString());var real=doc.declarations().getFirst();
        assertEquals(Completeness.COMPLETE,real.coverage().references());assertEquals(1,real.references().size());
        assertEquals("real",((LiteralValue)real.references().getFirst().arguments().getFirst()).value());
        assertTrue(doc.declarations().get(1).references().isEmpty());assertEquals(Completeness.PARTIAL,doc.declarations().get(1).coverage().references());
        assertTrue(doc.declarations().get(2).invocations().isEmpty());assertEquals(Completeness.PARTIAL,doc.declarations().get(2).coverage().references());
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
