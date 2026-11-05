package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.semantic.*;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import java.net.URI;
import java.util.*;
import org.eclipse.lsp4j.*;

final class LspDiagnosticMapper {
    static List<PublishDiagnosticsParams> map(AnalysisSnapshot snapshot,boolean relatedInformation) {
        var mapper=new LspQueryMapper(snapshot);var grouped=new LinkedHashMap<URI,List<org.eclipse.lsp4j.Diagnostic>>();
        snapshot.analysis().capture().project().documents().keySet().forEach(id->grouped.put(id,new ArrayList<>()));
        var settings=snapshot.analysis().capture().project().settings();if(settings!=null)grouped.put(settings.source().stamp().documentId(),new ArrayList<>());
        for(var diagnostic:snapshot.analysis().diagnostics()) {
            var range=mapper.range(diagnostic.anchor());if(range.isEmpty())continue;
            var item=new org.eclipse.lsp4j.Diagnostic();item.setRange(range.orElseThrow());item.setMessage(diagnostic.message()+(diagnostic.application()==null?"":" ["+diagnostic.application().ownerScope()+" · "+diagnostic.application().entryDocument()+"]"));item.setSource("gretl-runtime");item.setCode(diagnostic.code());
            item.setSeverity(switch(diagnostic.severity()){case ERROR->DiagnosticSeverity.Error;case WARNING->DiagnosticSeverity.Warning;case INFORMATION->DiagnosticSeverity.Information;});
            if(relatedInformation)item.setRelatedInformation(diagnostic.related().stream().map(mapper::location).flatMap(Optional::stream).map(location->new DiagnosticRelatedInformation(location,diagnostic.code())).toList());
            grouped.computeIfAbsent(diagnostic.anchor().documentId(),id->new ArrayList<>()).add(item);
        }
        return grouped.entrySet().stream().map(e->{var result=new PublishDiagnosticsParams();result.setUri(e.getKey().toASCIIString());result.setDiagnostics(e.getValue());snapshot.source(e.getKey()).ifPresent(source->result.setVersion(source.stamp().bufferVersion()));return result;}).toList();
    }
}
