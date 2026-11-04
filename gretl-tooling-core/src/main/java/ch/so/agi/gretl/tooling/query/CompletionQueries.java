package ch.so.agi.gretl.tooling.query;

import ch.so.agi.gretl.language.DslVocabulary;
import ch.so.agi.gretl.language.ProjectNameRules;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.edit.SourceValueWriter;
import ch.so.agi.gretl.tooling.query.QueryResults.CompletionProposal;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.syntax.TokenIndex.Channel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Contexts come from captured DSL nodes. Token depth only constrains insertion into their owned gaps. */
final class CompletionQueries {
    List<CompletionProposal> complete(QueryContext context) {
        if(context.tokens().isEmpty()||context.tokens().orElseThrow().budgetExceeded())return List.of();
        var token=context.tokens().orElseThrow().at(context.offsetUtf16());
        if(token.isPresent()&&(token.get().channel()==Channel.COMMENT||token.get().channel()==Channel.OPAQUE))return List.of();
        if(token.isEmpty()&&context.offsetUtf16()>0&&context.tokens().orElseThrow().at(context.offsetUtf16()-1).filter(t->t.channel()==Channel.COMMENT).isPresent())return List.of();
        var result=new ArrayList<CompletionProposal>();
        var analysis=context.snapshot().analysis();
        // Bound references also cover settings defaults and all dependency scalar/list forms.
        for(var reference:analysis.references())if(reference.anchor().documentId().equals(context.documentId())&&touch(reference.anchor().span(),context.offsetUtf16())) {
            var owned=context.tokens().orElseThrow().all().stream().filter(t->t.span().contains(reference.anchor().span())
                    &&(t.channel()==Channel.STRING&&!t.kind().equals("GString")||t.channel()==Channel.CODE&&t.span().equals(reference.anchor().span())&&t.text().matches("[\\p{L}_$][\\p{L}\\p{N}_$]*"))).findFirst();
            if(owned.isEmpty())return List.of();
            Namespace namespace=reference.kind()==ReferenceKind.PARAMETER?Namespace.PARAMETER:Namespace.TASK;
            var range=literalRange(context,reference.anchor().span());
            names(context,result,namespace,range,scope(context));
            return finish(result);
        }
        var document=context.document().orElse(null);
        if(document==null)return settings(context,result);
        for(var declaration:document.declarations()) {
            if(!touch(declaration.span(),context.offsetUtf16()))continue;
            var symbol=analysis.symbols().values().stream().flatMap(List::stream).filter(s->s.anchor().documentId().equals(context.documentId())&&s.declaration().equals(declaration)).findFirst().orElse(null);
            for(var property:declaration.properties())if(touch(property.propertySpan(),context.offsetUtf16())
                    &&property.receiver() instanceof CallValue call&&call.name().equals("result")&&call.arguments().size()==1
                    &&call.arguments().getFirst() instanceof LiteralValue literal&&literal.value() instanceof String) {
                var reference=analysis.references().stream().filter(r->r.kind()==ReferenceKind.RESULT&&r.anchor().documentId().equals(context.documentId())
                        &&call.span().contains(r.anchor().span())&&r.resolution()==Resolution.RESOLVED).findFirst();
                reference.flatMap(Reference::target).flatMap(analysis::unique).filter(s->s.operation()!=null).ifPresent(producer-> {
                    for(var output:producer.operation().outputs())add(result,output.name(),"FIELD",output.description(),property.propertySpan(),propertyName(output.name()));
                });
                return finish(result);
            }
            for(var call:declaration.references())if(touch(call.span(),context.offsetUtf16())) {
                if(!call.arguments().isEmpty()&&touch(call.arguments().getFirst().span(),context.offsetUtf16())) {
                    var range=call.arguments().getFirst().span();
                    if(call.name().equals("parameter"))names(context,result,Namespace.PARAMETER,range,scope(context));
                    if(call.name().equals("result")||call.name().equals("resultOptional"))names(context,result,Namespace.TASK,range,scope(context));
                    if(call.name().equals("file"))paths(context,result,range,false);
                }
                return finish(result); // Never treat helper arguments as configuration-field gaps.
            }
            if(declaration.operation()!=null&&touch(declaration.operation().span(),context.offsetUtf16())) {
                operations(context,result,declaration.operation().span());return finish(result);
            }
            for(var inner:declaration.invocations())if(touch(inner.operation().span(),context.offsetUtf16())) {
                operations(context,result,inner.operation().span());return finish(result);
            }
            var fields=declaration.fields();
            OperationDescriptor operation=symbol==null?null:symbol.operation();
            TextSpan body=declaration.body();
            for(var inner:declaration.invocations())if(inner.body()!=null&&touch(inner.body(),context.offsetUtf16())) {
                fields=inner.fields();body=inner.body();
                operation=inner.operation() instanceof NameValue name?operations(context).stream().filter(o->o.symbol().equals(name.name())).findFirst().orElse(null):null;
            }
            if(operation!=null) {
                for(var field:fields)if(touch(field.span(),context.offsetUtf16())) {
                    if(touch(field.nameSpan(),context.offsetUtf16())) {
                        for(var input:operation.inputs())add(result,input.name(),"FIELD",input.description(),field.nameSpan(),input.name());
                    } else if(touch(field.value().span(),context.offsetUtf16())) {
                        var descriptor=operation.inputs().stream().filter(i->i.name().equals(field.name())).findFirst().orElse(null);
                        if(descriptor!=null&&!(field.value() instanceof OpaqueExpression))values(result,descriptor,field.value().span());
                    }
                    return finish(result);
                }
                if(body!=null&&body.contains(context.offsetUtf16())&&directGap(context,body)) {
                    var range=word(context);
                    for(var input:operation.inputs())if(fields.stream().noneMatch(f->f.name().equals(input.name())))add(result,input.name(),"FIELD",input.description(),range,input.name()+" = ");
                    return finish(result);
                }
            }
            if(touch(declaration.header(),context.offsetUtf16())) {
                for(var option:declaration.options()) {
                    if(touch(option.key().span(),context.offsetUtf16())) {
                        options(result,declaration,option.key().span(),false);return finish(result);
                    }
                    if(touch(option.value().span(),context.offsetUtf16())) {
                        if(option.value() instanceof OpaqueExpression)return List.of();
                        if(option.key() instanceof LiteralValue key&&key.value() instanceof String name) {
                            if(name.equals("dependsOn")||name.equals("after"))names(context,result,Namespace.TASK,wordOrLiteral(context),scope(context));
                            if(name.equals("required"))booleans(result,option.value().span());
                            if(name.equals("type"))for(var type:DslVocabulary.parameterTypes())quoted(result,type,"VALUE","Parameter type",option.value().span());
                        }
                        return finish(result);
                    }
                }
                if(declaration.kind()==DeclarationKind.TASK&&declaration.operation()==null)operations(context,result,word(context));
                else if(headerGap(context,declaration))options(result,declaration,word(context),true);
                return finish(result);
            }
            return List.of();
        }
        // Top-level suggestions only in statement gaps, never opaque foreign Groovy statements.
        if(document.opaque().stream().anyMatch(o->touch(o.span(),context.offsetUtf16())))return List.of();
        if(token.isPresent()&&token.get().channel()==Channel.STRING)return List.of();
        for(var helper:List.of("task","action","parameter","defaults","cleanup"))add(result,helper,"KEYWORD","GRETL DSL",word(context),helper);
        return finish(result);
    }

