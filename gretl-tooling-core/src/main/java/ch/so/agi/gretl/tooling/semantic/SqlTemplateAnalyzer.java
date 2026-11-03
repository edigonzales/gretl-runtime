package ch.so.agi.gretl.tooling.semantic;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.syntax.EffectiveOptions;
import ch.so.agi.gretl.tooling.syntax.SqlSourceSyntax;
import ch.so.agi.gretl.tooling.workspace.*;
import ch.so.agi.gretl.tooling.workspace.WorkspaceManager.WorkspaceCapture;
import java.util.*;
import java.util.regex.Pattern;
import static ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import static ch.so.agi.gretl.tooling.semantic.ProjectAnalyzer.*;

/** Matches the current DuckDB/Db2Db textual substitution contract, including SQL comments and strings. */
public final class SqlTemplateAnalyzer {
    private static final Pattern PLACEHOLDER=Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_.-]*)}");
    private SqlTemplateAnalyzer() { }
    public record Placeholder(String name,int startUtf16,int endUtf16) { }
    public record PlaceholderScan(List<Placeholder> placeholders,boolean complete){public PlaceholderScan{placeholders=List.copyOf(placeholders);}}
    public static PlaceholderScan placeholders(String sql,CancellationToken cancellation){
        var found=new ArrayList<Placeholder>();var matcher=PLACEHOLDER.matcher(sql);while(matcher.find()){cancellation.check();if(found.size()>=16000)return new PlaceholderScan(found,false);found.add(new Placeholder(matcher.group(1),matcher.start(),matcher.end()));}return new PlaceholderScan(found,true);
    }
    @FunctionalInterface interface Reporter {void report(String code,Severity severity,Anchor anchor);}
    static void validate(WorkspaceCapture capture,Symbol symbol,Reporter reporter,CancellationToken cancellation,boolean conditional,java.util.function.Predicate<ch.so.agi.gretl.tooling.source.TextSpan> guarded){
        if(symbol.operation()!=null)validateFields(capture,symbol.anchor(),symbol.operation().symbol(),symbol.declaration().fields(),guarded(reporter,conditional),cancellation);
        for(var inner:symbol.declaration().invocations())if(EffectiveOptions.of(symbol.declaration()).active(inner.span())&&inner.operation() instanceof NameValue operation&&capture.catalog().result().catalog().stream().flatMap(c->c.operations().stream()).anyMatch(op->op.symbol().equals(operation.name())))validateFields(capture,new Anchor(symbol.anchor().documentId(),inner.span()),operation.name(),inner.fields(),guarded(reporter,guarded.test(inner.span())||inner.coverage().fieldAssignments()!=Completeness.COMPLETE),cancellation);
    }
    private static Reporter guarded(Reporter reporter,boolean conditional){return (code,severity,anchor)->reporter.report(code,conditional&&severity==Severity.ERROR?Severity.WARNING:severity,anchor);}
    private static void validateFields(WorkspaceCapture capture,Anchor owner,String operation,List<FieldAssignment> fields,Reporter reporter,CancellationToken cancellation){
        // SqlExecutor.scripts are plain SQL; its prepared statements use JDBC ?, not named templates.
        if(!SqlSourceSyntax.templated(operation))return;
        var assignments=SqlSourceSyntax.assignments(fields);var values=SqlSourceSyntax.files(operation,fields);
        var sets=parameterSets(assignments);int inspected=0;
        for(var file:values){cancellation.check();if(++inspected>512){reporter.report("SEMANTIC_LIMIT",Severity.INFORMATION,owner);return;}
            if(!(file instanceof CallValue call&&call.name().equals("file")&&call.arguments().size()==1&&text(call.arguments().getFirst())!=null)){
                reporter.report("PATH_DYNAMIC",Severity.INFORMATION,new Anchor(owner.documentId(),file.span()));continue;
            }
            java.net.URI resource;
            try{resource=SourceUris.jobResource(owner.documentId(),text(call.arguments().getFirst()));}catch(IllegalArgumentException invalid){reporter.report("PATH_INVALID",Severity.ERROR,new Anchor(owner.documentId(),file.span()));continue;}
            if(resource==null){reporter.report("PATH_DYNAMIC",Severity.INFORMATION,new Anchor(owner.documentId(),file.span()));continue;}
            var source=capture.project().sources().texts().get(resource);if(source==null){reporter.report("SQL_SOURCE_NOT_INSPECTED",Severity.INFORMATION,new Anchor(owner.documentId(),file.span()));continue;}
            var scan=placeholders(source.text(),cancellation);if(!scan.complete())reporter.report("SEMANTIC_LIMIT",Severity.INFORMATION,new Anchor(resource,null));
            if(sets==null){if(!scan.placeholders().isEmpty())reporter.report("SQL_PARAMETERS_DYNAMIC",Severity.INFORMATION,new Anchor(owner.documentId(),file.span()));continue;}
            for(var placeholder:scan.placeholders())for(var set:sets){cancellation.check();if(!set.containsKey(placeholder.name())||Boolean.FALSE.equals(set.get(placeholder.name())))reporter.report("SQL_PARAMETER_MISSING",Severity.ERROR,new Anchor(resource,new ch.so.agi.gretl.tooling.source.TextSpan(placeholder.startUtf16(),placeholder.endUtf16())));else if(set.get(placeholder.name())==null)reporter.report("SQL_PARAMETERS_DYNAMIC",Severity.INFORMATION,new Anchor(resource,new ch.so.agi.gretl.tooling.source.TextSpan(placeholder.startUtf16(),placeholder.endUtf16())));}
        }
    }
    /** null map means dynamic structure; null value means a known key with unknown nullness. */
    private static List<Map<String,Boolean>> parameterSets(Map<String,ValueExpression> fields){
        var sets=fields.get("sqlParameterSets");var base=fields.get("sqlParameters");
        if(sets==null||sets instanceof LiteralValue l&&l.value()==null)return base==null?List.of(Map.of()):oneMap(base);
        if(!(sets instanceof ListValue list))return null;
        if(list.items().isEmpty())return base==null?List.of(Map.of()):oneMap(base);
        var result=new ArrayList<Map<String,Boolean>>();for(var value:list.items()){var single=oneMap(value);if(single==null)return null;result.add(single.getFirst());}return result;
    }
    private static List<Map<String,Boolean>> oneMap(ValueExpression expression){
        if(expression instanceof LiteralValue l&&l.value()==null)return List.of(Map.of());
        if(!(expression instanceof MapValue map))return null;var result=new LinkedHashMap<String,Boolean>();
        for(var entry:map.entries()){String key=text(entry.key());if(key==null)return null;result.put(key,entry.value() instanceof LiteralValue literal?literal.value()!=null:null);}return List.of(result);
    }
}
