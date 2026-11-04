package ch.so.agi.gretl.runtime.groovy.fragment;

import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import ch.so.agi.gretl.runtime.groovy.SourceLocation;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.util.*;

/** Scope-aware DFS over immutable sources; no task registration or script execution. */
public final class FragmentGraphResolver {
    public static final int MAX_DEPTH=64, MAX_SOURCES=1024;
    public static final long MAX_TOTAL_BYTES=64L*1024*1024;
    public record Entry(String scopeId,String sourceId) { public Entry { Objects.requireNonNull(scopeId);Objects.requireNonNull(sourceId); } }
    public record Edge(String scopeId,String sourceId,ApplyDirectiveReader.Directive directive,String targetId) { }
    public record Bundle(List<Entry> entries, Map<String,FragmentSource> sources, List<Edge> edges,
                         boolean projectMode, String settingsId, String sha256) {
        public Bundle {
            entries=List.copyOf(entries);sources=Collections.unmodifiableMap(new LinkedHashMap<>(sources));edges=List.copyOf(edges);
        }
        public boolean hasIncludes(){return !edges.isEmpty();}
        public List<Edge> edgesFrom(String scope,String source){return edges.stream().filter(e->e.scopeId().equals(scope)&&e.sourceId().equals(source)).toList();}
        public String virtualName(String source) {
            // Hex UTF-8 identity is injective, portable and Groovy-class-name safe.
            return ScriptSourceNames.virtualGroovyName(source);
        }
    }
    public Bundle resolve(List<Entry> entries, boolean projectMode, String settingsId, FragmentSourceAccess access) {
        Objects.requireNonNull(access);entries=List.copyOf(entries);
        if(entries.isEmpty())throw new IllegalArgumentException("Entry required");
        entries=entries.stream().map(e->new Entry(e.scopeId(),canonicalId(access,e.sourceId()))).toList();
        if(settingsId!=null)settingsId=canonicalId(access,settingsId);
        var state=new State(entries,projectMode,settingsId,access);
        if(settingsId!=null)state.read(settingsId,false);
        for(var entry:entries) state.visit(entry.scopeId(),entry.sourceId(),new ArrayList<>(),new LinkedHashMap<>());
        return new Bundle(entries,state.sources,state.edges,projectMode,settingsId,state.hash());
    }
    private static String canonicalId(FragmentSourceAccess access,String sourceId) {
        try{return access.canonicalId(sourceId);}
        catch(IOException failure){throw new GroovyDslException("APPLY_NOT_FOUND",
                ch.so.agi.gretl.i18n.Messages.format(ch.so.agi.gretl.i18n.Messages.sourceLocale(),"apply.APPLY_NOT_FOUND",sourceId),failure);}
    }
    private static final class State {
        final List<Entry> entries; final boolean project;final String settings;final FragmentSourceAccess access;
        final Map<String,FragmentSource> sources=new LinkedHashMap<>();final List<Edge> edges=new ArrayList<>();
        final Map<String,List<ApplyDirectiveReader.Directive>> parsed=new LinkedHashMap<>();
        final Map<String,Map<String,List<String>>> visited=new HashMap<>();long total;
        State(List<Entry> entries,boolean project,String settings,FragmentSourceAccess access){this.entries=entries;this.project=project;this.settings=settings;this.access=access;}
        FragmentSource read(String id,boolean fragment) {
            var existing=sources.get(id);if(existing!=null)return existing;
            if(sources.size()>=MAX_SOURCES)throw error("APPLY_LIMIT",id,List.of(),null);
            try {
                var source=access.read(id,fragment);
                if(!source.sourceId().equals(id))throw new IllegalArgumentException("Source identity mismatch");
                total+=source.bytes().length;
                if(fragment && total>MAX_TOTAL_BYTES)throw error("APPLY_LIMIT",id,List.of(),null);
                sources.put(id,source);return source;
            }catch(IOException failure){throw error("APPLY_NOT_FOUND",id,List.of(),failure);}
        }
        void visit(String scope,String id,List<String> stack,Map<String,Boolean> active) {
            var chain=new ArrayList<>(stack);chain.add(id);
            if(active.containsKey(id))throw error("APPLY_CYCLE",id,chain,null);
            var seen=visited.computeIfAbsent(scope,k->new LinkedHashMap<>());
            var previous=seen.putIfAbsent(id,List.copyOf(chain));
            if(previous!=null)throw error("APPLY_DUPLICATE",id,List.of(String.join(" -> ",previous),String.join(" -> ",chain)),null);
            if(stack.size()>=MAX_DEPTH)throw error("APPLY_LIMIT",id,chain,null);
            var source=read(id,!stack.isEmpty());
            var directives=parsed.computeIfAbsent(id,k->new ApplyDirectiveReader().read(id,source.text()).requireValid());
            active.put(id,true);
            for(var directive:directives) {
                try {
                    String target=access.resolve(id,directive.from());
                    if(!active.containsKey(target) && entries.stream().anyMatch(e->e.sourceId().equals(target)))throw error("APPLY_CONTEXT",target,chain,null);
                    edges.add(new Edge(scope,id,directive,target));visit(scope,target,chain,active);
                }catch(IOException failure) {
                    throw new GroovyDslException("APPLY_NOT_FOUND",ch.so.agi.gretl.i18n.Messages.format(ch.so.agi.gretl.i18n.Messages.sourceLocale(),"apply.APPLY_NOT_FOUND",id),null,null,
                            new SourceLocation(id,directive.line(),directive.column()),chain,failure);
                }catch(GroovyDslException failure) {
                    if(failure.source()!=null)throw failure;
                    throw new GroovyDslException(failure.code(),failure.getMessage()+" ("+String.join(" -> ",chain)+")",null,null,
                            new SourceLocation(id,directive.line(),directive.column()),failure.available(),failure);
                }
            }
            active.remove(id);
        }
        String hash() {
            try {
                var digest=MessageDigest.getInstance("SHA-256");
                if(edges.isEmpty()) {
                    if(!project)digest.update(sources.get(entries.getFirst().sourceId()).bytes());
                    else {
                        digest.update("gretl-project-source-v1\0".getBytes(StandardCharsets.UTF_8));
                        if(settings!=null)hashSource(digest,settings);
                        for(var entry:entries)hashSource(digest,entry.sourceId());
                    }
                } else {
                    frame(digest,"gretl-script-bundle-v1");frame(digest,project?"project":"single");
                    frame(digest,settings==null?"":settings);
                    frame(digest,Integer.toString(entries.size()));
                    for(var entry:entries){frame(digest,entry.scopeId());frame(digest,entry.sourceId());}
                    frame(digest,Integer.toString(sources.size()));
                    for(String id:sources.keySet().stream().sorted().toList())hashSource(digest,id);
                    frame(digest,Integer.toString(edges.size()));
                    for(var edge:edges){frame(digest,edge.scopeId());frame(digest,edge.sourceId());frame(digest,edge.directive().siteId());frame(digest,edge.targetId());}
                }
                return HexFormat.of().formatHex(digest.digest());
            }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        }
        void hashSource(MessageDigest digest,String id){frame(digest,id);frame(digest,sources.get(id).bytes());}
    }
    private static void frame(MessageDigest digest,String value){frame(digest,value.getBytes(StandardCharsets.UTF_8));}
    private static void frame(MessageDigest digest,byte[] bytes){digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());digest.update(bytes);}
    private static GroovyDslException error(String code,String id,List<String> chain,Throwable cause){
        return new GroovyDslException(code,ch.so.agi.gretl.i18n.Messages.format(ch.so.agi.gretl.i18n.Messages.sourceLocale(),"apply."+code,id)+(chain.isEmpty()?"":" ["+String.join("; ",chain)+"]"),null,null,null,chain,cause);
    }
}
