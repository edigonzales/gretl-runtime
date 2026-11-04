package ch.so.agi.gretl.lsp;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HostBoundaryTest {
    @Test void customRequestSchemaPropertiesMatchTheActualJavaWireRecords()throws Exception {
        try(var input=getClass().getResourceAsStream("/gretl-protocol-v1.schema.json")) {
            assertNotNull(input);var definitions=JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("$defs");
            for(var type:List.of(GretlProtocol.CatalogRequest.class,GretlProtocol.GraphRequest.class,GretlProtocol.EditRequest.class,GretlProtocol.ValidationRequest.class,GretlProtocol.NavigationRequest.class,GretlProtocol.Intent.class,GretlProtocol.Value.class,GretlProtocol.Parameter.class,GretlProtocol.SymbolKey.class)) {
                var fields=Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
                assertEquals(fields,definitions.getAsJsonObject(type.getSimpleName()).getAsJsonObject("properties").keySet(),type.getName());
            }
        }
    }
    @Test void configurationIsAnAtomicTypedAllowlist() {
        var old=new ServerConfiguration("de",true);
        for(String input:List.of("[]","{\"language\":\"fr\"}","{\"language\":\"en\",\"trusted\":\"true\"}","{\"javaPath\":\"workspace/evil\"}","{\"trusted\":null}"))assertThrows(IllegalArgumentException.class,()->ServerConfiguration.decode(JsonParser.parseString(input),old),input);
        assertEquals(new ServerConfiguration("de",false),ServerConfiguration.decode(JsonParser.parseString("{\"trusted\":false}"),old));assertEquals("de",old.language());assertTrue(old.trusted());
    }
    @Test void framingRejectsLargeDuplicateAndMalformedMessagesBeforePayloadAllocation()throws Exception {
        for(String frame:List.of("Content-Length: 2097153\r\n\r\n","Content-Length: -1\r\n\r\n","Content-Length: 2\r\nContent-Length: 2\r\n\r\n{}","Content-Length: nope\r\n\r\n","X:"+"x".repeat(8192))) {
            assertThrows(IOException.class,()->new BoundedRpcInput(new ByteArrayInputStream(frame.getBytes(StandardCharsets.US_ASCII))).readAllBytes());
        }
        String frame="Content-Length: 2\r\n\r\n{}Content-Length: 2\r\n\r\n[]";
        assertEquals(frame,new String(new BoundedRpcInput(new ByteArrayInputStream(frame.getBytes(StandardCharsets.US_ASCII))).readAllBytes(),StandardCharsets.US_ASCII));
        assertThrows(EOFException.class,()->new BoundedRpcInput(new ByteArrayInputStream("Content-Length: 8\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII))).readAllBytes());
    }
    @Test void numericWireScalarsAreExactAndTypedValueBudgetIsEnforced() {
        var large=new GretlProtocol.Value("Long","9223372036854775807",null,null,null,null);
        assertEquals(new ch.so.agi.gretl.tooling.edit.EditableValue.Literal(Long.MAX_VALUE),large.core());
        assertThrows(IllegalArgumentException.class,()->new GretlProtocol.Value("Boolean","yes",null,null,null,null).core());
        assertThrows(IllegalArgumentException.class,()->new GretlProtocol.Value("RawGroovy","new File('x')",null,null,null,null).core());
        var current=large;for(int i=0;i<18;i++)current=new GretlProtocol.Value("List",null,List.of(current),null,null,null);
        var deep=current;assertThrows(IllegalArgumentException.class,deep::core);
    }
    @Test void customWireAdaptersRejectUnknownFieldsAndNumericStringCoercion() {
        var gson=new com.google.gson.GsonBuilder().registerTypeAdapterFactory(new ToolingWireAdapter()).create();
        for(String value:List.of("{\"kind\":\"Long\",\"scalar\":9223372036854775807}","{\"kind\":\"Text\",\"raw\":\"code\"}"))assertThrows(com.google.gson.JsonParseException.class,()->gson.fromJson(value,GretlProtocol.Value.class));
        assertThrows(com.google.gson.JsonParseException.class,()->gson.fromJson("{\"protocolVersion\":1.5,\"documentId\":\"file:///work/job.groovy\"}",GretlProtocol.GraphRequest.class));
        assertEquals("9223372036854775807",gson.fromJson("{\"kind\":\"Long\",\"scalar\":\"9223372036854775807\"}",GretlProtocol.Value.class).scalar());
    }
}
