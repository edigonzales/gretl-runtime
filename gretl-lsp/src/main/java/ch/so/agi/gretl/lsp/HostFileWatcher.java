package ch.so.agi.gretl.lsp;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;

/** Watches only registered/captured directories, bounded to 256 handles. Overflow invalidates all known sources. */
final class HostFileWatcher implements AutoCloseable {
    private final WatchService watcher;
    private final Map<WatchKey,Path> directories=new HashMap<>();
    private final Set<Path> registered=new HashSet<>();
    private final Set<URI> known=new HashSet<>();
    private final Consumer<Set<URI>> changed;
    private final Thread thread;
    HostFileWatcher(Consumer<Set<URI>> changed) throws IOException {
        this.changed=changed;watcher=FileSystems.getDefault().newWatchService();
        thread=new Thread(this::run,"gretl-file-watcher");thread.setDaemon(true);thread.start();
    }
    synchronized void capture(Collection<URI> sources) {
        for(var id:sources)if("file".equals(id.getScheme())) {
            if(known.size()<4096)known.add(id);Path directory=Path.of(id).getParent();
            if(directory==null||registered.contains(directory)||registered.size()>=256||Files.isSymbolicLink(directory))continue;
            try {var key=directory.register(watcher,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_MODIFY,StandardWatchEventKinds.ENTRY_DELETE);directories.put(key,directory);registered.add(directory);}
            catch(IOException|ClosedWatchServiceException unavailable){/* isCurrent still rechecks hashes for every response/edit. */}
        }
    }
    private void run() {
        try {
            while(!Thread.currentThread().isInterrupted()) {
                var key=watcher.take();var updates=new LinkedHashSet<URI>();
                synchronized(this) {
                    var directory=directories.get(key);
                    for(var event:key.pollEvents())if(event.kind()==StandardWatchEventKinds.OVERFLOW)updates.addAll(known);
                    else if(directory!=null&&event.context() instanceof Path path) {
                        var id=DocumentStore.fileUri(directory.resolve(path));String name=path.getFileName().toString();
                        if(known.contains(id)||name.equals("job.groovy")||name.equals("settings.gretl"))updates.add(id);
                    }
                    if(!key.reset()){var removed=directories.remove(key);if(removed!=null)registered.remove(removed);}
                }
                if(!updates.isEmpty())changed.accept(Set.copyOf(updates));
            }
        }catch(InterruptedException stopped){Thread.currentThread().interrupt();}catch(ClosedWatchServiceException stopped){ }
    }
    public void close() throws IOException {watcher.close();thread.interrupt();synchronized(this){directories.clear();registered.clear();known.clear();}}
}
