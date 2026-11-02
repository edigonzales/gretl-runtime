package ch.so.agi.gretl.tooling.catalog;

import ch.so.agi.gretl.runtime.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;

class OperationCatalogExporterTest {
    @TempDir Path output;
    private final CatalogJsonCodec codec = new CatalogJsonCodec();
    private OperationCatalog actual() throws Exception {
        var loader = getClass().getClassLoader();
        return new OperationCatalogExporter().export(RuntimeCatalogBootstrap.collectOperations(loader),"test",new MetadataSupplementLoader().loadFromOwnedResources(loader));
    }
    @Test void fullSpiParityAndDeterminismWithoutHardcodedOperationCount() throws Exception {
        var providers = RuntimeCatalogBootstrap.collectOperations(getClass().getClassLoader());
        var catalog = actual();
        var expected = providers.values().stream().flatMap(Collection::stream).toList();
        assertFalse(expected.isEmpty());
        assertEquals(expected.size(),catalog.operations().size());
        for (var op : expected) {
            var data = catalog.operations().stream().filter(d -> d.operationId().equals(op.id())).findFirst().orElseThrow();
            assertEquals(op.symbol(),data.symbol()); assertEquals(op.description(),data.description());
            assertEquals(op.example(),data.example()); assertEquals(op.resultType()==Void.class,data.voidResult());
            assertEquals(op.inputs().stream().map(InputField::name).toList(),data.inputs().stream().map(InputDescriptor::name).toList());
            for (int i=0;i<op.inputs().size();i++) {
                var f=op.inputs().get(i); var d=data.inputs().get(i);
                assertEquals(f.required(),d.required()); assertEquals(f.allowedValues(),d.allowedValues());
                assertEquals(f.description(),d.description()); assertEquals(f.example(),d.example());
                assertEquals(f.defaultValue().isPresent(),d.hasDefault());
                assertEquals(f.defaultValue().map(OperationCatalogExporter::dataValue).orElse(null),d.defaultValue());
                assertEquals(f.valueType(),d.shape().runtimeType());
            }
            assertEquals(op.outputs().stream().map(f->new OutputDescriptor(f.name(),f.description(),ValueShape.fromMetadata(f.valueType()),f.nullable())).toList(),data.outputs());
        }
        assertArrayEquals(codec.encodeCanonical(catalog),codec.encodeCanonical(actual()));
        var reverse = new LinkedHashMap<String,List<OperationType<?,?>>>();
        providers.entrySet().stream().sorted(Map.Entry.<String,List<OperationType<?,?>>>comparingByKey().reversed()).forEach(e->reverse.put(e.getKey(),e.getValue().reversed()));
        assertEquals(catalog,new OperationCatalogExporter().export(reverse,"test",new MetadataSupplementLoader().loadFromOwnedResources(getClass().getClassLoader())));
        new OperationCatalogExporter().write(catalog,output);
        assertEquals(CatalogJsonCodec.State.AVAILABLE,codec.decode(Files.readAllBytes(output.resolve("operations.json")),catalog.catalogId()).state());
    }
    @Test void poisonCallbacksAndUnknownObjectAreNeverExecuted() {
        var field = new InputField<Object,Long>("large","integer","large number",false,Optional.of(Long.MAX_VALUE),List.of(),"large = 1",v->{throw new AssertionError("convert");},(b,v)->{throw new AssertionError("assign");});
        var op = new OperationType<Object,Object>("test.poison","Poison","description",Object.class,Object.class,
                ()->{throw new AssertionError("builder");},()->{throw new AssertionError("handler");},List.of(field),
                List.of(new OutputField<>("value","text","value",false,r->{throw new AssertionError("read");})),"example");
        var c = new OperationCatalogExporter().export(Map.of("trusted",List.of(op)),"test",List.of());
        assertEquals("9223372036854775807",c.operations().getFirst().inputs().getFirst().defaultValue().scalar());
        assertThrows(IllegalArgumentException.class,()->OperationCatalogExporter.dataValue(new Object(){@Override public String toString(){throw new AssertionError("toString");}}));
        assertEquals(c,codec.decode(codec.encodeCanonical(c),c.catalogId()).catalog().orElseThrow());
    }
    @Test void schemaDigestCollisionAndUnavailableFailClosed() throws Exception {
        var c=actual(); byte[] bytes=codec.encodeCanonical(c);
        assertEquals(CatalogJsonCodec.State.UNAVAILABLE,codec.decode(null,null).state());
        assertEquals(CatalogJsonCodec.State.INCOMPATIBLE,codec.decode(new byte[0],null).state());
        assertEquals(CatalogJsonCodec.State.INCOMPATIBLE,codec.decode(bytes,"0".repeat(64)).state());
        String json=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        for (String broken : List.of(json.replaceFirst("schemaVersion\":1","schemaVersion\":2"),json.replaceFirst("test","changed"),json.replaceFirst("\\{","{\"extra\":true,"),json.replaceFirst("\\{","{\"schemaVersion\":1,")))
            assertEquals(CatalogJsonCodec.State.INCOMPATIBLE,codec.decode(broken.getBytes(java.nio.charset.StandardCharsets.UTF_8),null).state());
        var duplicate=new ArrayList<>(c.operations()); duplicate.add(c.operations().getFirst());
        var bad=codec.identify(new OperationCatalog(1,"test","1","",c.producers(),duplicate,"COMPLETE"));
        assertEquals(CatalogJsonCodec.State.INCOMPATIBLE,codec.decode(codec.encodeCanonical(bad),null).state());
        var repo=new CatalogRepository(); repo.loadBundled(bytes,c.catalogId());
        assertThrows(IllegalArgumentException.class,()->repo.mergeExplicitDescriptors(List.of(c.operations().getFirst())));
        assertEquals(1,repo.snapshot().generation());
    }
    @Test void supplementsCannotOverrideOrInventSpiFacts() throws Exception {
        var loader=new MetadataSupplementLoader();
        var providers=RuntimeCatalogBootstrap.collectOperations(getClass().getClassLoader());
        var operations=providers.values().stream().flatMap(Collection::stream).toList();
        var op=operations.getFirst(); var f=op.inputs().getFirst();
        var supplement=new MetadataSupplementLoader.Supplement(op.id(),f.name(),null,null,true,Map.of());
        assertEquals(1,loader.validateAgainstOperations(List.of(supplement),operations).size());
        assertThrows(IllegalArgumentException.class,()->loader.validateAgainstOperations(List.of(supplement,supplement),operations));
        assertThrows(IllegalArgumentException.class,()->loader.validateAgainstOperations(List.of(new MetadataSupplementLoader.Supplement(op.id(),"missing",null,null,false,Map.of())),operations));
        assertThrows(java.io.IOException.class,()->loader.read(new java.io.ByteArrayInputStream("{\"schemaVersion\":1,\"fields\":[],\"required\":true}".getBytes())));
        assertThrows(java.io.IOException.class,()->loader.read(new java.io.ByteArrayInputStream("{\"schemaVersion\":2,\"fields\":[]}".getBytes())));
    }
}
