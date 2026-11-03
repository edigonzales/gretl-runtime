package ch.so.agi.gretl.tooling.syntax;

import java.util.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Explicit AST/recovery precedence and syntax-problem deduplication. */
final class ParseResultMerger {
    List<Declaration> declarations(List<Declaration> ast,List<Declaration> recovery){
        var result=new TreeMap<Integer,Declaration>();recovery.forEach(d->result.put(d.span().startUtf16(),d));ast.forEach(d->result.put(d.span().startUtf16(),d));return List.copyOf(result.values());
    }
    List<SyntaxProblem> problems(List<SyntaxProblem> lexical,List<SyntaxProblem> parsed){
        var result=new ArrayList<SyntaxProblem>(lexical);
        for(var problem:parsed){
            int duplicate=-1;
            for(int i=0;i<result.size();i++){
                var existing=result.get(i);
                if(existing.code().equals("PARSE_LIMIT")||problem.code().equals("PARSE_LIMIT"))continue;
                if(existing.span().startUtf16()==problem.span().startUtf16()||existing.span().contains(problem.span())||problem.span().contains(existing.span())){duplicate=i;break;}
            }
            if(duplicate>=0){if(problem.span().length()<=result.get(duplicate).span().length())result.set(duplicate,problem);}
            else result.add(problem);
        }
        result.sort(Comparator.comparingInt(p->p.span().startUtf16()));return List.copyOf(result);
    }
    Declaration recoveredIsland(Declaration d){return new Declaration(d.kind(),d.name(),d.nameLiteral(),d.bindingName(),d.bindingSpan(),d.operation(),d.options(),d.targets(),d.statementSpan(),d.span(),d.header(),d.body(),d.fields(),d.locals(),d.invocations(),d.references(),d.properties(),d.coverage(),true).withGuardedExecution(d.guardedExecution());}
}
