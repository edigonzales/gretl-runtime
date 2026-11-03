package ch.so.agi.gretl.tooling.source;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import ch.so.agi.gretl.tooling.catalog.CatalogJsonCodec;

/** Immutable original text. No path lookup, EOL normalization or IO. */
public final class SourceText {
    public record SourceStamp(URI documentId, String contentSha256, Integer bufferVersion, String origin, boolean editable,String diskIdentity) {
        public SourceStamp(URI documentId,String contentSha256,Integer bufferVersion,String origin,boolean editable){this(documentId,contentSha256,bufferVersion,origin,editable,null);}
        public SourceStamp { Objects.requireNonNull(documentId); Objects.requireNonNull(contentSha256); Objects.requireNonNull(origin); }
    }
    private final String text;
    private final SourceStamp stamp;
    private final LineIndex lines;
    public SourceText(URI id, String text, Integer version, String origin, boolean editable) {
        this(id,text,version,origin,editable,null);
    }
    public SourceText(URI id,String text,Integer version,String origin,boolean editable,String diskIdentity){
        this.text=Objects.requireNonNull(text);this.lines=new LineIndex(text);
        // Hash original UTF-16 code units, including a temporarily unpaired surrogate in an editor buffer.
        ByteBuffer bytes=ByteBuffer.allocate(text.length()*2);for(int i=0;i<text.length();i++)bytes.putChar(text.charAt(i));
        this.stamp=new SourceStamp(id,CatalogJsonCodec.hash(bytes.array()),version,origin,editable,diskIdentity);
    }
    public static SourceText job(String text) { return new SourceText(URI.create("memory:/job.groovy"),text,1,"BUFFER",true); }
    public String text() { return text; }
    public SourceStamp stamp() { return stamp; }
    public LineIndex lines() { return lines; }
    public boolean hasBom() { return text.startsWith("\uFEFF"); }
    public String preferredEol() { int lf=text.indexOf('\n'),cr=text.indexOf('\r');if(cr>=0&&(lf<0||cr<lf))return cr+1==lf?"\r\n":"\r";return "\n"; }
    public String slice(TextSpan span) { return text.substring(span.startUtf16(),span.endUtf16()); }
}
