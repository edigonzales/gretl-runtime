package ch.so.agi.gretl.runtime.groovy.project;

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
        var cases=Map.ofEntries(Map.entry("include 'a', ':a'","PROJECT_INCLUDE_DUPLICATE"),Map.entry("include '../a'","PROJECT_INCLUDE_INVALID"),Map.entry("defaults 'a'","PROJECT_TARGET_INVALID"),Map.entry("include 'a'; include 'b'","PROJECT_SETTINGS_INVALID"),Map.entry("include []","PROJECT_SETTINGS_UNSUPPORTED"),Map.entry("include /a/","PROJECT_SETTINGS_INVALID"),Map.entry("include(/a/)","PROJECT_SETTINGS_UNSUPPORTED"),Map.entry("include '''a'''","PROJECT_SETTINGS_UNSUPPORTED"),Map.entry("import java.util.List\ninclude 'a'","PROJECT_SETTINGS_UNSUPPORTED"),Map.entry("def x='a'; include x","PROJECT_SETTINGS_UNSUPPORTED"),Map.entry("include \"${'a'}\"","PROJECT_SETTINGS_UNSUPPORTED"),Map.entry("label: include 'a'","PROJECT_SETTINGS_UNSUPPORTED"),Map.entry("return include('a')","PROJECT_SETTINGS_UNSUPPORTED"));
        for(var entry:cases.entrySet()){
            var runtime=assertThrows(ProjectException.class,()->new GretlProjectParser(Locale.ENGLISH).parseSettings(temp.resolve("settings.gretl"),entry.getKey()));assertEquals(entry.getValue(),runtime.code(),entry.getKey());
            var parsed=tooling(entry.getKey());assertFalse(parsed.complete());assertTrue(parsed.problems().stream().anyMatch(p->p.code().equals(runtime.code())),entry.getKey()+parsed.problems());
        }
    }
    @Test void bomHandlingIsComparedToTheCurrentRuntimeContract(){
        String text="\uFEFFinclude 'a'";var parsed=tooling(text);
        try{new GretlProjectParser(Locale.ENGLISH).parseSettings(temp.resolve("settings.gretl"),text);assertTrue(parsed.complete(),parsed.problems().toString());}
        catch(ProjectException runtime){assertFalse(parsed.complete(),"Runtime rejects the BOM; tooling must retain it and diagnose it");assertTrue(parsed.problems().stream().anyMatch(p->p.code().equals(runtime.code())));}
        assertEquals(text,parsed.source().text());
    }
    @Test void settingsNeverExecuteExpressionsOrInitializers() throws Exception {
        var marker=temp.resolve("must-not-exist");Files.writeString(marker,"positive file control");assertTrue(Files.exists(marker));Files.delete(marker);
        String path=marker.toString().replace("\\","\\\\").replace("'","\\'");String text="new File('"+path+"').text='executed'; class Init { static { new File('"+path+"').text='initializer' } }; include 'safe'";
        assertFalse(tooling(text).complete());assertThrows(ProjectException.class,()->new GretlProjectParser(Locale.ENGLISH).parseSettings(temp.resolve("settings.gretl"),text));assertFalse(Files.exists(marker));
    }
}
