package ch.so.agi.gretl.tooling.syntax;

import java.util.*;
import org.apache.groovy.parser.antlr4.util.StringUtils;
import ch.so.agi.gretl.tooling.source.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Pure literal decoding from Groovy's own escape utility, with explicit original escape ranges. */
final class LiteralDecoder {
    record Decoded(String value,StringLiteralSpan span) { }
    static Decoded decode(SourceText source,TextSpan token) {
        String raw=source.slice(token);QuoteKind kind;int width,mode=StringUtils.NONE_SLASHY;
        if(raw.startsWith("$/")){kind=QuoteKind.DOLLAR_SLASHY;width=2;mode=StringUtils.DOLLAR_SLASHY;}
        else if(raw.startsWith("'''")){kind=QuoteKind.TRIPLE_SINGLE;width=3;}
        else if(raw.startsWith("\"\"\"")){kind=QuoteKind.TRIPLE_DOUBLE;width=3;}
        else if(raw.startsWith("'")){kind=QuoteKind.SINGLE;width=1;}
        else if(raw.startsWith("\"")){kind=QuoteKind.DOUBLE;width=1;}
        else if(raw.startsWith("/")){kind=QuoteKind.SLASHY;width=1;mode=StringUtils.SLASHY;}
        else throw new IllegalArgumentException("Not a string literal");
        if(raw.length()<2*width)throw new IllegalArgumentException("Incomplete string literal");
        String content=raw.substring(width,raw.length()-width),decoded=StringUtils.replaceEscapes(StringUtils.removeCR(content),mode);
        var mapping=new ArrayList<Integer>();var reconstructed=new StringBuilder();
        for(int i=0;i<content.length();) {
            int start=i,end=i+1;char c=content.charAt(i);
            if(c=='\r'&&i+1<content.length()&&content.charAt(i+1)=='\n')end=i+2;
            else if(c=='\r'){i++;continue;}
            if(c=='\\'&&i+1<content.length()) {
                end=i+2;char n=content.charAt(i+1);
                if(n=='u'){while(end<content.length()&&content.charAt(end)=='u')end++;end=Math.min(content.length(),end+4);}
                else if(n>='0'&&n<='7'){while(end<content.length()&&end<i+4&&content.charAt(end)>='0'&&content.charAt(end)<='7')end++;}
                else if(n=='\r'&&end<content.length()&&content.charAt(end)=='\n')end++;
            } else if(mode==StringUtils.DOLLAR_SLASHY&&c=='$'&&i+1<content.length()&&(content.charAt(i+1)=='$'||content.charAt(i+1)=='/'))end=i+2;
            String unit=StringUtils.replaceEscapes(StringUtils.removeCR(content.substring(start,end)),mode);reconstructed.append(unit);
            for(int j=0;j<unit.length();j++)mapping.add(token.startUtf16()+width+start);
            i=end;
        }
        if(!reconstructed.toString().equals(decoded))mapping.clear();
        mapping.add(token.endUtf16()-width);
        return new Decoded(decoded,new StringLiteralSpan(token,new TextSpan(token.startUtf16()+width,token.endUtf16()-width),kind,mapping));
    }
}
