package ch.so.agi.gretl.tooling.workspace;

import java.net.URI;
import java.nio.file.Path;

/** Logical URI/path operations only; never stats or resolves real paths. */
public final class SourceUris {
    private SourceUris(){}
    public static URI directoryIdentity(URI id){id=DocumentStore.identity(id);String value=id.getPath();if(value!=null&&value.length()>1&&value.endsWith("/"))return path(id,value.substring(0,value.length()-1));return id;}
    public static URI parent(URI id){
        String path=id.getPath();if(path==null)return null;int end=path.endsWith("/")?path.length()-1:path.length();int slash=path.lastIndexOf('/',end-1);if(slash<0)return null;return path(id,path.substring(0,slash+1));
    }
    public static URI child(URI directory,String relative){String base=directory.getPath();if(base==null)throw new IllegalArgumentException("No source directory");return path(directory,(base.endsWith("/")?base:base+"/")+relative);}
    public static URI jobResource(URI source,String literal){return resource(parent(source),literal);}
    public static URI resource(URI directory,String literal){
        if(directory==null)return null;
        if("file".equalsIgnoreCase(directory.getScheme()))return DocumentStore.fileUri(Path.of(directory).resolve(literal).normalize());
        return literal.startsWith("/")?path(directory,literal):child(directory,literal);
    }
    public static boolean contains(URI root,URI id){return new SourceRepository.ReadPolicy(root,1,false).permits(id);}
    private static URI path(URI base,String path){try{return DocumentStore.identity(new URI(base.getScheme(),base.getAuthority(),path,null,null));}catch(java.net.URISyntaxException invalid){throw new IllegalArgumentException("Invalid source URI",invalid);}}
}
