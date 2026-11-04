package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.query.*;
import ch.so.agi.gretl.tooling.query.QueryResults.*;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.edit.EditVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class LanguageFeatureQueriesTest {
    final LanguageQueries queries=new LanguageQueries();
    QueryContext at(AnalysisSnapshot snapshot,String text,String marker,int inside) {return new QueryContext(snapshot,JOB,text.indexOf(marker)+inside);}
    CompletionProposal proposal(QueryContext context,String label) {return queries.complete(context).stream().filter(p->p.label().equals(label)).findFirst().orElseThrow(()->new AssertionError("Missing "+label+": "+queries.complete(context)));}
    String apply(String source,CompletionProposal proposal) {return source.substring(0,proposal.replacement().startUtf16())+proposal.newText()+source.substring(proposal.replacement().endUtf16());}

    @Test void resultOutputHoverUsesBoundSpiDescriptorAndExactPropertyAnchor() {
        try(var fixture=new Fixture()) {
            var shape=ch.so.agi.gretl.tooling.catalog.OperationCatalog.ValueShape.fromMetadata("path");
            var output=new ch.so.agi.gretl.tooling.catalog.OperationCatalog.OutputDescriptor("file","Downloaded archive",shape,false);
            fixture.catalog.mergeExplicitDescriptors(List.of(new ch.so.agi.gretl.tooling.catalog.OperationCatalog.OperationDescriptor("test.producer","Producer","Producer","fixture",List.of(),List.of(output),"example",false)));
            fixture.manager.onCatalogChanged();
            String source="def producer=task('p', Producer) {}\naction('consumer', dependsOn:'p') { result('p').file; result('p').unknown; resultOptional('p').file; result(producer).file }\n";
            fixture.files.put(JOB,source);var snapshot=fixture.capture();
            var hover=queries.hover(at(snapshot,source,"result('p').file",14)).orElseThrow();
            assertEquals("file : path",hover.title());assertEquals("Downloaded archive",hover.documentation());
            assertEquals("file",source.substring(hover.anchor().span().startUtf16(),hover.anchor().span().endUtf16()));
            for(String marker:List.of("result('p').unknown","resultOptional('p').file","result(producer).file"))assertTrue(queries.hover(at(snapshot,source,marker,marker.length()-2)).isEmpty(),marker);
        }
    }

    @Test void booleanValuesAndTextEnumsAreDistinctFromUriSchemesAndApplyWithoutNewErrors() {
        try(var fixture=new Fixture()) {
            var fields=List.of(
                    new ch.so.agi.gretl.tooling.catalog.OperationCatalog.InputDescriptor("url","URL",false,false,null,List.of("http","https"),"url",new ch.so.agi.gretl.tooling.catalog.OperationCatalog.ValueShape("Opaque","uri",List.of()),null,false,java.util.Map.of()),
                    new ch.so.agi.gretl.tooling.catalog.OperationCatalog.InputDescriptor("enabled","Enabled",false,false,null,List.of(),"enabled",ch.so.agi.gretl.tooling.catalog.OperationCatalog.ValueShape.fromMetadata("boolean"),null,false,java.util.Map.of()));
            fixture.catalog.mergeExplicitDescriptors(List.of(new ch.so.agi.gretl.tooling.catalog.OperationCatalog.OperationDescriptor("test.hints","Hints","Hints","fixture",fields,List.of(),"example",true)));fixture.manager.onCatalogChanged();
            String source="task('a', Hints) { url='https://example.invalid'; enabled=true }";
            fixture.files.put(JOB,source);var snapshot=fixture.capture();
            assertTrue(queries.complete(at(snapshot,source,"https://",3)).isEmpty(),"URI scheme restrictions are not whole-value enums");
            var change=proposal(at(snapshot,source,"true",2),"false");
            assertEquals("false",change.newText());
            var replacement=new SourceReplacement(change.replacement(),source.substring(change.replacement().startUtf16(),change.replacement().endUtf16()),change.newText());
            var plan=new EditVerifier().verify(snapshot,java.util.Map.of(JOB,List.of(replacement)),"Complete Boolean",post->post.analysis().diagnostics().isEmpty(),List.of(),ch.so.agi.gretl.tooling.CancellationToken.NONE);
            assertTrue(plan.postimages().get(JOB).contains("enabled=false"));
        }
    }

    @Test void settingsAndInnerInvocationsUseCapturedProjectAndCatalogInformation() {
        try(var fixture=new Fixture()) {
            var part=java.net.URI.create("file:/work/part/job.groovy");
            fixture.files.put(SETTINGS,"include 'part'\ndefaults ':part:a'\n");
            fixture.files.put(part,"task('a', Echo) { input='part' }\n");
            fixture.files.put(JOB,"action('root') { ctx.run(Echo) { input='inside' } }\n");
            var snapshot=fixture.capture();
            var defaults=new QueryContext(snapshot,SETTINGS,fixture.files.get(SETTINGS).indexOf(":part:a")+2);
            assertTrue(queries.complete(defaults).stream().anyMatch(p->p.label().equals(":part:a")&&p.newText().equals("':part:a'")));
            var include=new QueryContext(snapshot,SETTINGS,fixture.files.get(SETTINGS).indexOf("'part'")+2);
            assertTrue(queries.complete(include).stream().anyMatch(p->p.label().equals(":part")));
            assertTrue(queries.signatureHelp(include).orElseThrow().label().startsWith("include("));
            var inner=at(snapshot,fixture.files.get(JOB),"Echo",1);
            assertEquals("Echo",proposal(inner,"Echo").newText());assertTrue(queries.hover(inner).isPresent());
            assertTrue(queries.signatureHelp(inner).orElseThrow().label().startsWith("ctx.run("));
        }
    }

    @Test void completionUsesCatalogAndExactQuotedReplacementInsteadOfDoublingQuotes() {
        try(var fixture=new Fixture()) {
            String source="parameter('arg', type:'text', defaultValue:'x')\n"
                    +"task('a', Echo, description:'A') { input=parameter('arg'); mode='FAST' }\n"
                    +"action('c', dependsOn:'a') { println result('a') }\ndefaults 'c'\n";
            fixture.files.put(JOB,source);
            var snapshot=fixture.capture();
            var operation=proposal(at(snapshot,source,"Echo",1),"Echo");
            assertEquals("Echo",source.substring(operation.replacement().startUtf16(),operation.replacement().endUtf16()));
            var value=proposal(at(snapshot,source,"'FAST'",2),"SLOW");
            assertTrue(apply(source,value).contains("mode='SLOW'"));
            assertFalse(apply(source,value).contains("''SLOW''"));
            var parameter=proposal(at(snapshot,source,"parameter('arg')",12),"arg");
            assertEquals("'arg'",source.substring(parameter.replacement().startUtf16(),parameter.replacement().endUtf16()));
            var option=proposal(at(snapshot,source,"description:",3),"timeout");
            assertTrue(apply(source,option).contains("timeout:'A'"));
            assertFalse(apply(source,option).contains("timeout: :"));
            assertEquals("'a'",proposal(at(snapshot,source,"dependsOn:'a'",12),"a").newText());
        }
    }

    @Test void fieldsPathsAndResultPortsComeFromTheirOwnedSnapshotContexts() {
        try(var fixture=new Fixture()) {
            new DependencySourceEditsTest().catalog(fixture);
            String source="task('p', Source) {}\n"
                    +"task('a', Echo) {\n    input=file('query.sql')\n    \n}\n"
                    +"task('c', Sink, dependsOn:'p') { input=result('p').file }\n";
            fixture.files.put(JOB,source);
            var path=java.net.URI.create("file:/work/query.sql");fixture.files.put(path,"select 1");
            var snapshot=fixture.capture(List.of(path));
            var field=proposal(at(snapshot,source,"    \n}",3),"mode");
            assertEquals("mode = ",field.newText());
            var file=proposal(at(snapshot,source,"'query.sql'",3),"query.sql");
            assertTrue(apply(source,file).contains("file('query.sql')"));
            var output=proposal(at(snapshot,source,".file",3),"file");
            assertEquals("file",source.substring(output.replacement().startUtf16(),output.replacement().endUtf16()));
            fixture.files.put(JOB,"def p=task('p', Source) {}; action('c',dependsOn:p) { println result(p).file }");
            var raw=fixture.capture();
            assertTrue(queries.complete(at(raw,fixture.files.get(JOB),".file",3)).isEmpty(),"TaskRef returns the raw object, not the SPI view");
        }
    }

    @Test void commentsForeignCallsOpaqueExpressionsAndOrdinaryStringsDoNotReceiveDslProposals() {
        try(var fixture=new Fixture()) {
            String source="task('a', Echo) {\n input=foreign('FAST')\n foreign(mode:'FAST')\n // mode input\n}\n"
                    +"def ordinary='Echo'\nforeign('Echo')\n";
            fixture.files.put(JOB,source);var snapshot=fixture.capture();
            for(String marker:List.of("foreign('FAST')","foreign(mode:","// mode","ordinary='Echo'","foreign('Echo')")) {
                var context=at(snapshot,source,marker,marker.length()-2);
                assertTrue(queries.complete(context).isEmpty(),marker+": "+queries.complete(context));
                assertTrue(queries.signatureHelp(context).isEmpty(),marker);
            }
        }
    }

    @Test void partiallyTypedOwnedGapsRemainUsefulButForeignHeaderCallsAndCommentEndsStayQuiet() {
        try(var fixture=new Fixture()) {
            for(String source:List.of("task('a', Ec) { input='x' }","task('a', ) { input='x' }")) {
                fixture.files.put(JOB,source);var snapshot=fixture.capture();
                int position=source.contains("Ec")?source.indexOf("Ec")+1:source.indexOf(",")+2;
                assertEquals("Echo",proposal(new QueryContext(snapshot,JOB,position),"Echo").newText());
            }
            fixture.files.put(JOB,"task('a', Echo) {\n    mo\n}");
            var field=fixture.capture();assertEquals("mode = ",proposal(at(field,fixture.files.get(JOB),"mo",2),"mode").newText());
            for(String source:List.of("task('a', Echo, timeout: foreign('x')) { input='x' }","task('a', Echo, dependsOn: foreign('x')) { input='x' }")) {
                fixture.files.put(JOB,source);var snapshot=fixture.capture();var context=at(snapshot,source,"foreign('x')",9);
                assertTrue(queries.complete(context).isEmpty());assertTrue(queries.signatureHelp(context).isEmpty());
            }
            fixture.files.put(JOB,"// trailing comment");var comment=fixture.capture();
            assertTrue(queries.complete(new QueryContext(comment,JOB,fixture.files.get(JOB).length())).isEmpty());
        }
    }

    @Test void signaturesRenameAndQuickFixesUseCurrentDslAndApplyRealPostimages() {
        try(var fixture=new Fixture()) {
            new DependencySourceEditsTest().catalog(fixture);
            String source="parameter('arg',type:'integer',defaultValue:7)\n"
                    +"task('p', Source) {}\ntask('c', Sink) { input=result('p').file; println parameter('arg',Long) }\n";
            fixture.files.put(JOB,source);var snapshot=fixture.capture();
            assertTrue(queries.signatureHelp(at(snapshot,source,"task('c', Sink",12)).orElseThrow().label().contains("when: Closure"));
            assertTrue(queries.signatureHelp(at(snapshot,source,"parameter('arg',Long)",17)).orElseThrow().label().contains("resolves to Long"));
            var rename=queries.rename(at(snapshot,source,"task('p'",7),"producer").plan().orElseThrow();
            assertTrue(rename.postimages().get(JOB).contains("result('producer').file"));
            var diagnostic=snapshot.analysis().diagnostics().stream().filter(d->d.code().equals("RESULT_DEPENDENCY_REQUIRED")).findFirst().orElseThrow();
            var context=at(snapshot,source,"result('p')",8);
            var actions=queries.codeActions(context,List.of(new DiagnosticId(diagnostic.code(),diagnostic.anchor())));
            assertEquals(1,actions.size());
            assertThrows(java.util.concurrent.CancellationException.class,()->queries.codeActions(context,List.of(new DiagnosticId(diagnostic.code(),diagnostic.anchor())),()->true));
            assertThrows(java.util.concurrent.CancellationException.class,()->queries.applyQuickFix(context,actions.getFirst(),()->true));
            var fixed=queries.applyQuickFix(context,actions.getFirst()).plan().orElseThrow();
            assertTrue(fixed.postimages().get(JOB).contains("dependsOn: 'p'"));
            assertFalse(fixed.postimage().analysis().diagnostics().stream().anyMatch(d->d.code().equals("RESULT_DEPENDENCY_REQUIRED")));
            assertTrue(queries.codeActions(context,List.of(new DiagnosticId("WRONG",diagnostic.anchor()))).isEmpty());
            assertThrows(IllegalArgumentException.class,()->queries.applyQuickFix(new QueryContext(fixed.postimage(),JOB,0),actions.getFirst()));
        }
    }

    @Test void signaturePositionsDistinguishNamedOptionEntriesFromSeparateArgumentsAndLeadingMaps() {
        try(var fixture=new Fixture()) {
            String named="task('a', Echo, description:'A', timeout:'PT1S') { input='x' }";
            fixture.files.put(JOB,named);var snapshot=fixture.capture();
            assertEquals(2,queries.signatureHelp(at(snapshot,named,"PT1S",2)).orElseThrow().activeParameter());
            assertEquals(1,queries.signatureHelp(at(snapshot,named,"Echo",2)).orElseThrow().activeParameter());
            String explicit="task([description:'A'], 'a', Echo) { input='x' }";
            fixture.files.put(JOB,explicit);var leading=fixture.capture();
            var signature=queries.signatureHelp(at(leading,explicit,"description",2)).orElseThrow();
            assertEquals(0,signature.activeParameter());assertTrue(signature.parameters().getFirst().startsWith("options:"));
            assertEquals(2,queries.signatureHelp(at(leading,explicit,"Echo",2)).orElseThrow().activeParameter());
        }
    }
}
