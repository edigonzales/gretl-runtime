package ch.so.agi.gretl.tooling.source;

/** Half-open offsets in the unchanged Java/UTF-16 source string. */
public record TextSpan(int startUtf16, int endUtf16) {
    public TextSpan { if (startUtf16 < 0 || endUtf16 < startUtf16) throw new IllegalArgumentException("Invalid source span"); }
    public int length() { return endUtf16 - startUtf16; }
    public boolean contains(int offset) { return offset >= startUtf16 && offset < endUtf16; }
    public boolean contains(TextSpan other) { return other.startUtf16 >= startUtf16 && other.endUtf16 <= endUtf16; }
    public boolean intersects(TextSpan other) { return startUtf16 < other.endUtf16 && other.startUtf16 < endUtf16; }
}
