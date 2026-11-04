package ch.so.agi.gretl.tooling.source;

import java.util.ArrayList;

/** Groovy 6 counts code points and resets parser lines only at LF; source/LSP also recognize bare CR. */
public final class ParserCoordinateMapper {
    private final int[] offsets;
    private final int[] parserLineStarts;
    public ParserCoordinateMapper(SourceText source) {
        String text=source.text();offsets=new int[text.codePointCount(0,text.length())+1];int cp=0;
        var lines=new ArrayList<Integer>();lines.add(0);
        for(int i=0;i<text.length();){
            int point=text.codePointAt(i);offsets[cp++]=i;i+=Character.charCount(point);
            if(point=='\n')lines.add(cp);
        }
        offsets[cp]=text.length();parserLineStarts=lines.stream().mapToInt(Integer::intValue).toArray();
    }
    public int lexerOffset(int codePointOffset) { return offsets[Math.max(0,Math.min(offsets.length-1,codePointOffset))]; }
    public int parserOffset(int oneBasedLine,int oneBasedColumn) {
        int line=Math.max(0,Math.min(parserLineStarts.length-1,oneBasedLine-1));
        int start=parserLineStarts[line],end=line+1<parserLineStarts.length?parserLineStarts[line+1]-1:offsets.length-1;
        return offsets[start+Math.max(0,Math.min(end-start,oneBasedColumn-1))];
    }
    public TextSpan parserSpan(int line,int column,int lastLine,int lastColumn) {
        int start=parserOffset(line,column),end=parserOffset(lastLine,lastColumn);return new TextSpan(start,Math.max(start,end));
    }
}
