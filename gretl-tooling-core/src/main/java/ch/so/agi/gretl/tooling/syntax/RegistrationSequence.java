package ch.so.agi.gretl.tooling.syntax;

import java.util.*;
import java.net.URI;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.workspace.IncludeModel.*;
import ch.so.agi.gretl.syntax.ApplyAstInspector.Directive;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Ordered source events; include sites are source relationships, never task dependency edges. */
public final class RegistrationSequence {
    private RegistrationSequence() { }
    public sealed interface Event permits DeclarationEvent,IncludeEvent { int startUtf16(); }
    public record DeclarationEvent(Declaration declaration) implements Event {
        public int startUtf16(){return declaration.statementSpan().startUtf16();}
    }
    public record IncludeEvent(Directive directive) implements Event {
        public int startUtf16(){return directive.start();}
    }
    public static List<Event> local(ParsedDocument document) {
        var events=new ArrayList<Event>();
        document.declarations().forEach(d->events.add(new DeclarationEvent(d)));
        document.includes().directives().forEach(d->events.add(new IncludeEvent(d)));
        events.sort(Comparator.comparingInt(Event::startUtf16));return List.copyOf(events);
    }
    /** Effective coverage belongs to an application; never mutate the physical parser result. */
    public static Coverage coverage(ParsedDocument document,SourceApplication application,IncludeClosure closure) {
        if(document.includes().directives().isEmpty())return document.coverage();
        boolean syntax=document.problems().isEmpty()&&document.coverage().syntax()==Completeness.COMPLETE;
        boolean declarations=syntax&&!document.includes().topLevelControlFlowUncertain()
                &&document.opaque().stream().noneMatch(o->o.mayRegisterDeclarations()&&!resolved(o,application,closure))
                &&document.declarations().stream().noneMatch(d->d.name()==null&&d.kind()!=DeclarationKind.DEFAULTS&&d.kind()!=DeclarationKind.INCLUDE);
        boolean references=declarations&&document.declarations().stream().allMatch(d->d.coverage().references()==Completeness.COMPLETE);
        return new Coverage(declarations?Completeness.COMPLETE:Completeness.PARTIAL,references?Completeness.COMPLETE:Completeness.PARTIAL,
                document.coverage().fieldAssignments(),document.coverage().syntax());
    }
    public static boolean resolved(OpaqueStatement statement,SourceApplication application,IncludeClosure closure) {
        return statement.reason().equals("UNRESOLVED_INCLUDE")&&closure.links().stream().anyMatch(l->
                l.caller().equals(application.id())&&l.site().startUtf16()==statement.span().startUtf16()
                        &&l.resolution()==IncludeResolution.RESOLVED);
    }
    public record Expansion(List<RegistrationEvent> registrations,boolean orderProven) {
        public Expansion { registrations=List.copyOf(registrations); }
    }
    /** Expand only captured applications. Missing/cyclic/opaque data never proves registration order. */
    public static Expansion expand(SourceApplication entry,Map<URI,ParsedDocument> sources,
                                   List<IncludeLink> links,CancellationToken cancellation) {
        var byCaller=new HashMap<ApplicationId,Map<Integer,IncludeLink>>();
        for(var link:links){
            var previous=byCaller.computeIfAbsent(link.caller(),id->new HashMap<>()).put(link.site().startUtf16(),link);
            if(previous!=null)throw new IllegalArgumentException("Duplicate include site in application");
        }
        var registrations=new ArrayList<RegistrationEvent>();var visited=new HashSet<ApplicationId>();
        boolean proven=expand(entry,sources,byCaller,cancellation,visited,registrations,0);
        return new Expansion(registrations,proven);
    }
    private static boolean expand(SourceApplication application,Map<URI,ParsedDocument> sources,
                                  Map<ApplicationId,Map<Integer,IncludeLink>> links,CancellationToken cancellation,
                                  Set<ApplicationId> visited,List<RegistrationEvent> registrations,int depth) {
        cancellation.check();
        if(depth>=64||visited.size()>=4096||!visited.add(application.id()))return false;
        var document=sources.get(application.id().sourceDocument());if(document==null)return false;
        boolean proven=document.problems().isEmpty()&&!document.includes().topLevelControlFlowUncertain()&&document.coverage().syntax()==Completeness.COMPLETE
                &&document.opaque().stream().noneMatch(o->o.mayRegisterDeclarations()&&!o.reason().equals("UNRESOLVED_INCLUDE"));
        for(var event:local(document)){
            cancellation.check();
            if(event instanceof DeclarationEvent declaration)registrations.add(new RegistrationEvent(application,declaration.declaration()));
            else if(event instanceof IncludeEvent include){
                var link=links.getOrDefault(application.id(),Map.of()).get(include.startUtf16());
                if(link==null||link.resolution()!=IncludeResolution.RESOLVED||link.target()==null)proven=false;
                else proven&=expand(link.target(),sources,links,cancellation,visited,registrations,depth+1);
            }
        }
        return proven;
    }

}
