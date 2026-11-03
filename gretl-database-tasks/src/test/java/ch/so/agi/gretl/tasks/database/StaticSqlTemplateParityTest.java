package ch.so.agi.gretl.tasks.database;

import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.SqlTemplateAnalyzer;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaticSqlTemplateParityTest {
    private final OperationContext context=(OperationContext)Proxy.newProxyInstance(OperationContext.class.getClassLoader(),new Class<?>[]{OperationContext.class},(proxy,method,args)->{
        if(method.getName().equals("locale"))return Locale.ENGLISH;
        throw new AssertionError("SQL template inspection must not request database, secret or execution services: "+method.getName());
    });
    @Test void actualRuntimeSubstitutionIncludesQuotedTextCommentsAndRepeatedNames(){
        for(String sql:List.of("select '${value}' -- ${comment}\n/* ${block} */ ${value}","select ${under_score}, ${name.with-dot}, ${9invalid}, ${bad space}, ${unterminated", "select '😀${value}'\r\n-- ${comment}")){
            var scan=SqlTemplateAnalyzer.placeholders(sql,CancellationToken.NONE);assertTrue(scan.complete());
            var parameters=new LinkedHashMap<String,String>();for(var placeholder:scan.placeholders()){parameters.put(placeholder.name(),"replacement$\\value");assertEquals("${"+placeholder.name()+"}",sql.substring(placeholder.startUtf16(),placeholder.endUtf16()));}
            String expected=sql;for(var entry:parameters.entrySet())expected=expected.replace("${"+entry.getKey()+"}",entry.getValue());assertEquals(expected,Db2DbSupport.substitute(sql,parameters,context));
            for(String name:parameters.keySet()){
                var missing=new LinkedHashMap<>(parameters);missing.remove(name);assertEquals("DB_PARAMETER",assertThrows(Db2DbTaskException.class,()->Db2DbSupport.substitute(sql,missing,context)).code());
                missing.put(name,null);assertEquals("DB_PARAMETER",assertThrows(Db2DbTaskException.class,()->Db2DbSupport.substitute(sql,missing,context)).code());
            }
        }
    }
    @Test void boundedScanAndCancellationRemainExplicit(){
        var scan=SqlTemplateAnalyzer.placeholders("${name}".repeat(16001),CancellationToken.NONE);assertFalse(scan.complete());assertEquals(16000,scan.placeholders().size());
        assertThrows(java.util.concurrent.CancellationException.class,()->SqlTemplateAnalyzer.placeholders("${name}",()->true));
    }
}
