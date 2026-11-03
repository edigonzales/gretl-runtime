package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.source.*;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

/** Immutable buffer overlays. No filesystem access or analysis occurs on notifications. */
public final class DocumentStore {
    public record OpenDocument(String languageId,long incarnation,SourceText source) { }
    public record BufferCapture(long revision,Map<URI,OpenDocument> documents) {
        public BufferCapture { documents=Map.copyOf(documents); }
    }
    public record TextChange(TextPosition start,TextPosition end,String text) {
        public TextChange { Objects.requireNonNull(text);if((start==null)!=(end==null))throw new IllegalArgumentException("Incomplete change range"); }
        public static TextChange full(String text){return new TextChange(null,null,text);}
    }
    private final Map<URI,OpenDocument> open=new LinkedHashMap<>();
    private final int maximumDocuments,maximumUtf16Length,maximumTotalUtf16;
    private long revision;
    private volatile Consumer<URI> changed=id->{};
    public DocumentStore(){this(64,1_048_576);}
    public DocumentStore(int maximumDocuments,int maximumUtf16Length){
        this(maximumDocuments,maximumUtf16Length,(int)Math.min(8_388_608L,(long)maximumDocuments*maximumUtf16Length));
    }
    public DocumentStore(int maximumDocuments,int maximumUtf16Length,int maximumTotalUtf16){
        if(maximumDocuments<1||maximumDocuments>512||maximumUtf16Length<1||maximumUtf16Length>4_194_304||maximumTotalUtf16<maximumUtf16Length||maximumTotalUtf16>33_554_432)throw new IllegalArgumentException("Invalid buffer budget");
        this.maximumDocuments=maximumDocuments;this.maximumUtf16Length=maximumUtf16Length;this.maximumTotalUtf16=maximumTotalUtf16;
    }
    void onChange(Consumer<URI> listener){changed=Objects.requireNonNull(listener);}
    public void open(URI id,String languageId,int version,String text){
        id=identity(id);checkSize(text);
        synchronized(this){
            if(open.containsKey(id))throw new IllegalArgumentException("DOCUMENT_ALREADY_OPEN");
            if(open.size()>=maximumDocuments)throw new IllegalStateException("DOCUMENT_LIMIT");
            if(totalLength()+text.length()>maximumTotalUtf16)throw new IllegalStateException("BUFFER_TOTAL_LIMIT");
            open.put(id,new OpenDocument(Objects.requireNonNull(languageId),++revision,new SourceText(id,text,version,"BUFFER",true)));
        }
        changed.accept(id);
    }
    /** All changes apply in order to one local value; failure leaves text/version/incarnation untouched. */
    public void applyChanges(URI id,int version,List<TextChange> changes){
        id=identity(id);Objects.requireNonNull(changes);
        synchronized(this){
            var previous=Objects.requireNonNull(open.get(id),"DOCUMENT_NOT_OPEN");
            if(version<=previous.source().stamp().bufferVersion())throw new IllegalArgumentException("STALE_DOCUMENT_VERSION");
            String text=previous.source().text();
            for(var change:changes){
                if(change.start()==null)text=change.text();
                else{
                    validPosition(change.start());validPosition(change.end());var lines=new LineIndex(text);
                    int start=lines.offsetAt(change.start()),end=lines.offsetAt(change.end());if(end<start)throw new IllegalArgumentException("Reversed change range");
                    long size=(long)text.length()-(end-start)+change.text().length();if(size>maximumUtf16Length)throw new IllegalArgumentException("DOCUMENT_SIZE_LIMIT");
                    text=text.substring(0,start)+change.text()+text.substring(end);
                }
                checkSize(text);
            }
            if(totalLength()-previous.source().text().length()+text.length()>maximumTotalUtf16)throw new IllegalStateException("BUFFER_TOTAL_LIMIT");
            var next=new SourceText(id,text,version,"BUFFER",true);open.put(id,new OpenDocument(previous.languageId(),previous.incarnation(),next));revision++;
        }
        changed.accept(id);
    }
    public void close(URI id){
        id=identity(id);boolean removed;
        synchronized(this){removed=open.remove(id)!=null;if(removed)revision++;}
        if(removed)changed.accept(id);
    }
    public synchronized Optional<OpenDocument> get(URI id){return Optional.ofNullable(open.get(identity(id)));}
    public synchronized BufferCapture capture(){return new BufferCapture(revision,open);}
    public synchronized int size(){return open.size();}
    /** Used at the publication point; locks only buffer mutation, never parsing or disk IO. */
    synchronized boolean ifUnchanged(Map<URI,OpenDocument> expected,java.util.function.Predicate<URI> relevant,Runnable publish){
        var actual=new LinkedHashMap<URI,OpenDocument>();open.forEach((id,value)->{if(relevant.test(id))actual.put(id,value);});
        if(!actual.equals(expected))return false;publish.run();return true;
    }
    private void checkSize(String text){Objects.requireNonNull(text);if(text.length()>maximumUtf16Length)throw new IllegalArgumentException("DOCUMENT_SIZE_LIMIT");}
    private long totalLength(){long total=0;for(var document:open.values())total+=document.source().text().length();return total;}
    private static void validPosition(TextPosition p){if(p.line()<0||p.character()<0)throw new IllegalArgumentException("Negative change position");}
    /** Unlike some Path.toUri implementations this performs no directory/symlink stat. */
    public static URI fileUri(java.nio.file.Path path){
        String value=path.toAbsolutePath().normalize().toString().replace(java.io.File.separatorChar,'/');if(!value.startsWith("/"))value="/"+value;
        try{return new URI("file",null,value,null);}catch(java.net.URISyntaxException invalid){throw new IllegalArgumentException("Invalid source path",invalid);}
    }
    public static URI identity(URI id){
        Objects.requireNonNull(id);if(!id.isAbsolute()||id.getFragment()!=null)throw new IllegalArgumentException("Absolute document URI without fragment required");
        if("file".equalsIgnoreCase(id.getScheme())){
            if(id.getAuthority()!=null||id.getQuery()!=null||id.getPath()==null)throw new IllegalArgumentException("Local file URI required");
            try{return new URI("file",null,id.getPath(),null).normalize();}catch(java.net.URISyntaxException invalid){throw new IllegalArgumentException("Invalid file URI",invalid);}
        }
        return id.normalize();
    }
}
