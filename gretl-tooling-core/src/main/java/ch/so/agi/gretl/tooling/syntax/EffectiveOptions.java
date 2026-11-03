package ch.so.agi.gretl.tooling.syntax;

import ch.so.agi.gretl.tooling.source.TextSpan;
import java.util.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Explicit Groovy maps retain all source entries; only their last constant key wins. */
public record EffectiveOptions(Map<String,MapEntry> entries,boolean complete,List<TextSpan> inactiveClosures) {
    public EffectiveOptions { entries=Collections.unmodifiableMap(new LinkedHashMap<>(entries));inactiveClosures=List.copyOf(inactiveClosures); }
    public static EffectiveOptions of(Declaration declaration){
        var entries=new LinkedHashMap<String,MapEntry>();boolean complete=true;
        for(var entry:declaration.options())if(entry.key() instanceof LiteralValue l&&l.value() instanceof String key)entries.put(key,entry);else complete=false;
        var inactive=new ArrayList<TextSpan>();
        for(var entry:declaration.options())if(entry.value() instanceof OpaqueExpression opaque&&opaque.reason().equals("CLOSURE")){
            String key=entry.key() instanceof LiteralValue l&&l.value() instanceof String s?s:null;
            if(!complete||key==null||entries.get(key)!=entry)inactive.add(entry.value().span());
        }
        return new EffectiveOptions(entries,complete,inactive);
    }
    /** Discarded closures do not run. Nonclosure map expressions still evaluate, even if overwritten. */
    public boolean active(TextSpan span){return inactiveClosures.stream().noneMatch(parent->parent.contains(span));}
}
