package ch.so.agi.gretl.tooling.source;

import java.util.*;

/** CRLF is one line break; tabs and supplementary characters keep original UTF-16 width. */
public final class LineIndex {
    private final String text;
    private final int[] starts, ends;
    public LineIndex(String text) {
        this.text = Objects.requireNonNull(text);
        var startList = new ArrayList<Integer>(); var endList = new ArrayList<Integer>(); startList.add(0);
        for (int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if (c=='\r' || c=='\n') {
                endList.add(i);
                if (c=='\r' && i+1<text.length() && text.charAt(i+1)=='\n') i++;
                startList.add(i+1);
            }
        }
        endList.add(text.length()); starts=startList.stream().mapToInt(Integer::intValue).toArray(); ends=endList.stream().mapToInt(Integer::intValue).toArray();
    }
    public int lineCount() { return starts.length; }
    public int offsetAt(TextPosition p) {
        if (p.line() >= starts.length || p.character() > ends[p.line()] - starts[p.line()]) throw new IllegalArgumentException("Position outside line content");
        return starts[p.line()] + p.character();
    }
    public TextPosition positionAt(int offset) {
        if (offset < 0 || offset > text.length()) throw new IllegalArgumentException("Offset outside source");
        int line=Arrays.binarySearch(starts,offset); if(line<0)line=-line-2;
        // Offsets inside CRLF share the preceding line-end position, which is the only LSP-representable boundary.
        return new TextPosition(line,Math.min(offset,ends[line])-starts[line]);
    }
    public String lineText(int line) { return text.substring(starts[line],ends[line]); }
    public TextSpan contentSpan(int line) { return new TextSpan(starts[line],ends[line]); }
}
