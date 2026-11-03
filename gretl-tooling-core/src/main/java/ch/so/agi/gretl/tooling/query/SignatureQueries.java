package ch.so.agi.gretl.tooling.query;

import ch.so.agi.gretl.tooling.query.QueryResults.SignatureInfo;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.Anchor;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.Channel;
import ch.so.agi.gretl.tooling.source.TextSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Signatures of AST-recognized current DSL calls, including helpers in their actual scope. */
final class SignatureQueries {
    private record Candidate(String name,TextSpan span,Declaration declaration,CallValue helper) {
        Candidate(String name,TextSpan span){this(name,span,null,null);}
    }
    Optional<SignatureInfo> signatureHelp(QueryContext context) {
        var token=context.tokens().flatMap(t->t.at(context.offsetUtf16()));
        if(token.isPresent()&&(token.get().channel()==Channel.COMMENT||token.get().channel()==Channel.OPAQUE))return Optional.empty();
        var calls=new ArrayList<Candidate>();
        context.document().ifPresent(document-> {
            for(var declaration:document.declarations()) {
                if(!opaqueAt(declaration.operation(),context.offsetUtf16())&&declaration.options().stream().noneMatch(e->opaqueAt(e.value(),context.offsetUtf16())))calls.add(new Candidate(switch(declaration.kind()) {
                    case TASK->"task";case ACTION->"action";case PARAMETER->"parameterDeclaration";
                    case DEFAULTS->"defaults";case CLEANUP->"cleanup";case INCLUDE->"include";
                },declaration.header(),declaration,null));
                for(var call:declaration.references())if(call.arguments().stream().noneMatch(v->opaqueAt(v,context.offsetUtf16())))calls.add(new Candidate(call.name(),call.span(),null,call));
                for(var inner:declaration.invocations()) {
                    int end=inner.body()==null?inner.span().endUtf16():inner.body().startUtf16();
                    calls.add(new Candidate("ctx.run",new TextSpan(inner.span().startUtf16(),end)));
                }
            }
        });
        var settings=context.snapshot().analysis().capture().project().settings();
        if(settings!=null&&settings.source().stamp().documentId().equals(context.documentId()))settings.directives().forEach((name,span)->calls.add(new Candidate(name,span)));
        return calls.stream().filter(c->c.span()!=null&&c.span().startUtf16()<=context.offsetUtf16()&&context.offsetUtf16()<=c.span().endUtf16())
                .sorted(Comparator.comparingInt(c->c.span().length())).map(c->signature(context,c)).flatMap(Optional::stream).findFirst();
    }

    private boolean opaqueAt(ValueExpression value,int position) {
        if(value==null||!value.span().contains(position))return false;
        return switch(value) {
            case OpaqueExpression ignored->true;
            case ListValue list->list.items().stream().anyMatch(v->opaqueAt(v,position));
            case MapValue map->map.entries().stream().anyMatch(e->opaqueAt(e.value(),position)||opaqueAt(e.key(),position));
            case CallValue call->call.arguments().stream().anyMatch(v->opaqueAt(v,position));
            default->false;
        };
    }

    private Optional<SignatureInfo> signature(QueryContext context,Candidate call) {
        List<String> parameters=switch(call.name()) {
            case "task"->List.of("name: String","operation: OperationType","options: [dependsOn, after, description, when: Closure, timeout: positive ISO-8601 Duration]","configuration: Closure");
            case "action"->List.of("name: String","options: [dependsOn, after, description, when: Closure, timeout: positive ISO-8601 Duration]","body: Closure");
            case "parameterDeclaration"->List.of("name: String","options: [type: text|boolean|integer|path, required: boolean, description: String, defaultValue]");
            case "parameter"->List.of("name: String","type: Class (optional; integer resolves to Long)");
            case "result"->List.of("task: String (ResultView) | TaskRef (raw result)");
            case "resultOptional"->List.of("task: String | TaskRef (Optional)");
            case "file"->List.of("jobRelativePath: String");
            case "workPath"->List.of("workRelativePath: String");
            case "secret"->List.of("alias: String");
            case "defaults"->List.of("targets: String...");
            case "include"->List.of("projectPaths: String...");
            case "cleanup"->List.of("name: String (optional)","body: Closure");
            case "ctx.run"->List.of("operation: OperationType","configuration: Closure");
            default->List.of();
        };
        if(parameters.isEmpty())return Optional.empty();
        boolean leadingOptions=call.declaration()!=null&&call.declaration().nameLiteral()!=null&&call.declaration().options().stream()
                .anyMatch(e->e.key().span().startUtf16()<call.declaration().nameLiteral().token().startUtf16());
        if(leadingOptions&&List.of("task","action","parameterDeclaration").contains(call.name())) {
            var reordered=new ArrayList<>(parameters);int option=call.name().equals("task")?2:1;
            String options=reordered.remove(option);reordered.addFirst(options);parameters=List.copyOf(reordered);
        }
        int depth=0,commas=0;boolean rootParen=false;
        for(var token:context.tokens().orElseThrow().within(call.span())) {
            if(token.span().startUtf16()>=context.offsetUtf16())break;
            if(token.channel()!=Channel.CODE)continue;
            String text=token.text();
            if(text.equals("(")||text.equals("[")||text.equals("{")) {if(depth==0&&text.equals("("))rootParen=true;depth++;}
            else if(text.equals(")")||text.equals("]")||text.equals("}"))depth--;
            else if(text.equals(",")&&depth==(rootParen?1:0))commas++;
        }
        int active=Math.min(commas,parameters.size()-1);
        var declaration=call.declaration();
        if(declaration!=null) {
            int optionIndex=leadingOptions?0:call.name().equals("task")?2:1;
            if(declaration.options().stream().anyMatch(e->e.span().contains(context.offsetUtf16())))active=Math.min(optionIndex,parameters.size()-1);
            else if(declaration.nameLiteral()!=null&&declaration.nameLiteral().token().contains(context.offsetUtf16()))active=leadingOptions?1:0;
            else if(declaration.operation()!=null&&declaration.operation().span().contains(context.offsetUtf16()))active=leadingOptions?2:1;
        } else if(call.helper()!=null) {
            for(int i=0;i<call.helper().arguments().size();i++)if(call.helper().arguments().get(i).span().contains(context.offsetUtf16()))active=Math.min(i,parameters.size()-1);
        }
        String name=call.name().equals("parameterDeclaration")?"parameter":call.name();
        return Optional.of(new SignatureInfo(new Anchor(context.documentId(),call.span()),name+"("+String.join(", ",parameters)+")",parameters,
                active,"GRETL "+context.snapshot().analysis().capture().catalog().result().catalog().map(c->c.dslVersion()).orElse("DSL")));
    }
}
