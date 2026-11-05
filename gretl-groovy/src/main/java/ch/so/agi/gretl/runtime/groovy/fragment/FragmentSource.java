package ch.so.agi.gretl.runtime.groovy.fragment;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.util.Objects;

/** Immutable bytes used for parsing, compilation and source identity. */
public final class FragmentSource {
    private final String sourceId;
    private final byte[] bytes;
    private final String text;
    public FragmentSource(String sourceId, byte[] bytes) {
        this.sourceId=Objects.requireNonNull(sourceId);this.bytes=bytes.clone();
        try { text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(this.bytes)).toString(); }
        catch (CharacterCodingException e) { throw new IllegalArgumentException("Invalid UTF-8: " + sourceId,e); }
    }
    public String sourceId() { return sourceId; }
    public byte[] bytes() { return bytes.clone(); }
    public String text() { return text; }
}
