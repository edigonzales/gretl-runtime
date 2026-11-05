package ch.so.agi.gretl.runtime.groovy.fragment;

import ch.so.agi.gretl.runtime.groovy.project.ProjectPath;
import java.nio.file.Path;
import java.util.*;

/** Versioned, validated bytecode registration metadata. Contains no source bodies or filesystem access. */
public record CompiledScriptBundle(boolean projectMode,String settingsId,String sha256,
        List<FragmentGraphResolver.Entry> entries,List<Source> sources,List<FragmentGraphResolver.Edge> edges) {
    public record Source(String sourceId,String className,String virtualName,String role) { }
    public CompiledScriptBundle {
        entries=List.copyOf(entries);sources=List.copyOf(sources);edges=List.copyOf(edges);
        if(sha256==null||!sha256.matches("[0-9a-f]{64}")||entries.isEmpty()||sources.size()>FragmentGraphResolver.MAX_SOURCES||edges.isEmpty())invalid();
        if(projectMode){if(!"settings.gretl".equals(settingsId))invalid();}else if(settingsId!=null||entries.size()!=1||!entries.getFirst().scopeId().isEmpty())invalid();
        var ids=new HashSet<String>();var classes=new HashSet<String>();var roots=new HashSet<String>();var scopes=new HashSet<String>();
        for(var entry:entries){path(entry.sourceId());if(!roots.add(entry.sourceId())||!scopes.add(entry.scopeId()))invalid();if(projectMode&&!ProjectPath.parseQualified(entry.scopeId()).canonical().equals(entry.scopeId()))invalid();}
        for(var source:sources){
            path(source.sourceId());
            if(!ids.add(source.sourceId())||!classes.add(source.className())||!source.virtualName().equals(ScriptSourceNames.virtualGroovyName(source.sourceId()))
                    ||!source.className().matches("(?:\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)*"+java.util.regex.Pattern.quote(source.virtualName().substring(0,source.virtualName().length()-7)))
                    ||!(roots.contains(source.sourceId())?"entry":"fragment").equals(source.role()))invalid();
        }
        if(!ids.containsAll(roots)||ids.contains(settingsId))invalid();
        var sites=new HashSet<String>();
        for(var edge:edges){
            var d=edge.directive();
            if(!scopes.contains(edge.scopeId())||!ids.contains(edge.sourceId())||!ids.contains(edge.targetId())||roots.contains(edge.targetId())
                    ||!edge.sourceId().equals(d.sourceId())||d.line()<1||d.column()<1||d.start()<0||d.end()<=d.start()
                    ||d.literalStart()<d.start()||d.literalEnd()>d.end()||d.literalEnd()<=d.literalStart()
                    ||!sites.add(edge.scopeId()+"\0"+d.siteId()))invalid();
            String target;
            try{target=FragmentSourceAccess.resolveLexically(edge.sourceId(),d.from(),Locale.ROOT);}
            catch(RuntimeException failure){throw new IllegalArgumentException("Invalid compiled include path",failure);}
            if(!target.equals(edge.targetId()))invalid();
        }
        var reachable=new HashSet<String>();var used=new HashSet<FragmentGraphResolver.Edge>();
        for(var entry:entries)visit(entry.scopeId(),entry.sourceId(),edges,new HashSet<>(),reachable,used,0);
        if(!reachable.equals(ids)||used.size()!=edges.size())invalid();
    }
    private static void visit(String scope,String source,List<FragmentGraphResolver.Edge> edges,Set<String> seen,Set<String> all,Set<FragmentGraphResolver.Edge> used,int depth){
        if(depth>=FragmentGraphResolver.MAX_DEPTH||!seen.add(source))invalid();all.add(source);
        int previous=-1;
        for(var edge:edges)if(edge.scopeId().equals(scope)&&edge.sourceId().equals(source)){
            if(edge.directive().start()<=previous)invalid();previous=edge.directive().start();used.add(edge);
            visit(scope,edge.targetId(),edges,seen,all,used,depth+1);
        }
    }
    public String virtualName(String id){return ScriptSourceNames.virtualGroovyName(id);}
    public List<FragmentGraphResolver.Edge> edgesFrom(String scope,String source){return edges.stream().filter(e->e.scopeId().equals(scope)&&e.sourceId().equals(source)).toList();}
    public static CompiledScriptBundle from(FragmentGraphResolver.Bundle bundle,Map<String,String> classes){
        Set<String> roots=new HashSet<>();bundle.entries().forEach(e->roots.add(e.sourceId()));
        return new CompiledScriptBundle(bundle.projectMode(),bundle.settingsId(),bundle.sha256(),bundle.entries(),
                bundle.sources().keySet().stream().filter(id->!id.equals(bundle.settingsId())).map(id->new Source(id,Objects.requireNonNull(classes.get(id)),bundle.virtualName(id),roots.contains(id)?"entry":"fragment")).toList(),bundle.edges());
    }
    public Map<String,Object> toMetadata(){
        var value=new LinkedHashMap<String,Object>();value.put("version",1);value.put("settingsId",settingsId);value.put("sha256",sha256);
        value.put("entries",entries.stream().map(e->Map.of("scopeId",e.scopeId(),"sourceId",e.sourceId())).toList());
        value.put("sources",sources.stream().map(s->Map.of("sourceId",s.sourceId(),"className",s.className(),"virtualName",s.virtualName(),"role",s.role())).toList());
        value.put("edges",edges.stream().map(e->{var d=e.directive();var m=new LinkedHashMap<String,Object>();m.put("scopeId",e.scopeId());m.put("sourceId",e.sourceId());m.put("targetId",e.targetId());m.put("from",d.from());m.put("start",d.start());m.put("end",d.end());m.put("literalStart",d.literalStart());m.put("literalEnd",d.literalEnd());m.put("line",d.line());m.put("column",d.column());return m;}).toList());return value;
    }
    public static CompiledScriptBundle parse(Object raw,boolean project){
        var m=object(raw,Set.of("version","settingsId","sha256","entries","sources","edges"));if(number(m,"version")!=1)invalid();
        String settings=m.get("settingsId")==null?null:string(m,"settingsId");
        var entries=new ArrayList<FragmentGraphResolver.Entry>();for(Object item:list(m,"entries")){var e=object(item,Set.of("scopeId","sourceId"));entries.add(new FragmentGraphResolver.Entry(string(e,"scopeId"),string(e,"sourceId")));}
        var sources=new ArrayList<Source>();for(Object item:list(m,"sources")){var s=object(item,Set.of("sourceId","className","virtualName","role"));sources.add(new Source(string(s,"sourceId"),string(s,"className"),string(s,"virtualName"),string(s,"role")));}
        var edges=new ArrayList<FragmentGraphResolver.Edge>();for(Object item:list(m,"edges")){
            var e=object(item,Set.of("scopeId","sourceId","targetId","from","start","end","literalStart","literalEnd","line","column"));String id=string(e,"sourceId");
            var d=new ApplyDirectiveReader.Directive(id,string(e,"from"),number(e,"start"),number(e,"end"),number(e,"literalStart"),number(e,"literalEnd"),number(e,"line"),number(e,"column"));
            edges.add(new FragmentGraphResolver.Edge(string(e,"scopeId"),id,d,string(e,"targetId")));
        }
        return new CompiledScriptBundle(project,settings,string(m,"sha256"),entries,sources,edges);
    }
    private static Map<?,?> object(Object value,Set<String> fields){if(!(value instanceof Map<?,?> m)||!m.keySet().equals(fields))throw new IllegalArgumentException("Invalid script bundle fields");return m;}
    private static List<?> list(Map<?,?> m,String key){if(!(m.get(key) instanceof List<?> l)||l.size()>FragmentGraphResolver.MAX_SOURCES*FragmentGraphResolver.MAX_SOURCES)throw new IllegalArgumentException("Invalid script bundle list");return l;}
    private static String string(Map<?,?> m,String key){if(!(m.get(key) instanceof String s))throw new IllegalArgumentException("Invalid script bundle string");return s;}
    private static int number(Map<?,?> m,String key){if(!(m.get(key) instanceof Number n)||n.doubleValue()!=n.intValue())throw new IllegalArgumentException("Invalid script bundle integer");return n.intValue();}
    public static void path(String id){
        if(id==null||id.isBlank()||id.contains("\\")||id.contains(":")||id.chars().anyMatch(Character::isISOControl))invalid();
        Path p=Path.of(id);if(p.isAbsolute()||p.startsWith("..")||!p.normalize().toString().replace('\\','/').equals(id)||Arrays.asList(id.split("/",-1)).contains(""))invalid();
        if(!id.endsWith(".groovy"))invalid();
    }
    private static void invalid(){throw new IllegalArgumentException("Invalid compiled script bundle");}
}
