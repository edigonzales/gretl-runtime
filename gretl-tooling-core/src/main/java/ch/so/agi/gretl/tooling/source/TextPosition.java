package ch.so.agi.gretl.tooling.source;

/** Zero-based UTF-16 line/character position. */
public record TextPosition(int line, int character) {
    public TextPosition { if (line < 0 || character < 0) throw new IllegalArgumentException("Negative text position"); }
}
