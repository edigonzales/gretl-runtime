package ch.so.agi.gretl.tooling.workspace;

import java.net.URI;
import java.util.*;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectId;

/** Physical source identity is independent of each logical application in a definition. */
public final class IncludeModel {
    private IncludeModel() { }
    public enum SourceRole { ENTRY, FRAGMENT }
    public record ApplicationId(ProjectId project,URI entryDocument,String ownerScope,URI sourceDocument) {
        public ApplicationId {
            Objects.requireNonNull(project);Objects.requireNonNull(ownerScope);
            entryDocument=DocumentStore.identity(entryDocument);sourceDocument=DocumentStore.identity(sourceDocument);
        }
    }
    public record IncludeSiteId(URI sourceDocument,int startUtf16) {
        public IncludeSiteId { sourceDocument=DocumentStore.identity(sourceDocument);if(startUtf16<0)throw new IllegalArgumentException("Negative include offset"); }
    }
    public enum IncludeResolution { RESOLVED, UNAVAILABLE, INVALID, CYCLE, DUPLICATE, LIMITED }
    public record IncludeLink(ApplicationId caller,IncludeSiteId site,URI targetDocument,
                              SourceApplication target,IncludeResolution resolution) {
        public IncludeLink {
            Objects.requireNonNull(caller);Objects.requireNonNull(site);Objects.requireNonNull(resolution);
            if(!site.sourceDocument().equals(caller.sourceDocument()))throw new IllegalArgumentException("Include site outside caller");
            if(target!=null&&(!target.id().project().equals(caller.project())||!target.id().entryDocument().equals(caller.entryDocument())
                    ||!target.id().ownerScope().equals(caller.ownerScope())||!target.id().sourceDocument().equals(targetDocument)))
                throw new IllegalArgumentException("Include target changed application context");
        }
    }
    public record RegistrationEvent(SourceApplication application,ch.so.agi.gretl.tooling.syntax.SyntaxModel.Declaration declaration) {
        public RegistrationEvent { Objects.requireNonNull(application);Objects.requireNonNull(declaration); }
    }
    public record SourceApplication(ApplicationId id,URI resourceBase,SourceRole role,List<IncludeSiteId> includeChain) {
        public SourceApplication {
            Objects.requireNonNull(id);Objects.requireNonNull(role);
            resourceBase=SourceUris.directoryIdentity(resourceBase);includeChain=List.copyOf(includeChain);
        }
    }
}