    private List<CompletionProposal> settings(QueryContext context,ArrayList<CompletionProposal> result) {
        var settings=context.snapshot().analysis().capture().project().settings();
        if(settings==null||!settings.source().stamp().documentId().equals(context.documentId()))return List.of();
        for(var entry:settings.directives().entrySet())if(touch(entry.getValue(),context.offsetUtf16())) {
            if(entry.getKey().equals("defaults"))names(context,result,Namespace.TASK,wordOrLiteral(context),":");
            else if(entry.getKey().equals("include"))paths(context,result,wordOrLiteral(context),true);
            return finish(result);
        }
        if(context.tokens().orElseThrow().at(context.offsetUtf16()).isPresent())return List.of();
        for(var keyword:List.of("include","defaults"))add(result,keyword,"KEYWORD","Project settings",word(context),keyword);
        return finish(result);
    }

    private void paths(QueryContext context,List<CompletionProposal> result,TextSpan range,boolean include) {
        var base=context.documentId().resolve(".");
        for(var id:context.snapshot().sources().keySet()) {
            if(!context.snapshot().analysis().capture().readPolicy().permits(id))continue;
            var relative=base.relativize(id);
            if(relative.isAbsolute()||relative.equals(id)||relative.getPath()==null||relative.getPath().isEmpty())continue;
            String path=relative.getPath();
            if(include) {
                if(!path.endsWith("/job.groovy"))continue;
                path=path.substring(0,path.length()-"/job.groovy".length()).replace('/',':');
                try {path=ProjectNameRules.parseInclude(path).canonical();}catch(IllegalArgumentException invalid){continue;}
            }
            quoted(result,path,"FILE","Captured project source",range);
        }
    }
    private void names(QueryContext context,List<CompletionProposal> result,Namespace namespace,TextSpan range,String scope) {
        var analysis=context.snapshot().analysis();
        for(var entry:analysis.symbols().entrySet())if(entry.getKey().namespace()==namespace&&entry.getValue().size()==1) {
            String name=entry.getKey().qualifiedName();
            if(analysis.capture().project().projectMode())name=ProjectNameRules.parseQualified(scope).renderReference(name);
            quoted(result,name,"REFERENCE",entry.getValue().getFirst().declaration().kind().name(),range);
        }
    }
    private String scope(QueryContext context) {
        return context.snapshot().analysis().capture().project().jobs().stream().filter(j->j.documentId().equals(context.documentId())).map(j->j.scope().canonical()).findFirst().orElse(":");
    }
    private void operations(QueryContext context,List<CompletionProposal> result,TextSpan range) {
        for(var op:operations(context))add(result,op.symbol(),"CLASS",op.description(),range,op.symbol());
    }
    private List<OperationDescriptor> operations(QueryContext context) {
        return context.snapshot().analysis().capture().catalog().result().catalog().map(c->c.operations()).orElse(List.of());
    }
    private void options(List<CompletionProposal> result,Declaration declaration,TextSpan range,boolean insertColon) {
        var names=declaration.kind()==DeclarationKind.PARAMETER?DslVocabulary.parameterOptions():DslVocabulary.taskOptions();
        for(var name:names)add(result,name,"PROPERTY","GRETL option",range,name+(insertColon?": ":""));
    }
    private void values(List<CompletionProposal> result,InputDescriptor input,TextSpan range) {
        if(input.shape().kind().equals("Text"))for(var value:input.allowedValues())quoted(result,value,"ENUM",input.description(),range);
        if(input.shape().kind().equals("Boolean"))booleans(result,range);
    }
    private void booleans(List<CompletionProposal> result,TextSpan range) {for(var value:List.of("true","false"))add(result,value,"VALUE","Boolean",range,value);}
    private TextSpan literalRange(QueryContext context,TextSpan content) {
        return context.tokens().orElseThrow().all().stream().filter(t->t.channel()==Channel.STRING&&t.span().contains(content)).map(t->t.span()).findFirst().orElse(content);
    }
    private TextSpan wordOrLiteral(QueryContext context) {
        return context.tokens().orElseThrow().at(context.offsetUtf16()).filter(t->t.channel()==Channel.STRING).map(t->t.span()).orElseGet(()->word(context));
    }
    private TextSpan word(QueryContext context) {
        int start=context.offsetUtf16(),end=start;String text=context.source().text();
        while(start>0&&Character.isJavaIdentifierPart(text.charAt(start-1)))start--;
        while(end<text.length()&&Character.isJavaIdentifierPart(text.charAt(end)))end++;
        return new TextSpan(start,end);
    }
    private boolean directGap(QueryContext context,TextSpan body) {
        int depth=0;String previous="";
        for(var token:context.tokens().orElseThrow().within(body)) {
            if(token.span().startUtf16()>=context.offsetUtf16())break;
            if(token.channel()==Channel.WHITESPACE) {if(token.text().contains("\n")||token.text().contains("\r"))previous=";";continue;}
            if(token.channel()==Channel.COMMENT)continue;
            if(List.of("(","[","{").contains(token.text()))depth++;
            if(List.of(")","]","}").contains(token.text()))depth--;
            if(token.span().endUtf16()<=word(context).startUtf16())previous=token.text();
        }
        // Body spans include their opening brace. Foreign calls, maps and nested closures are excluded.
        return depth==1&&List.of("{",";","}").contains(previous);
    }
    private boolean headerGap(QueryContext context,Declaration declaration) {
        var tokens=context.tokens().orElseThrow().within(declaration.header());
        String previous="";
        int start=word(context).startUtf16();
        for(var token:tokens)if(token.span().endUtf16()<=start&&(token.channel()==Channel.CODE||token.channel()==Channel.STRING))previous=token.text();
        return previous.equals(",");
    }
    private boolean touch(TextSpan span,int offset) {return span!=null&&offset>=span.startUtf16()&&offset<=span.endUtf16();}
    private String propertyName(String name) {return name.matches("[A-Za-z_$][A-Za-z0-9_$]*")?name:SourceValueWriter.string(name,QuoteKind.SINGLE);}
    private void quoted(List<CompletionProposal> result,String label,String kind,String detail,TextSpan range) {add(result,label,kind,detail,range,SourceValueWriter.string(label,QuoteKind.SINGLE));}
    private void add(List<CompletionProposal> result,String label,String kind,String detail,TextSpan range,String text) {result.add(new CompletionProposal(label,kind,detail,range,text));}
    private List<CompletionProposal> finish(List<CompletionProposal> result) {return result.stream().distinct().sorted(Comparator.comparing(CompletionProposal::label)).toList();}
}
