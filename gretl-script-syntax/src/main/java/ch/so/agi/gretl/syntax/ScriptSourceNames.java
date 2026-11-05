package ch.so.agi.gretl.syntax;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Injective names shared by syntax conversion and later bundle compilation. */
public final class ScriptSourceNames {
    private ScriptSourceNames() { }
    public static String virtualGroovyName(String sourceId) {
        return "GretlSource_"+HexFormat.of().formatHex(sourceId.getBytes(StandardCharsets.UTF_8))+".groovy";
    }
}
