package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.semantic.SemanticModel.Anchor;
import java.net.URI;
import java.util.List;

/** Concrete refusal reasons and sources needed by a host before requesting a new plan. */
public record EditCapability(State state, List<Reason> reasons, List<URI> dependencies, List<URI> requiredOpenDocuments) {
    public enum State { ALLOWED, NEEDS_OPEN_DOCUMENTS, REJECTED }
    public record Reason(String code, String message, List<Anchor> related,List<ch.so.agi.gretl.tooling.workspace.IncludeModel.ApplicationId> owners) {
        public Reason(String code,String message,List<Anchor> related){this(code,message,related,List.of());}
        public Reason { related = List.copyOf(related);owners=List.copyOf(owners); }
    }
    public EditCapability {
        reasons = List.copyOf(reasons);
        dependencies = List.copyOf(dependencies);
        requiredOpenDocuments = List.copyOf(requiredOpenDocuments);
    }
}
