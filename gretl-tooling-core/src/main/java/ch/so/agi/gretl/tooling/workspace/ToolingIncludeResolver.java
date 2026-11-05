package ch.so.agi.gretl.tooling.workspace;

import java.net.URI;
import java.util.*;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.syntax.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import static ch.so.agi.gretl.tooling.workspace.IncludeModel.*;
import static ch.so.agi.gretl.tooling.workspace.ProjectResolver.*;

/** Bounded, non-executing scope-aware DFS using the same frozen overlay acquisition as entries. */
final class ToolingIncludeResolver {
    private final ProjectId project;
    private final URI definitionRoot;
    private final List<JobSource> jobs;
    private final SourceView view;
    private final Map<URI,ParsedDocument> documents;
    private final List<ProjectProblem> problems;
    private final CancellationToken cancellation;
    private final List<SourceApplication> applications=new ArrayList<>();
    private final List<IncludeLink> links=new ArrayList<>();
    private final Set<ApplicationId> visited=new HashSet<>(),active=new HashSet<>();
    private boolean complete=true;
    ToolingIncludeResolver(ProjectId project,URI definitionRoot,List<JobSource> jobs,SourceView view,
                           Map<URI,ParsedDocument> documents,List<ProjectProblem> problems,CancellationToken cancellation) {
        this.project=project;this.definitionRoot=SourceUris.directoryIdentity(definitionRoot);this.jobs=jobs;
        this.view=view;this.documents=documents;this.problems=problems;this.cancellation=cancellation;
    }
    IncludeClosure resolve() {
        var entries=new ArrayList<SourceApplication>();
        for(var job:jobs) {
            var entry=new SourceApplication(new ApplicationId(project,job.documentId(),job.scope().canonical(),job.documentId()),
                    SourceUris.parent(job.documentId()),SourceRole.ENTRY,List.of());
            entries.add(entry);visit(entry,0);
        }
        var registrations=new ArrayList<RegistrationEvent>();boolean order=true;
        for(var entry:entries){var expansion=RegistrationSequence.expand(entry,documents,links,cancellation);registrations.addAll(expansion.registrations());order&=expansion.orderProven();}
        return new IncludeClosure(applications,links,registrations,complete&&!view.freeze().limited(),order&&complete);
    }
    private void visit(SourceApplication application,int depth) {
        cancellation.check();var id=application.id();
        if(depth>=64||applications.size()>=4096){complete=false;return;}
        applications.add(application);visited.add(id);active.add(id);
        var read=view.read(id.sourceDocument(),cancellation);
        if(read.source().isEmpty()){complete=false;active.remove(id);return;}
        var doc=documents.computeIfAbsent(id.sourceDocument(),uri->new GretlDocumentParser().parse(read.source().orElseThrow(),DocumentKind.JOB,ParseOptions.DEFAULT,cancellation));
        if(!doc.problems().isEmpty()||doc.coverage().syntax()!=Completeness.COMPLETE)complete=false;
        if(application.role()==SourceRole.FRAGMENT) {
            for(var p:doc.problems())problem(p.code(),id.sourceDocument(),p.span(),null,application.includeChain());
            for(var p:doc.includes().fragmentProblems()){
                var mapper=new ch.so.agi.gretl.tooling.source.ParserCoordinateMapper(doc.source());
                int start=mapper.parserOffset(p.line(),p.column());
                problem(p.code(),id.sourceDocument(),new TextSpan(start,start),null,application.includeChain());complete=false;
            }
        }
        for(var directive:doc.includes().directives()) {
            cancellation.check();var site=new IncludeSiteId(id.sourceDocument(),directive.start());
            var chain=new ArrayList<>(application.includeChain());chain.add(site);URI target=null;SourceApplication child=null;
            IncludeResolution resolution=IncludeResolution.INVALID;String failure=null;
            try {
                target=target(id.sourceDocument(),directive.from());
                var traversal=new SourceRepository.IncludeTraversal(id.sourceDocument(),directive.from(),definitionRoot);
                var issues=view.checkIncludeTraversal(traversal,cancellation);
                if(!issues.isEmpty()&&!issues.stream().allMatch(i->i.code().equals("SOURCE_MISSING")))failure=issues.stream().anyMatch(i->i.code().equals("SOURCE_TRAVERSAL_UNSUPPORTED"))?"APPLY_SOURCE_NOT_INSPECTED":"APPLY_PATH_UNSAFE";
                if(failure==null) {
                    var childId=new ApplicationId(project,id.entryDocument(),id.ownerScope(),target);
                    child=new SourceApplication(childId,application.resourceBase(),SourceRole.FRAGMENT,chain);
                    if(active.contains(childId)){failure="APPLY_CYCLE";resolution=IncludeResolution.CYCLE;}
                    else if(visited.contains(childId)){failure="APPLY_DUPLICATE";resolution=IncludeResolution.DUPLICATE;}
                    else if(jobs.stream().anyMatch(j->j.documentId().equals(childId.sourceDocument()))){failure="APPLY_CONTEXT";}
                    else if(depth+1>=64||applications.size()>=4096){failure="APPLY_LIMIT";resolution=IncludeResolution.LIMITED;}
                    else {
                        var childRead=view.read(target,cancellation);
                        if(childRead.source().isEmpty()){
                            resolution=IncludeResolution.UNAVAILABLE;
                            failure=childRead.issues().stream().allMatch(i->i.code().equals("SOURCE_MISSING"))?"APPLY_NOT_FOUND":"APPLY_SOURCE_NOT_INSPECTED";
                        } else resolution=IncludeResolution.RESOLVED;
                    }
                }
            } catch(IllegalArgumentException unsafe){failure="APPLY_PATH_UNSAFE";}
            links.add(new IncludeLink(id,site,target,child,resolution));
            if(failure!=null){complete=false;problem(failure,id.sourceDocument(),new TextSpan(directive.literalStart(),directive.literalEnd()),target,chain);}
            else visit(child,depth+1);
        }
        // A dynamic/invalid apply or recovered syntax can never stand for a complete empty closure.
        if(!doc.includes().problems().isEmpty())complete=false;
        active.remove(id);
    }
    private URI target(URI caller,String from) {
        if(!"file".equals(caller.getScheme())||from.isBlank()||from.indexOf('\\')>=0||from.indexOf(':')>=0||from.startsWith("/")
                ||from.codePoints().anyMatch(Character::isISOControl)||from.chars().anyMatch(c->"*?[]{}".indexOf(c)>=0))throw new IllegalArgumentException("APPLY_PATH_UNSAFE");
        var uri=SourceUris.child(SourceUris.parent(caller),from);
        if(!uri.getPath().endsWith(".groovy")||!SourceUris.contains(definitionRoot,uri)||!view.policy().permits(uri))throw new IllegalArgumentException("APPLY_PATH_UNSAFE");
        return uri;
    }
    private void problem(String code,URI document,TextSpan span,URI target,List<IncludeSiteId> chain) {
        var related=new ArrayList<RelatedSource>();
        if(target!=null)related.add(new RelatedSource(target,null));
        for(var site:chain)if(!site.sourceDocument().equals(document)||span==null||site.startUtf16()!=span.startUtf16())related.add(new RelatedSource(site.sourceDocument(),new TextSpan(site.startUtf16(),site.startUtf16())));
        problems.add(new ProjectProblem(code,document,span,!code.equals("APPLY_LIMIT")&&!code.equals("APPLY_SOURCE_NOT_INSPECTED"),List.of(),related));
    }
}
