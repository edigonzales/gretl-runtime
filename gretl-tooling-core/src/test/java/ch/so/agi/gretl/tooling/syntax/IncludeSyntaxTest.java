package ch.so.agi.gretl.tooling.syntax;

import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.IncludeModel.*;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectId;
import java.net.URI;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IncludeSyntaxTest {
    private SyntaxModel.ParsedDocument parse(String text) {
        return new GretlDocumentParser().parse(new SourceText(URI.create("file:/work/Map.groovy"),text,null,"TEST",false));
    }
    @Test void bothFormsKeepOriginalUtf16RangesAndApplySiteOrder() {
        String text="\ufeff// ä😀\r\ntask('before',Echo){}\r\napply from: 'tasks/ä😀.groovy'\r\napply(from: \"second.groovy\")\r\ntask('after',Echo){}\r\n";
        var parsed=parse(text);assertTrue(parsed.problems().isEmpty(),parsed.problems().toString());
        var includes=parsed.includes();assertEquals(2,includes.directives().size());assertTrue(includes.problems().isEmpty());
        var first=includes.directives().getFirst();assertEquals("tasks/ä😀.groovy",first.from());
        assertEquals("'tasks/ä😀.groovy'",text.substring(first.literalStart(),first.literalEnd()));
        assertEquals(3,first.line());assertEquals("file:/work/Map.groovy@"+first.start(),first.siteId());
        var sequence=RegistrationSequence.local(parsed);
        assertEquals(java.util.List.of("DeclarationEvent","IncludeEvent","IncludeEvent","DeclarationEvent"),sequence.stream().map(e->e.getClass().getSimpleName()).toList());
        assertEquals(SyntaxModel.Completeness.PARTIAL,parsed.coverage().declarations(),"Unresolved closure is not complete");
    }
    @Test void ordinaryCallsAndStringsAreNotIncludesAndInvalidContextStaysPartial() {
        var ordinary=parse("// apply from:'comment.groovy'\ndef value=\"apply from: 'text.groovy'\"\ndef apply(String value){}\napply('ordinary')\nother.apply(from:'receiver.groovy')");
        assertTrue(ordinary.includes().directives().isEmpty());assertFalse(ordinary.includes().applySeen());
        var invalid=parse("if(true){apply from:'conditional.groovy'}\napply from: variable");
        assertEquals(java.util.Set.of("APPLY_CONTEXT","APPLY_SYNTAX"),invalid.includes().problems().stream().map(p->p.code()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(SyntaxModel.Completeness.PARTIAL,invalid.coverage().declarations());
    }
    @Test void returnAndRecoveryCannotBecomeCompleteAndAstTransformsDoNotRun(@TempDir Path temp) {
        var returning=parse("return\napply from:'a.groovy'\ntask('after',Echo){}");
        assertEquals(SyntaxModel.Completeness.PARTIAL,returning.coverage().declarations());
        assertTrue(returning.includes().topLevelControlFlowUncertain());
        assertEquals(1,parse("defaults 'x'").includes().fragmentProblems().size());
        var broken=parse("apply from:'a.groovy'\ntask('broken',Echo){");
        assertFalse(broken.problems().isEmpty());assertEquals(SyntaxModel.Completeness.PARTIAL,broken.coverage().syntax());
        Path marker=temp.resolve("ran");
        String text="@groovy.transform.ASTTest(value={new File('"+marker+"').text='ran'})\ndef value\nnew File('"+marker+"').text='top'\napply from:'a.groovy'";
        assertEquals(1,parse(text).includes().directives().size());assertFalse(Files.exists(marker));
    }
    @Test void onePhysicalSourceHasSeparateApplicationsAndResourceBases() {
        URI root=URI.create("file:/work/"),source=URI.create("file:/work/shared/tasks.groovy");
        var project=new ProjectId(root,root.resolve("settings.gretl"));
        var north=new SourceApplication(new ApplicationId(project,root.resolve("north/job.groovy"),":north",source),root.resolve("north/"),SourceRole.FRAGMENT,java.util.List.of());
        var south=new SourceApplication(new ApplicationId(project,root.resolve("south/job.groovy"),":south",source),root.resolve("south/"),SourceRole.FRAGMENT,java.util.List.of());
        assertNotEquals(north.id(),south.id());assertEquals(north.id().sourceDocument(),south.id().sourceDocument());assertNotEquals(north.resourceBase(),south.resourceBase());
    }
    @Test void expansionIsDepthFirstAtSitesAndOpaqueControlFlowCannotProveOrder() {
        URI root=URI.create("file:/work/"),entry=root.resolve("job.groovy"),child=root.resolve("parts/child.groovy"),nested=root.resolve("parts/nested.groovy");
        var project=new ProjectId(root,entry);
        var a=new SourceApplication(new ApplicationId(project,entry,":",entry),root,SourceRole.ENTRY,java.util.List.of());
        var b=new SourceApplication(new ApplicationId(project,entry,":",child),root,SourceRole.FRAGMENT,java.util.List.of());
        var c=new SourceApplication(new ApplicationId(project,entry,":",nested),root,SourceRole.FRAGMENT,java.util.List.of());
        var parser=new GretlDocumentParser();
        var docs=new java.util.HashMap<URI,SyntaxModel.ParsedDocument>();
        docs.put(entry,parser.parse(new SourceText(entry,"task('before',Echo){}\napply from:'parts/child.groovy'\ntask('after',Echo){}",null,"TEST",false)));
        docs.put(child,parser.parse(new SourceText(child,"task('outer',Echo){}\napply from:'nested.groovy'\ntask('tail',Echo){}",null,"TEST",false)));
        docs.put(nested,parser.parse(new SourceText(nested,"task('inner',Echo){}",null,"TEST",false)));
        var links=java.util.List.of(new IncludeLink(a.id(),new IncludeSiteId(entry,docs.get(entry).includes().directives().getFirst().start()),child,b,IncludeResolution.RESOLVED),
                new IncludeLink(b.id(),new IncludeSiteId(child,docs.get(child).includes().directives().getFirst().start()),nested,c,IncludeResolution.RESOLVED));
        var expansion=RegistrationSequence.expand(a,docs,links,ch.so.agi.gretl.tooling.CancellationToken.NONE);
        assertTrue(expansion.orderProven());assertEquals(java.util.List.of("before","outer","inner","tail","after"),expansion.registrations().stream().map(e->e.declaration().name()).toList());
        assertEquals(child,expansion.registrations().get(1).application().id().sourceDocument());
        docs.put(nested,parser.parse(new SourceText(nested,"return\ntask('inner',Echo){}",null,"TEST",false)));
        assertFalse(RegistrationSequence.expand(a,docs,links,ch.so.agi.gretl.tooling.CancellationToken.NONE).orderProven());
        assertFalse(RegistrationSequence.expand(a,docs,java.util.List.of(),ch.so.agi.gretl.tooling.CancellationToken.NONE).orderProven());
    }

    @Test void helperMapAliasesStayOrdinaryWithoutLoadingHelperTypes() {
        String helper="import java.util.Map as Options\nclass Helper { def apply(Options options){}; def run(){ apply(from:'helper.groovy') } }\n";
        var parsed=parse(helper+"apply from:'real.groovy'");
        assertTrue(parsed.includes().problems().isEmpty());
        assertEquals(java.util.List.of("real.groovy"),parsed.includes().directives().stream().map(d->d.from()).toList());
        assertTrue(parse("class Map {}\napply from:'real.groovy'").includes().problems().isEmpty());
    }

}
