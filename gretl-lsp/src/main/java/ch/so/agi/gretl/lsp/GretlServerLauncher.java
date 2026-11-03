package ch.so.agi.gretl.lsp;

import java.io.*;
import java.util.concurrent.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;

/** The executable boundary owns process exit. Fachklassen never terminate the host JVM. */
public final class GretlServerLauncher {
    private GretlServerLauncher(){ }
    public static void main(String[] args) {
        if(args.length!=1||!args[0].equals("--stdio")){System.err.println("Usage: java -jar gretl-lsp-…-jar-with-dependencies.jar --stdio");System.exit(2);}
        if(Runtime.version().feature()!=25){System.err.println("JAVA_25_REQUIRED");System.exit(2);}
        try {System.exit(launchStdio(System.in,System.out).get());}
        catch(Exception failed){System.err.println("LSP_START_OR_TRANSPORT_FAILED: "+ServerWorkspace.code(failed));System.exit(1);}
    }
    public static CompletableFuture<Integer> launchStdio(InputStream input,OutputStream output) throws IOException {
        var server=new GretlLanguageServer(BundledCatalog.load());
        var transport=Executors.newCachedThreadPool(r->{var thread=new Thread(r,"gretl-lsp-transport");thread.setDaemon(true);return thread;});
        Launcher<GretlLanguageClient> launcher=new Launcher.Builder<GretlLanguageClient>().setLocalService(server).setRemoteInterface(GretlLanguageClient.class)
                .setInput(new BoundedRpcInput(input)).setOutput(output).setExecutorService(transport).configureGson(builder->builder.registerTypeAdapterFactory(new ToolingEnumAdapter()).registerTypeAdapterFactory(new ToolingWireAdapter())).create();
        server.connect(launcher.getRemoteProxy());var listening=launcher.startListening();
        var finished=new CompletableFuture<Integer>();
        server.exitSignal().whenComplete((code,failure)->finished.complete(code));
        var monitor=new Thread(()->{try{listening.get();finished.complete(1);}catch(CancellationException stopped){ }catch(Exception failed){finished.complete(1);}},"gretl-lsp-eof");monitor.setDaemon(true);monitor.start();
        finished.whenComplete((code,failure)->{listening.cancel(true);transport.shutdownNow();server.close();});return finished;
    }
}
