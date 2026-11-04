package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.tooling.syntax.GretlDocumentParser;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.runtime.api.OperationProvider;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** Hostile workspace/provider/AST-transform fixture stays exclusively in test scope. */
class StaticParsingSecurityTest {
    @TempDir Path root;
    @Test void topLevelGrabAstTestSettingsAndStaticInitializersNeverExecute() throws Exception {
        Path marker=root.resolve("executed.txt");String path=marker.toString().replace("\\","\\\\").replace("'","\\'");
        var parser=new GretlDocumentParser();
        for(String code:List.of(
                "new File('"+path+"').text='top'; action('a') {}",
                "@Grab('invalid.local.do-not-download:absent:0')\nclass Workspace {}\naction('a') {}",
                "@groovy.transform.ASTTest(value={new File('"+path+"').text='ast'})\nclass Workspace {}",
                "class Workspace {static {new File('"+path+"').text='static'}}; new Workspace()",
                "include new File('"+path+"').text='settings'")){
            parser.parse(SourceText.job(code));assertFalse(Files.exists(marker),code);
        }
    }
    @Test void parseDoesNotContactNetworkForTopLevelOrGrabResolvers() throws Exception {
        var requests=new java.util.concurrent.atomic.AtomicInteger();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{requests.incrementAndGet();byte[] data="fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,data.length);try(var out=exchange.getResponseBody()){out.write(data);}});
        server.start();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort()+"/";
            // A real positive control proves the loopback observer would see a request.
            try(var in=java.net.URI.create(url).toURL().openStream()){assertEquals("fixture",new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
            assertEquals(1,requests.get());requests.set(0);
            var parser=new GretlDocumentParser();
            parser.parse(SourceText.job("new URL('"+url+"executed').text; action('safe') {}"));
            parser.parse(SourceText.job("@GrabResolver(name='fixture', root='"+url+"')\n@Grab(group='local.fixture', module='must-not-resolve', version='0')\nclass Workspace {}"));
            assertEquals(0,requests.get(),"Static parse contacted the workspace network endpoint");
        } finally {server.stop(0);}
    }
    @Test void maliciousContextClasspathIsIgnoredAndPositiveControlsReallyTrigger() throws Exception {
        Path marker=root.resolve("poison.txt"),classes=root.resolve("classes");Files.createDirectories(classes);
        String markerJava=marker.toString().replace("\\","\\\\").replace("\"","\\\"");
        Path provider=root.resolve("PoisonProvider.java"),transform=root.resolve("PoisonTransform.java");
        String write="try {java.nio.file.Files.writeString(java.nio.file.Path.of(\""+markerJava+"\"),\"executed\");}catch(Exception e){throw new RuntimeException(e);}";
        Files.writeString(provider,"public class PoisonProvider implements ch.so.agi.gretl.runtime.api.OperationProvider {static {"+write+"} public java.util.Collection<ch.so.agi.gretl.runtime.api.OperationType<?,?>> operations(){"+write+"return java.util.List.of();}} ");
        Files.writeString(transform,"@org.codehaus.groovy.transform.GroovyASTTransformation(phase=org.codehaus.groovy.control.CompilePhase.CONVERSION) public class PoisonTransform implements org.codehaus.groovy.transform.ASTTransformation {static {"+write+"} public void visit(org.codehaus.groovy.ast.ASTNode[] n,org.codehaus.groovy.control.SourceUnit s){"+write+"}} ");
        int result=javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","--release","25","-classpath",System.getProperty("java.class.path"),"-d",classes.toString(),provider.toString(),transform.toString());assertEquals(0,result);
        Path services=classes.resolve("META-INF/services");Files.createDirectories(services);
        Files.writeString(services.resolve("ch.so.agi.gretl.runtime.api.OperationProvider"),"PoisonProvider\n");
        Files.writeString(services.resolve("org.codehaus.groovy.transform.ASTTransformation"),"PoisonTransform\n");
        // Positive control: this workspace provider would execute if a loader discovered it.
        try(var loader=new URLClassLoader(new URL[]{classes.toUri().toURL()},getClass().getClassLoader())) {
            ServiceLoader.load(OperationProvider.class,loader).forEach(p->p.operations());assertTrue(Files.exists(marker));
        }
        Files.delete(marker);
        // Positive control: general CompilationUnit CONVERSION really discovers the hostile global transform.
        try(var parent=new URLClassLoader(new URL[]{classes.toUri().toURL()},getClass().getClassLoader());var loader=new GroovyClassLoader(parent)) {
            var unit=new CompilationUnit(new CompilerConfiguration(),null,loader);unit.addSource("Control.groovy","1");unit.compile(Phases.CONVERSION);assertTrue(Files.exists(marker));
        }
        Files.delete(marker);
        var loaded=new AtomicBoolean();ClassLoader previous=Thread.currentThread().getContextClassLoader();
        try(var poisoned=new URLClassLoader(new URL[]{classes.toUri().toURL()},getClass().getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(name.startsWith("Poison"))loaded.set(true);return super.loadClass(name,resolve);}
        }) {
            Thread.currentThread().setContextClassLoader(poisoned);
            new GretlDocumentParser().parse(SourceText.job("@PoisonTransform\nclass Workspace {}\naction('safe') { println 'ok' }"));
            assertFalse(loaded.get(),"Parser requested a workspace class");assertFalse(Files.exists(marker),"Parser executed a provider or transform");
        } finally {Thread.currentThread().setContextClassLoader(previous);}
    }
}
