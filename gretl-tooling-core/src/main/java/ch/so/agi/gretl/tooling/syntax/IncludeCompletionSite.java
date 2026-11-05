package ch.so.agi.gretl.tooling.syntax;

import ch.so.agi.gretl.tooling.source.*;
import java.util.*;
import java.util.regex.Pattern;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Lexical completion envelopes are hints only, never authoritative include declarations. */
public record IncludeCompletionSite(String value,StringLiteralSpan literal,boolean closed) {
    private static final Pattern OPEN=Pattern.compile("(?m)^[ \\t]*apply[ \\t]*(?:\\([ \\t]*)?(?:\\[[ \\t]*)?from[ \\t]*:[ \\t]*(['\"])");
    public static List<IncludeCompletionSite> in(ParsedDocument document){
        if(document.tokens().budgetExceeded())return List.of();
        var result=new ArrayList<IncludeCompletionSite>();
        for(var directive:document.includes().directives())try{
            var decoded=LiteralDecoder.decode(document.source(),new TextSpan(directive.literalStart(),directive.literalEnd()));
            result.add(new IncludeCompletionSite(decoded.value(),decoded.span(),true));
        }catch(IllegalArgumentException ignored){}
        var text=document.source().text();var matcher=OPEN.matcher(text);
        while(result.size()<64&&matcher.find()){
            int quote=matcher.end()-1,start=matcher.start();
            if(result.stream().anyMatch(s->s.literal().token().startUtf16()==quote))continue;
            var apply=document.tokens().significant().stream().filter(t->t.span().startUtf16()>=start&&t.span().startUtf16()<quote).findFirst();
            if(apply.isEmpty()||!apply.get().text().equals("apply")||apply.get().channel()!=TokenIndex.Channel.CODE)continue;
            int depth=0;for(var token:document.tokens().significant()){if(token.span().startUtf16()>=apply.get().span().startUtf16())break;if(token.channel()==TokenIndex.Channel.CODE){if(token.text().equals("{"))depth++;if(token.text().equals("}"))depth--;}}
            if(depth!=0)continue;
            int end=text.indexOf('\n',quote);if(end<0)end=text.length();if(end>quote&&text.charAt(end-1)=='\r')end--;
            String raw=text.substring(quote,end);char q=text.charAt(quote);
            // Only a single unfinished quoted literal; no interpolation, trailing code or split escapes.
            if(raw.length()>4096||raw.startsWith("'''" )||raw.startsWith("\"\"\"")||raw.indexOf('$')>=0||raw.endsWith("\\"))continue;
            boolean hasClose=false;for(int i=1;i<raw.length();i++){if(raw.charAt(i)=='\\'){i++;continue;}if(raw.charAt(i)==q){hasClose=true;break;}}
            if(hasClose)continue;
            var synthetic=new SourceText(document.source().stamp().documentId(),text.substring(0,end)+q+text.substring(end),null,"COMPLETION_ENVELOPE",false);
            try{var decoded=LiteralDecoder.decode(synthetic,new TextSpan(quote,end+1));result.add(new IncludeCompletionSite(decoded.value(),decoded.span(),false));}catch(IllegalArgumentException ignored){}
        }
        return List.copyOf(result);
    }
    public String prefix(int offset){
        int count=0;for(int source:literal.decodedToSource()){if(source>=offset||count>=value.length())break;count++;}
        return value.substring(0,count);
    }
    public TextSpan replacement(){return closed?literal.token():new TextSpan(literal.token().startUtf16(),literal.token().endUtf16()-1);}
}
