package ch.so.agi.gretl.runtime.groovy.fragment;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Injective names shared by syntax conversion and later bundle compilation. */
final class ScriptSourceNames {
    private ScriptSourceNames() { }
    static String virtualGroovyName(String sourceId) {
        return "GretlSource_"+HexFormat.of().formatHex(sourceId.getBytes(StandardCharsets.UTF_8))+".groovy";
    }
}
