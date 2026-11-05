package ch.so.agi.gretl.tooling.workspace;

import java.net.URI;
import java.util.*;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.ParsedDocument;
import static ch.so.agi.gretl.tooling.workspace.IncludeModel.*;
import static ch.so.agi.gretl.tooling.workspace.ProjectResolver.*;
import static ch.so.agi.gretl.tooling.workspace.SourceRepository.*;

/** Bounded refactoring domain: conventional workspace entries plus explicitly registered entries. */
public final class SourceOwnershipIndex {
    public record EntryRegistration(URI documentId,boolean singleJob) {
        public EntryRegistration {documentId=DocumentStore.identity(documentId);}
    }
    public record OwnershipSnapshot(Map<URI,List<SourceApplication>> applications,Map<ProjectId,ResolvedProject> projects,
                                    SourceListing inventory,long generation,boolean complete) {
        public OwnershipSnapshot {
            var copy=new LinkedHashMap<URI,List<SourceApplication>>();applications.forEach((id,apps)->copy.put(id,List.copyOf(apps)));
            applications=Map.copyOf(copy);projects=Map.copyOf(projects);Objects.requireNonNull(inventory);
        }
        public static OwnershipSnapshot unknown(){return new OwnershipSnapshot(Map.of(),Map.of(),new SourceListing(List.of(),List.of(),false),-1,false);}
        public OwnershipSnapshot withGeneration(long value){return new OwnershipSnapshot(applications,projects,inventory,value,complete);}
        public List<SourceApplication> contexts(URI source){return applications.getOrDefault(DocumentStore.identity(source),List.of());}
        public boolean exclusive(URI source){return complete&&contexts(source).size()==1;}
    }
    public OwnershipSnapshot captureOwners(ProjectRequest activeRequest,ResolvedProject active,SourceView view,
                                           Set<EntryRegistration> explicitEntries,long generation,CancellationToken cancellation) {
        var listing=view.listProjectEntries(activeRequest.workspaceRoot(),ScanBudget.defaults(),cancellation);
        var projects=new LinkedHashMap<ProjectId,ResolvedProject>();if(!active.includeClosure().applications().isEmpty()||active.projectMode())projects.put(active.id(),active);
        var parsed=new LinkedHashMap<URI,ParsedDocument>(active.documents());
        var candidates=new LinkedHashSet<EntryRegistration>();
        listing.entries().stream().filter(id->id.getPath()!=null&&(id.getPath().endsWith("/settings.gretl")||id.getPath().endsWith("/job.groovy")))
                .sorted(Comparator.comparing((URI id)->!id.getPath().endsWith("/settings.gretl")).thenComparing(URI::toString))
                .forEach(id->candidates.add(new EntryRegistration(id,false)));
        candidates.addAll(explicitEntries);
        boolean complete=listing.complete();int applicationCount=active.includeClosure().applications().size();
        for(var candidate:candidates) {
            cancellation.check();if(!view.policy().permits(candidate.documentId()))continue;
            if(projects.values().stream().anyMatch(p->p.id().singleJob()==candidate.singleJob()
                    &&(p.id().anchor().equals(candidate.documentId())||!candidate.singleJob()&&p.jobs().stream().anyMatch(j->j.documentId().equals(candidate.documentId())))))continue;
            if(projects.size()>=128){complete=false;break;}
            var request=new ProjectRequest(activeRequest.workspaceRoot(),candidate.documentId(),candidate.singleJob(),activeRequest.excludedRoots());
            var resolved=new ProjectResolver().resolve(request,view,parsed,cancellation);
            if(!projects.containsKey(resolved.id())){
                if(applicationCount+resolved.includeClosure().applications().size()>4096){complete=false;break;}
                applicationCount+=resolved.includeClosure().applications().size();projects.put(resolved.id(),resolved);
            }
        }
        var owners=new LinkedHashMap<URI,List<SourceApplication>>();
        for(var p:projects.values()){
            complete&=p.complete()&&p.includeClosure().sourcesComplete();
            for(var application:p.includeClosure().applications())owners.computeIfAbsent(application.id().sourceDocument(),id->new ArrayList<>()).add(application);
        }
        complete&=!view.freeze().limited();
        return new OwnershipSnapshot(owners,projects,listing,generation,complete);
    }
}
