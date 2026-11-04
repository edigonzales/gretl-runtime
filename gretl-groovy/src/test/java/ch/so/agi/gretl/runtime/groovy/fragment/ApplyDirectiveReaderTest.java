package ch.so.agi.gretl.runtime.groovy.fragment;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ApplyDirectiveReaderTest {
    final ApplyDirectiveReader reader=new ApplyDirectiveReader();
    @TempDir Path temp;
    @ParameterizedTest @ValueSource(strings={"apply from: 'tasks/a.groovy'", "apply(from: \"tasks/a.groovy\")", "  apply /* comment */ from: 'tasks/a.groovy'\r\n", "apply from: 'tasks/\\u0061.groovy'"})
    void readsBothFormsAndEscapes(String source){
        var result=reader.read("job.groovy",source);assertTrue(result.problems().isEmpty(),result.toString());
        assertEquals("tasks/a.groovy",result.directives().getFirst().from());
        var d=result.directives().getFirst();assertTrue(source.substring(d.literalStart(),d.literalEnd()).startsWith("'" )||source.substring(d.literalStart(),d.literalEnd()).startsWith("\""));
    }
    @ParameterizedTest @ValueSource(strings={"apply from: path", "apply from: \"${path}\"", "apply from: ('a' + '.groovy')", "apply from: file('a.groovy')", "apply plugin: 'x'", "apply from: 'a.groovy', optional: true", "apply(['a.groovy'])", "apply from: '''a.groovy'''", "apply from: /a.groovy/", "apply from: 'a.groovy', { }", "apply from: ''"})
    void rejectsNonLiteralOrUnsupportedOptions(String source){assertFalse(reader.read("job.groovy",source).problems().isEmpty(),source);}
    @ParameterizedTest @ValueSource(strings={"if(true) { apply from: 'a.groovy' }", "for(x in []) { apply from: 'a.groovy' }", "action('a') { apply from: 'a.groovy' }", "def helper() { apply from: 'a.groovy' }", "class Helper { void x() { apply from: 'a.groovy' } }", "class Helper { def x = { apply from: 'a.groovy' } }", "def helper(){def apply=1}; if(true){apply from:'a.groovy'}", "this.apply from: 'a.groovy'", "def apply = { x -> }; apply from: 'a.groovy'", "def apply(x) { }; apply from: 'a.groovy'"})
    void rejectsContextAndScriptShadowing(String source){assertTrue(reader.read("job.groovy",source).problems().stream().anyMatch(p->p.code().equals("APPLY_CONTEXT")),source);}
    @Test void commentsStringsForeignReceiversAndHelperMethodsDoNotInventIncludes(){
        var result=reader.read("job.groovy","// apply from: 'fake.groovy'\ndef text=\"apply from: 'fake.groovy'\"\nother.apply(from:'foreign.groovy')\nclass Helper { void apply(x) {} }\napply from: 'real.groovy'\n");
        assertEquals(1,result.directives().size());assertTrue(result.problems().isEmpty(),result.toString());assertEquals("real.groovy",result.directives().getFirst().from());
    }
    @ParameterizedTest @ValueSource(strings={
        "class Helper { void apply(String x){}; void run(){apply('local')} }; apply from:'real.groovy'",
        "class Helper { void apply(Map x){}; void run(){apply from:'local'} }; apply from:'real.groovy'",
        "class Helper { void apply(String x){}; void run(){this.apply('local')} }; apply from:'real.groovy'"})
    void helperOwnedApplyCallsRemainOrdinaryGroovy(String source){
        var result=reader.read("job.groovy",source);
        assertTrue(result.problems().isEmpty(),result.toString());assertEquals(1,result.directives().size());
        assertEquals("real.groovy",result.directives().getFirst().from());
    }
    @ParameterizedTest @ValueSource(strings={"def apply(String x){x}; apply('local')", "def apply={x->x}; apply('local')"})
    void jobsWithoutIncludesKeepTheirOwnApply(String source){
        var result=reader.read("job.groovy",source);assertTrue(result.problems().isEmpty(),result.toString());assertTrue(result.directives().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={
        "def apply(x){}; apply from:path",
        "def apply(x){}; if(true){apply from:'a.groovy'}",
        "def apply(x){}; class Helper {void run(){apply from:'a.groovy'}}",
        "def apply={x->x}; apply from:path"})
    void scriptOwnedApplyDoesNotHideMalformedGretlMapCalls(String source){
        assertFalse(reader.read("job.groovy",source).problems().isEmpty(),source);
    }
    @ParameterizedTest @ValueSource(strings={
        "class Base{void apply(String x){}}; class Helper extends Base{void run(){apply('local')}}; apply from:'real.groovy'",
        "class Helper{Closure apply={x->x}; void run(){apply('local')}}; apply from:'real.groovy'"})
    void helperInheritedMethodsAndClosureFieldsRemainOrdinary(String source){
        var result=reader.read("job.groovy",source);assertTrue(result.problems().isEmpty(),result.toString());assertEquals(1,result.directives().size());
    }
    @Test void helperStringOverloadDoesNotHideMapFirstGretlCall(){
        var result=reader.read("job.groovy","class Helper{void apply(String x){}; void run(){apply from:'hidden.groovy'}}; apply from:'real.groovy'");
        assertTrue(result.problems().stream().anyMatch(p->p.code().equals("APPLY_CONTEXT")),result.toString());
    }
    @ParameterizedTest @ValueSource(strings={
        "class Base{void apply(Map x){}}; class Helper extends Base{void run(){apply from:'local'}}; apply from:'real.groovy'",
        "class Helper{void apply(Map x,String label='x'){}; void run(){apply from:'local'}}; apply from:'real.groovy'",
        "class Helper{void apply(Map... x){}; void run(){apply from:'local'}}; apply from:'real.groovy'",
        "import java.util.Map as Options; class Helper{void apply(Options x){}; void run(){apply from:'local'}}; apply from:'real.groovy'"})
    void mapCompatibleHelperCallsKeepTheirOwnReceiver(String source){
        var result=reader.read("job.groovy",source);assertTrue(result.problems().isEmpty(),result.toString());assertEquals(1,result.directives().size());
    }
    @ParameterizedTest @ValueSource(strings={
        "class Helper{Closure apply={ -> 'x'}; void run(){apply from:'hidden.groovy'}}; apply from:'real.groovy'",
        "class Helper{Closure apply={String x -> x}; void run(){apply from:'hidden.groovy'}}; apply from:'real.groovy'"})
    void incompatibleHelperClosureCannotHideMapFirstCall(String source){
        assertTrue(reader.read("job.groovy",source).problems().stream().anyMatch(p->p.code().equals("APPLY_CONTEXT")),source);
    }
    @ParameterizedTest @ValueSource(strings={
        "[1].each {def apply=1}; apply from:'real.groovy'",
        "if(true){def apply=1}; apply from:'real.groovy'",
        "class Helper{Closure apply={Map x->x}; void run(){apply from:'local'}}; apply from:'real.groovy'",
        "class Helper{Closure apply={it}; void run(){apply from:'local'}}; apply from:'real.groovy'"})
    void localNamesAndCompatibleClosureFieldsPreserveIncludes(String source){
        var result=reader.read("job.groovy",source);assertTrue(result.problems().isEmpty(),result.toString());assertEquals(1,result.directives().size());
    }
    @Test void helperDefaultInterfaceMethodRetainsItsReceiver(){
        var result=reader.read("job.groovy","interface Base{default void apply(Map x){}}; class Helper implements Base{void run(){apply from:'local'}}; apply from:'real.groovy'");
        assertTrue(result.problems().isEmpty(),result.toString());assertEquals(1,result.directives().size());
    }
    @Test void castClosureLiteralStillChecksItsArity(){
        var result=reader.read("job.groovy","class Helper{Closure apply=({ -> 'x'} as Closure); void run(){apply from:'hidden.groovy'}}; apply from:'real.groovy'");
        assertTrue(result.problems().stream().anyMatch(p->p.code().equals("APPLY_CONTEXT")),result.toString());
    }
    @ParameterizedTest @ValueSource(strings={
        "class Helper extends external.Base{void run(){apply from:'external'}}; apply from:'real.groovy'",
        "class Helper implements external.Api{void run(){apply from:'external'}}; apply from:'real.groovy'",
        "import groovy.lang.Closure as Callback; class Helper{Callback apply=({Map x->x} as Callback); void run(){apply from:'local'}}; apply from:'real.groovy'"})
    void unknownHelperTypesStayOpaqueAndClosureAliasesWork(String source){
        var result=reader.read("job.groovy",source);assertTrue(result.problems().isEmpty(),result.toString());assertEquals(1,result.directives().size());
    }
    @Test void castClosureAliasStillChecksItsArity(){
        var result=reader.read("job.groovy","import groovy.lang.Closure as Callback; class Helper{Callback apply=({->1} as Callback); void run(){apply from:'hidden.groovy'}}; apply from:'real.groovy'");
        assertTrue(result.problems().stream().anyMatch(p->p.code().equals("APPLY_CONTEXT")),result.toString());
    }
    @ParameterizedTest @ValueSource(strings={
        "class Helper{def <T> apply(T x){x}; void run(){apply from:'local'}}; apply from:'real.groovy'",
        "class Helper<T>{void apply(T x){}; void run(){apply from:'local'}}; apply from:'real.groovy'",
        "class Helper{def <T> apply(T... x){x}; void run(){apply from:'local'}}; apply from:'real.groovy'",
        "class Helper{def <T extends Map> apply(T x){x}; void run(){apply from:'local'}}; apply from:'real.groovy'",
        "class Helper<T>{Closure apply={T x->x}; void run(){apply from:'local'}}; apply from:'real.groovy'"})
    void genericHelperMembersUseTheirErasure(String source){
        var result=reader.read("job.groovy",source);assertTrue(result.problems().isEmpty(),result.toString());assertEquals(1,result.directives().size());
    }
    @Test void genericHelperWithoutIncludesRemainsOrdinaryGroovy(){
        var result=reader.read("job.groovy","class Helper{def <T> apply(T x){x}; void run(){apply from:'local'}}");
        assertTrue(result.problems().isEmpty(),result.toString());assertTrue(result.directives().isEmpty());
    }
    @Test void genericStringBoundCannotHideMapFirstCall(){
        var result=reader.read("job.groovy","class Helper{def <T extends String> apply(T x){x}; void run(){apply from:'hidden.groovy'}}; apply from:'real.groovy'");
        assertTrue(result.problems().stream().anyMatch(p->p.code().equals("APPLY_CONTEXT")),result.toString());
    }
    @Test void localClassNamedMapIsNotTheJdkMapBound(){
        var result=reader.read("job.groovy","class Map{}; class Helper{def <T extends Map> apply(T x){x}; void run(){apply from:'hidden.groovy'}}; apply from:'real.groovy'");
        assertTrue(result.problems().stream().anyMatch(p->p.code().equals("APPLY_CONTEXT")),result.toString());
    }
    @ParameterizedTest @ValueSource(strings={
        "class Helper{void apply(SequencedMap x){}; void run(){apply from:'local'}}; apply from:'real.groovy'",
        "class Helper{def <T extends SequencedMap> apply(T x){x}; void run(){apply from:'local'}}; apply from:'real.groovy'"})
    void helperAcceptsTheActualJdkNamedArgumentMapSupertypes(String source){
        assertTrue(java.util.SequencedMap.class.isAssignableFrom(java.util.LinkedHashMap.class));
        var result=reader.read("job.groovy",source);assertTrue(result.problems().isEmpty(),result.toString());assertEquals(1,result.directives().size());
    }
    @Test void positionsUseUtf16AndUnreachableDirectivesRemainStatic(){
        String text="// 😀\r\nreturn\r\napply from: 'ä space.groovy'\r\n";
        var d=reader.read("job.groovy",text).directives().getFirst();
        assertEquals(text.indexOf("apply"),d.start());assertEquals("'ä space.groovy'",text.substring(d.literalStart(),d.literalEnd()));assertEquals(3,d.line());
    }
    @Test void parseDoesNotExecuteToplevelAstTestOrGrab() {
        String marker=temp.resolve("marker").toString().replace("\\","\\\\");
        String text="@groovy.transform.ASTTest(value={ new File('"+marker+"').text='ast' })\ndef x=1\nnew File('"+marker+"').text='body'\napply from:'a.groovy'\n";
        var result=reader.read("job.groovy",text);assertTrue(result.problems().isEmpty(),result.toString());assertFalse(Files.exists(temp.resolve("marker")));
        assertTrue(reader.read("grab.groovy","@Grab('invalid:never:0')\nimport java.util.List\napply from:'a.groovy'").problems().isEmpty());
    }
    @Test void invalidSourceIsReported(){assertEquals("APPLY_SYNTAX",reader.read("broken.groovy","apply(").problems().getFirst().code());}
}
