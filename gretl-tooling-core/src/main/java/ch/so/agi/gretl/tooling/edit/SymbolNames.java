package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.ProjectNameRules;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;

final class SymbolNames {
    private SymbolNames() { }
    static SymbolId local(AnalysisSnapshot snapshot,String scope,String name,Namespace namespace) {
        if(name==null||name.isBlank())throw new IllegalArgumentException("EDIT_INVALID_NAME");
        if(snapshot.analysis().capture().project().projectMode()) {
            try{return new SymbolId(namespace,ProjectNameRules.parseQualified(scope).qualifyLocal(name));}
            catch(IllegalArgumentException invalid){throw new IllegalArgumentException("EDIT_INVALID_NAME");}
        }
        if(namespace==Namespace.PARAMETER&&!name.matches("[A-Za-z][A-Za-z0-9_.-]*")
                &&!name.matches(":(?:[A-Za-z][A-Za-z0-9_-]*)(?::[A-Za-z][A-Za-z0-9_-]*)*"))throw new IllegalArgumentException("EDIT_INVALID_NAME");
        return new SymbolId(namespace,name);
    }
}
