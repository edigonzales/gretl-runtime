package ch.so.agi.gretl.runtime.groovy.project;

import ch.so.agi.gretl.runtime.groovy.SourceLocation;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.syntax.SettingsDocumentParser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StaticSettingsParityTest {
    @TempDir Path temp;
    private SettingsDocumentParser.SettingsDocument tooling(String text){return new SettingsDocumentParser().parse(new SourceText(temp.resolve("settings.gretl").toUri(),text,3,"BUFFER",true),CancellationToken.NONE);}
    @Test void declarativeSettingsAndOriginalSpansMatchTheActualRuntime(){
        for(String text:List.of("", "// 😀\r\ninclude 'source', ':checks:local'\r\ndefaults ':checks:local:run'", "include (('source')), (\"checks:local\"); defaults(':source:run')", "#!/usr/bin/env groovy\ninclude 'source'")){
            var runtime=new GretlProjectParser(Locale.ENGLISH).parseSettings(temp.resolve("settings.gretl"),text);var parsed=tooling(text);
            assertTrue(parsed.complete(),text+parsed.problems());assertEquals(runtime.included().stream().map(ProjectPath::canonical).toList(),parsed.includes().stream().map(SettingsDocumentParser.SettingName::canonical).toList());
            assertEquals(runtime.defaultTargets(),parsed.defaults().stream().map(SettingsDocumentParser.SettingName::canonical).toList());
            for(var name:parsed.includes()){var envelope=parsed.source().slice(name.literal().token());assertTrue(envelope.startsWith("'")||envelope.startsWith("\""));assertEquals(name.value().length()+1,name.literal().decodedToSource().size());}
        }
    }
    @Test void invalidDirectivesAgreeWithRuntimeRatherThanInventingASettingsDialect(){
        var cases=Map.ofEntries(
                Map.entry("include 'a', ':a'", "PROJECT_INCLUDE_DUPLICATE"),
                Map.entry("defaults ':a:run', ':a:run'", "DUPLICATE_DEFAULT_TARGET"),
                Map.entry("include '../a'", "PROJECT_INCLUDE_INVALID"),
                Map.entry("defaults 'a'", "PROJECT_TARGET_INVALID"),
                Map.entry("include 'a'; include 'b'", "PROJECT_SETTINGS_INVALID"),
                Map.entry("defaults ':a'; defaults ':b'", "PROJECT_SETTINGS_INVALID"),
                Map.entry("include()", "PROJECT_INCLUDE_INVALID"),
                Map.entry("defaults()", "PROJECT_TARGET_INVALID"),
                Map.entry("include []", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("include /a/", "PROJECT_SETTINGS_INVALID"),
                Map.entry("include((/a/))", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("include(($/a/$))", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("include '''a'''", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("include \"\"\"a\"\"\"", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("include \"${'a'}\"", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("include(('a' + 'b'))", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("this.include('a')", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("this?.include('a')", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("this*.include('a')", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("this.<String>include('a')", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("this.\"${'include'}\"('a')", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("import java.util.List\ninclude 'a'", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("import module java.base\ninclude 'a'", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("package projects\ninclude 'a'", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("def helper() { 1 }; include 'a'", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("class Hidden { static {} }; include 'a'", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("@groovy.transform.Field String value='a'\ninclude 'a'", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("def x='a'; include x", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("label: include 'a'", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("return include('a')", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("if (true) include 'a'", "PROJECT_SETTINGS_UNSUPPORTED"),
                Map.entry("for (x in [1]) { include 'a' }", "PROJECT_SETTINGS_UNSUPPORTED"));
        for(var entry:cases.entrySet()){
            var runtime=assertThrows(ProjectException.class,()->new GretlProjectParser(Locale.ENGLISH).parseSettings(temp.resolve("settings.gretl"),entry.getKey()), entry.getKey());assertEquals(entry.getValue(),runtime.code(),entry.getKey());
            var parsed=tooling(entry.getKey());assertFalse(parsed.complete());assertTrue(parsed.problems().stream().anyMatch(p->p.code().equals(runtime.code())),entry.getKey()+parsed.problems());
        }
    }
    @Test void literalTokensKeepOriginalUtf16OffsetsAndRuntimeAstPositions() {
        String first = "'\\u0061\\\r\nb'";
        String second = "\"b:c\"";
        String target = "':ab:run'";
        String text = "/* 😀 */ include((\r\n /* 🚀 */ " + first + "\r\n), " + second
                + ");\r/* 😀 */ defaults(" + target + ", ':b:c:run')";
        var runtime = new GretlProjectParser(Locale.ENGLISH).parseSettings(temp.resolve("settings.gretl"), text);
        var parsed = tooling(text);
        assertTrue(parsed.complete(), parsed.problems().toString());
        assertEquals(List.of(":ab", ":b:c"), runtime.included().stream().map(ProjectPath::canonical).toList());
        assertEquals(List.of(":ab", ":b:c"), parsed.includes().stream().map(SettingsDocumentParser.SettingName::canonical).toList());
        assertEquals(List.of(":ab:run", ":b:c:run"), parsed.defaults().stream().map(SettingsDocumentParser.SettingName::canonical).toList());
        assertEquals(runtime.defaultTargets(), parsed.defaults().stream().map(SettingsDocumentParser.SettingName::canonical).toList());
        assertEquals(1, runtime.includeSources().get(ProjectPath.parseInclude("ab")).line());
        assertEquals(17, runtime.includeSources().get(ProjectPath.parseInclude("ab")).column());
        assertRuntimePosition(text, target, runtime.defaultSources().get(":ab:run"));
        assertToken(text, first, parsed.includes().getFirst());
        assertToken(text, second, parsed.includes().getLast());
        assertToken(text, target, parsed.defaults().getFirst());
        assertEquals(List.of(text.indexOf(first) + 1, text.indexOf(first) + first.length() - 2,
                text.indexOf(first) + first.length() - 1), parsed.includes().getFirst().literal().decodedToSource());
    }

    @Test void duplicateIncludesRetainBothOriginalPositions() {
        String text = "/* 😀 */ include('a',\r\n /* 🚀 */ (':a'))";
        var runtime = assertThrows(ProjectException.class,
                () -> new GretlProjectParser(Locale.ENGLISH).parseSettings(temp.resolve("settings.gretl"), text));
        assertEquals("PROJECT_INCLUDE_DUPLICATE", runtime.code());
        assertRuntimePosition(text, "(':a'", runtime.source());
        assertTrue(runtime.getMessage().contains("first included as a at " + temp.resolve("settings.gretl") + ":1"), runtime.getMessage());

        var parsed = tooling(text);
        assertFalse(parsed.complete());
        var duplicate = parsed.problems().getFirst();
        assertEquals("PROJECT_INCLUDE_DUPLICATE", duplicate.code());
        assertEquals("':a'", parsed.source().slice(duplicate.span()));
        assertEquals("'a'", parsed.source().slice(duplicate.related().getFirst()));
        assertEquals(text.indexOf("':a'"), duplicate.span().startUtf16());
        assertEquals(text.indexOf("'a'"), duplicate.related().getFirst().startUtf16());
    }

    @Test void bareCarriageReturnKeepsEachHostsLineConvention() {
        String text = "include 'a'\rinclude('b')";
        var runtime = assertThrows(ProjectException.class,
                () -> new GretlProjectParser(Locale.ENGLISH).parseSettings(temp.resolve("settings.gretl"), text));
        assertEquals("PROJECT_SETTINGS_INVALID", runtime.code());
        assertEquals(1, runtime.source().line());
        assertEquals(13, runtime.source().column());

        var parsed = tooling(text);
        assertFalse(parsed.complete());
        var duplicate = parsed.problems().getFirst();
        assertEquals("PROJECT_SETTINGS_INVALID", duplicate.code());
        assertEquals(12, duplicate.span().startUtf16());
        var position = parsed.source().lines().positionAt(duplicate.span().startUtf16());
        assertEquals(1, position.line());
        assertEquals(0, position.character());
    }

    @Test void hostErrorOrderAndRecoveryRemainDistinct() {
        var parser = new GretlProjectParser(Locale.ENGLISH);
        Path file = temp.resolve("settings.gretl");
        var invalidName = assertThrows(ProjectException.class,
                () -> parser.parseSettings(file, "include('..', variable); if (true) defaults(':run')"));
        assertEquals("PROJECT_INCLUDE_INVALID", invalidName.code());
        var repeated = assertThrows(ProjectException.class,
                () -> parser.parseSettings(file, "include 'a'; include variable"));
        assertEquals("PROJECT_SETTINGS_INVALID", repeated.code());
        var partial = tooling("include variable; include 'a'; defaults ':run'");
        assertFalse(partial.complete());
        assertEquals(List.of(":a"), partial.includes().stream().map(SettingsDocumentParser.SettingName::canonical).toList());
        assertEquals(List.of(":run"), partial.defaults().stream().map(SettingsDocumentParser.SettingName::canonical).toList());
        assertEquals(List.of("PROJECT_SETTINGS_UNSUPPORTED"), partial.problems().stream().map(SettingsDocumentParser.SettingsProblem::code).toList());

        String incomplete = "include 'a'\ndefaults(";
        assertEquals("PROJECT_SETTINGS_INVALID", assertThrows(ProjectException.class,
                () -> parser.parseSettings(file, incomplete)).code());
        var recovered = tooling(incomplete);
        assertFalse(recovered.complete());
        assertEquals(List.of(":a"), recovered.includes().stream().map(SettingsDocumentParser.SettingName::canonical).toList());
        assertTrue(recovered.problems().stream().anyMatch(p -> p.code().equals("PROJECT_SETTINGS_INVALID")));
    }

    @Test void settingsBudgetsRemainSpecificToEachHost() {
        String oversized = "//" + "ä".repeat(131_072);
        var parser = new GretlProjectParser(Locale.ENGLISH);
        Path file = temp.resolve("settings.gretl");
        assertEquals("PROJECT_SETTINGS_INVALID", assertThrows(ProjectException.class,
                () -> parser.parseSettings(file, oversized)).code());
        assertSettingsLimit(oversized);

        String manyArguments = "include(" + String.join(", ", Collections.nCopies(300, "'a'")) + ")";
        assertSettingsLimit(manyArguments);
        assertEquals("PROJECT_INCLUDE_DUPLICATE", assertThrows(ProjectException.class,
                () -> parser.parseSettings(file, manyArguments)).code());
    }

    private void assertSettingsLimit(String text) {
        var parsed = tooling(text);
        assertFalse(parsed.complete());
        assertEquals("PROJECT_SETTINGS_LIMIT", parsed.problems().getFirst().code());
    }

    private static void assertToken(String text, String token, SettingsDocumentParser.SettingName name) {
        var span = name.literal().token();
        assertEquals(text.indexOf(token), span.startUtf16());
        assertEquals(token, text.substring(span.startUtf16(), span.endUtf16()));
    }

    private static void assertRuntimePosition(String text, String origin,
                                             SourceLocation source) {
        String prefix = text.substring(0, text.indexOf(origin));
        assertEquals(1 + prefix.chars().filter(c -> c == '\n').count(), source.line());
        assertEquals(1 + prefix.codePointCount(prefix.lastIndexOf('\n') + 1, prefix.length()), source.column());
    }

    @Test void bomHandlingIsComparedToTheCurrentRuntimeContract(){
        String text="\uFEFFinclude(('a'))";var parsed=tooling(text);
        try{new GretlProjectParser(Locale.ENGLISH).parseSettings(temp.resolve("settings.gretl"),text);assertTrue(parsed.complete(),parsed.problems().toString());}
        catch(ProjectException runtime){assertFalse(parsed.complete(),"Runtime rejects the BOM; tooling must retain it and diagnose it");assertTrue(parsed.problems().stream().anyMatch(p->p.code().equals(runtime.code())));}
        assertEquals(text,parsed.source().text());
        assertToken(text, "'a'", parsed.includes().getFirst());
    }
    @Test void settingsNeverExecuteExpressionsOrInitializers() throws Exception {
        var marker=temp.resolve("must-not-exist");Files.writeString(marker,"positive file control");assertTrue(Files.exists(marker));Files.delete(marker);
        String path=marker.toString().replace("\\","\\\\").replace("'","\\'");String text="new File('"+path+"').text='executed'; class Init { static { new File('"+path+"').text='initializer' } }; include 'safe'";
        assertFalse(tooling(text).complete());assertThrows(ProjectException.class,()->new GretlProjectParser(Locale.ENGLISH).parseSettings(temp.resolve("settings.gretl"),text));assertFalse(Files.exists(marker));
    }
}
