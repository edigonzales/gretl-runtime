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
                                    SourceListing inventory,long generation,boolean complete,boolean domainComplete) {
        public OwnershipSnapshot(Map<URI,List<SourceApplication>> applications,Map<ProjectId,ResolvedProject> projects,SourceListing inventory,long generation,boolean complete){this(applications,projects,inventory,generation,complete,complete);}
        public OwnershipSnapshot {
            var copy=new LinkedHashMap<URI,List<SourceApplication>>();applications.forEach((id,apps)->copy.put(id,List.copyOf(apps)));
            applications=Map.copyOf(copy);projects=Map.copyOf(projects);Objects.requireNonNull(inventory);
        }
        public static OwnershipSnapshot unknown(){return new OwnershipSnapshot(Map.of(),Map.of(),new SourceListing(List.of(),List.of(),false),-1,false);}
        public OwnershipSnapshot withGeneration(long value){return new OwnershipSnapshot(applications,projects,inventory,value,complete,domainComplete);}
        public List<SourceApplication> contexts(URI source){return applications.getOrDefault(DocumentStore.identity(source),List.of());}
        public boolean exclusive(URI source){
            source=DocumentStore.identity(source);var contexts=contexts(source);
            if(!domainComplete||contexts.size()!=1)return false;
            if(complete)return true;
            // A known entry cannot be a fragment of its own definition. An incomplete
            // independent definition outside its directory cannot consume it either.
            if(contexts.getFirst().role()!=SourceRole.ENTRY)return false;
            for(var project:projects.values())if(!project.complete()||!project.includeClosure().sourcesComplete()){
                if(source.equals(project.settingsId()))continue;
                if(SourceUris.contains(project.directory(),source)){
                    boolean entry=false;for(var job:project.jobs())if(job.documentId().equals(source)){entry=true;break;}
                    if(!entry)return false;
                }
            }
            return true;
        }
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
        boolean complete=listing.complete(),domainComplete=listing.complete();int applicationCount=active.includeClosure().applications().size();
        for(var candidate:candidates) {
            cancellation.check();if(!view.policy().permits(candidate.documentId()))continue;
            if(projects.values().stream().anyMatch(p->p.id().singleJob()==candidate.singleJob()
                    &&(p.id().anchor().equals(candidate.documentId())||!candidate.singleJob()&&p.jobs().stream().anyMatch(j->j.documentId().equals(candidate.documentId())))))continue;
            if(projects.size()>=128){complete=false;domainComplete=false;break;}
            var request=new ProjectRequest(activeRequest.workspaceRoot(),candidate.documentId(),candidate.singleJob(),activeRequest.excludedRoots());
            var resolved=new ProjectResolver().resolve(request,view,parsed,cancellation);
            if(!projects.containsKey(resolved.id())){
                if(applicationCount+resolved.includeClosure().applications().size()>4096){complete=false;domainComplete=false;break;}
                applicationCount+=resolved.includeClosure().applications().size();projects.put(resolved.id(),resolved);
            }
        }
        var owners=new LinkedHashMap<URI,List<SourceApplication>>();
        for(var p:projects.values()){
            complete&=p.complete()&&p.includeClosure().sourcesComplete();
            for(var application:p.includeClosure().applications())owners.computeIfAbsent(application.id().sourceDocument(),id->new ArrayList<>()).add(application);
            if(p.settingsId()!=null){
                var settings=new SourceApplication(new ApplicationId(p.id(),p.settingsId(),":",p.settingsId()),p.directory(),SourceRole.ENTRY,List.of());
                owners.computeIfAbsent(p.settingsId(),id->new ArrayList<>()).add(settings);
            }
        }
        domainComplete&=!view.freeze().limited();complete&=domainComplete;
        return new OwnershipSnapshot(owners,projects,listing,generation,complete,domainComplete);
    }
}
