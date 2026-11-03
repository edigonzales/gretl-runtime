package ch.so.agi.gretl.tooling.syntax;

import java.util.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Current built-in SQL source surfaces; no class loading, path lookup or SQL execution. */
public final class SqlSourceSyntax {
    private SqlSourceSyntax() { }
    public static boolean templated(String operation){return Set.of("DuckDbSqlExecutor","Db2Db").contains(operation);}
    public static Map<String,ValueExpression> assignments(List<FieldAssignment> fields){var result=new LinkedHashMap<String,ValueExpression>();for(var field:fields)result.put(field.name(),field.value());return result;}
    public static List<ValueExpression> files(String operation,List<FieldAssignment> fields){
        var values=assignments(fields);if(operation.equals("DuckDbSqlExecutor"))return items(values.get("sqlFiles"));if(!operation.equals("Db2Db"))return List.of();
        var result=new ArrayList<ValueExpression>();
        for(var transfer:items(values.get("transfers"))){
            if(transfer instanceof MapValue map){ValueExpression file=null;for(var entry:map.entries())if(entry.key() instanceof LiteralValue key&&"sqlFile".equals(key.value()))file=entry.value();if(file!=null)result.add(file);}
            else result.add(transfer);
        }
        return List.copyOf(result);
    }
    private static List<ValueExpression> items(ValueExpression value){return value==null?List.of():value instanceof ListValue list?list.items():List.of(value);}
}
