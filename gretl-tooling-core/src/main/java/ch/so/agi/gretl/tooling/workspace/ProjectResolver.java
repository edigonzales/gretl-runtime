package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.language.ProjectNameRules;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.*;
import ch.so.agi.gretl.tooling.syntax.*;
import java.net.URI;
import java.util.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import static ch.so.agi.gretl.tooling.workspace.SourceRepository.*;

/** Resolves explicit project entries from the captured settings buffer, never by executing it. */
public final class ProjectResolver {
    public record ProjectId(URI workspaceRoot,URI anchor,boolean singleJob){
        public ProjectId{workspaceRoot=SourceUris.directoryIdentity(workspaceRoot);anchor=DocumentStore.identity(anchor);}
        public ProjectId(URI root,URI anchor){this(root,anchor,false);}
    }
    public record ProjectRequest(URI workspaceRoot,URI documentId,boolean singleJob,List<URI> excludedRoots){
        public ProjectRequest { workspaceRoot=SourceUris.directoryIdentity(workspaceRoot);documentId=DocumentStore.identity(documentId);excludedRoots=excludedRoots.stream().map(SourceUris::directoryIdentity).toList(); }
        public ProjectRequest(URI root,URI id){this(root,id,false,List.of());}
    }
    public record JobSource(ProjectNameRules scope,URI documentId) { }
    public record ProjectProblem(String code,URI documentId,TextSpan span,boolean error,List<TextSpan> related){public ProjectProblem{related=List.copyOf(related);}}
    public record ResolvedProject(ProjectId id,URI directory,URI settingsId,boolean projectMode,List<JobSource> jobs,List<SettingsDocumentParser.SettingName> defaultTargets,List<ProjectProblem> problems,Map<URI,ParsedDocument> documents,SourceView.SnapshotSources sources,boolean complete){
        public ResolvedProject { jobs=List.copyOf(jobs);defaultTargets=List.copyOf(defaultTargets);problems=List.copyOf(problems);documents=Map.copyOf(documents); }
    }
    public ResolvedProject resolve(ProjectRequest request,SourceRepository repository,DocumentStore.BufferCapture overlay,CancellationToken cancellation){
        var explicit=Set.of("untitled","memory").contains(request.documentId().getScheme())?Set.of(request.documentId()):Set.<URI>of();
        var policy=new ReadPolicy(request.workspaceRoot(),1_048_576,true,request.excludedRoots(),explicit);var view=new SourceView(repository,policy,overlay);
        var problems=new ArrayList<ProjectProblem>();URI settings=null,dir=SourceUris.parent(request.documentId());boolean lookupLimited=false;
        if(!request.singleJob()&&request.documentId().getPath()!=null){
            if(request.documentId().getPath().endsWith("/settings.gretl"))settings=request.documentId();
            else {int depth=0;for(;dir!=null&&depth<32&&within(request.workspaceRoot(),dir);depth++,dir=SourceUris.parent(dir)){
                cancellation.check();URI probe=SourceUris.child(dir,"settings.gretl");var read=view.read(probe,cancellation);
                if(read.source().isPresent()||read.issues().stream().anyMatch(i->!i.code().equals("SOURCE_MISSING"))){settings=probe;break;}
                if(sameDirectory(dir,request.workspaceRoot()))break;
            }if(settings==null&&depth>=32&&dir!=null&&within(request.workspaceRoot(),dir)){lookupLimited=true;problems.add(new ProjectProblem("PROJECT_DEPTH_LIMIT",request.documentId(),null,false,List.of()));}}
        }
        var jobs=new ArrayList<JobSource>();var defaults=new ArrayList<SettingsDocumentParser.SettingName>();var documents=new LinkedHashMap<URI,ParsedDocument>();boolean complete=!lookupLimited;
        URI projectDirectory=settings==null?SourceUris.parent(request.documentId()):SourceUris.parent(settings);
        if(projectDirectory==null)projectDirectory=request.workspaceRoot();
        if(settings!=null){
            var read=view.read(settings,cancellation);
            if(read.source().isEmpty()){sourceProblem(settings,read,true,problems);complete=false;}
            else{
                var parsed=new SettingsDocumentParser().parse(read.source().get(),cancellation);defaults.addAll(parsed.defaults());complete&=parsed.complete();
                for(var p:parsed.problems())problems.add(new ProjectProblem(p.code(),settings,p.span(),!p.code().endsWith("LIMIT"),p.related()));
                URI rootJob=SourceUris.child(projectDirectory,"job.groovy");var rootRead=view.read(rootJob,cancellation);
                if(rootRead.source().isPresent())jobs.add(new JobSource(ProjectNameRules.root(),rootJob));else if(rootRead.issues().stream().anyMatch(i->!i.code().equals("SOURCE_MISSING"))){sourceProblem(rootJob,rootRead,false,problems);complete=false;}
                for(var include:parsed.includes()){
                    cancellation.check();var scope=ProjectNameRules.parseQualified(include.canonical());URI file=SourceUris.child(projectDirectory,String.join("/",scope.segments())+"/job.groovy");
                    if(scope.segments().size()>32){problems.add(new ProjectProblem("PROJECT_DEPTH_LIMIT",settings,include.literal().token(),false,List.of()));complete=false;continue;}
                    boolean ambiguous=false;URI ancestor=SourceUris.parent(file);
                    for(int depth=0;ancestor!=null&&!sameDirectory(ancestor,projectDirectory)&&depth<32;depth++,ancestor=SourceUris.parent(ancestor)){
                        var nested=view.read(SourceUris.child(ancestor,"settings.gretl"),cancellation);
                        if(nested.source().isPresent()){ambiguous=true;break;}
                        if(nested.issues().stream().anyMatch(i->!i.code().equals("SOURCE_MISSING"))){sourceProblem(SourceUris.child(ancestor,"settings.gretl"),nested,false,problems);ambiguous=true;break;}
                    }
                    if(ambiguous){problems.add(new ProjectProblem("PROJECT_OWNERSHIP_AMBIGUOUS",settings,include.literal().token(),false,List.of()));complete=false;continue;}
                    var entry=view.read(file,cancellation);if(entry.source().isPresent())jobs.add(new JobSource(scope,file));else{sourceProblem(file,entry,true,problems);complete=false;}
                }
                if(jobs.isEmpty()){problems.add(new ProjectProblem("PROJECT_EMPTY",settings,null,complete,List.of()));complete=false;}
            }
        }else{
            var read=view.read(request.documentId(),cancellation);if(read.source().isPresent())jobs.add(new JobSource(ProjectNameRules.root(),request.documentId()));else{sourceProblem(request.documentId(),read,true,problems);complete=false;}
        }
        for(var job:jobs){
            cancellation.check();var source=view.read(job.documentId(),cancellation).source().orElseThrow();var parsed=new GretlDocumentParser().parse(source,DocumentKind.JOB,ParseOptions.DEFAULT,cancellation);documents.put(job.documentId(),parsed);
            // Only explicitly named SQL resources are acquired; no broad SQL or data-directory scan.
            for(var declaration:parsed.declarations()){
                acquireSqlFiles(declaration.operation(),declaration.fields(),job.documentId(),view,cancellation);
                for(var inner:declaration.invocations())acquireSqlFiles(inner.operation(),inner.fields(),job.documentId(),view,cancellation);
            }
            for(var declaration:parsed.declarations())for(var call:declaration.references())if(call.name().equals("file")&&!call.arguments().isEmpty()&&call.arguments().getFirst() instanceof LiteralValue value&&value.value() instanceof String name&&name.toLowerCase(Locale.ROOT).endsWith(".sql")){
                try{URI resource=SourceUris.jobResource(job.documentId(),name);if(resource!=null)view.read(resource,cancellation);}catch(IllegalArgumentException invalidPath){/* Semantic path analysis reports unsupported literals; never perform guessed IO. */}
            }
        }
        var snapshot=view.freeze();if(snapshot.limited()){complete=false;problems.add(new ProjectProblem("PROJECT_CAPTURE_LIMIT",request.documentId(),null,false,List.of()));}
        return new ResolvedProject(new ProjectId(request.workspaceRoot(),settings==null?request.documentId():settings,request.singleJob()),projectDirectory,settings,settings!=null,jobs,defaults,problems,documents,snapshot,complete);
    }
    private static void acquireSqlFiles(ValueExpression operation,List<FieldAssignment> fields,URI job,SourceView view,CancellationToken cancellation){
        if(!(operation instanceof NameValue name)||!name.name().equals("DuckDbSqlExecutor"))return;
        for(var field:fields)if(field.name().equals("sqlFiles")){
            var values=field.value() instanceof ListValue list?list.items():List.of(field.value());
            for(var value:values)if(value instanceof CallValue call&&call.name().equals("file")&&call.arguments().size()==1&&call.arguments().getFirst() instanceof LiteralValue literal&&literal.value() instanceof String relative){
                try{URI resource=SourceUris.jobResource(job,relative);if(resource!=null)view.read(resource,cancellation);}catch(IllegalArgumentException invalid){/* semantic path diagnostic */}
            }
        }
    }
    private static boolean within(URI root,URI directory){return sameDirectory(root,directory)||SourceUris.contains(root,directory);}
    private static boolean sameDirectory(URI a,URI b){String left=a.getPath(),right=b.getPath();return Objects.equals(a.getScheme(),b.getScheme())&&Objects.equals(a.getAuthority(),b.getAuthority())&&left!=null&&right!=null&&strip(left).equals(strip(right));}
    private static String strip(String path){return path.endsWith("/")?path.substring(0,path.length()-1):path;}
    private static void sourceProblem(URI id,SourceRead read,boolean required,List<ProjectProblem> problems){String code=read.issues().isEmpty()?"SOURCE_UNAVAILABLE":read.issues().getFirst().code();problems.add(new ProjectProblem(required&&code.equals("SOURCE_MISSING")?"PROJECT_ENTRY_MISSING":"PROJECT_SOURCE_NOT_INSPECTED",id,null,required&&code.equals("SOURCE_MISSING"),List.of()));}
}
