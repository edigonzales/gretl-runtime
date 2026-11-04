package ch.so.agi.gretl.job;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.groovy.fragment.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class BundleMetadataTest {
    @Test void validVersionedBundleRoundTripsWithoutAnySourceParsingOrLoading()throws Exception{
        var metadata=read(valid());assertEquals(3,metadata.schemaVersion());assertEquals(2,metadata.bundle().sources().size());assertFalse(metadata.bundle().projectMode());
        assertThrows(UnsupportedOperationException.class,()->metadata.bundle().sources().clear());
    }
    @Test void malformedBundleAndMixedMetadataAreRejectedBeforeClassInitialization(){
        for(var change:List.<Consumer<Map<String,Object>>>of(
                m->m.put("schemaVersion",4),m->m.put("schemaVersion",3.5),m->m.put("definitionMode","other"),
                m->m.put("javaFeatureVersion",25.5),m->m.put("entryClass","legacy"),m->m.put("sourceFiles",List.of("job.groovy")),
                m->m.put("resourceFiles",List.of("../escape")),m->m.put("defaultTargets",List.of("run")),
                m->bundle(m).put("version",2),m->bundle(m).put("settingsId","settings.gretl"),m->bundle(m).put("sha256","bad"),
                m->bundle(m).put("entries",List.of()),m->bundle(m).put("edges",List.of()),
                m->sources(m).add(sources(m).getFirst()),
                m->sources(m).getLast().put("sourceId","../escape.groovy"),
                m->sources(m).getLast().put("className","java.lang.System"),
                m->sources(m).getLast().put("virtualName","wrong.groovy"),m->sources(m).getLast().put("role","entry"),
                m->edges(m).getFirst().put("targetId","missing.groovy"),m->edges(m).getFirst().put("from","https://example/f.groovy"),
                m->edges(m).getFirst().put("line",0),m->edges(m).getFirst().put("start",0.5),
                m->bundle(m).put("entries",List.of(Map.of("scopeId",":a","sourceId","job.groovy")))
        )){var value=valid();change.accept(value);assertThrows(JobJarMain.JobJarException.class,()->read(value),value.toString());}
    }
    @SuppressWarnings("unchecked") private Map<String,Object> bundle(Map<String,Object> m){return (Map<String,Object>)m.get("bundle");}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> sources(Map<String,Object> m){return (List<Map<String,Object>>)bundle(m).get("sources");}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> edges(Map<String,Object> m){return (List<Map<String,Object>>)bundle(m).get("edges");}
    @SuppressWarnings("unchecked") private Map<String,Object> valid(){
        FragmentSourceAccess access=(id,fragment)->new FragmentSource(id,(id.equals("job.groovy")?"apply from:'tasks/f.groovy'":"action('run'){}").getBytes(StandardCharsets.UTF_8));
        var source=new FragmentGraphResolver().resolve(List.of(new FragmentGraphResolver.Entry("","job.groovy")),false,null,access);
        var types=new LinkedHashMap<String,String>();source.sources().keySet().forEach(id->types.put(id,source.virtualName(id).replace(".groovy","")));
        var m=new LinkedHashMap<String,Object>();m.put("schemaVersion",3);m.put("name","bundle");m.put("version","1");m.put("runtimeVersion","6.0.0-SNAPSHOT");m.put("javaFeatureVersion",25);m.put("definitionMode","single");m.put("bundle",CompiledScriptBundle.from(source,types).toMetadata());m.put("defaultTargets",List.of());m.put("resourceRoot",JobJarMain.RESOURCE_ROOT);m.put("resourceFiles",List.of());m.put("sourceFiles",List.of("job.groovy","tasks/f.groovy"));m.put("dependencyInventory","META-INF/gretl/dependencies.json");m.put("packagerVersion","test");
        return (Map<String,Object>)Json.parse(Json.write(m));
    }
    private JobJarMain.JobMetadata read(Map<String,Object> metadata)throws Exception{
        byte[] bytes=Json.write(metadata).getBytes(StandardCharsets.UTF_8);
        return JobJarMain.JobMetadata.load(new ClassLoader(null){@Override public InputStream getResourceAsStream(String name){return name.equals(JobJarMain.METADATA_RESOURCE)?new ByteArrayInputStream(bytes):null;}},Locale.ENGLISH);
    }
}
