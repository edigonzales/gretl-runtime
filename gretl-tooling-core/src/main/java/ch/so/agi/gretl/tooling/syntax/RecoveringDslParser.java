package ch.so.agi.gretl.tooling.syntax;

import java.util.*;
import org.apache.groovy.parser.antlr4.GroovyLexer;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import static ch.so.agi.gretl.tooling.syntax.TokenIndex.*;

/** Token-grammar recovery. Only top-level islands are declarations; strings/comments are never scanned as code. */
final class RecoveringDslParser {
    private final SourceText source;
    private final List<LexToken> tokens;
    private final CancellationToken cancellation;
    private final int[] mate;
    private static final Set<String> DSL=Set.of("task","action","parameter","defaults","cleanup","include");
    private static final Set<String> HELPERS=Set.of("parameter","result","resultOptional","file","path","workPath","config","secret","sensitive");
    RecoveringDslParser(SourceText source,TokenIndex index,CancellationToken cancellation){
        this.source=source;this.tokens=index.significant();this.cancellation=cancellation;mate=new int[tokens.size()];Arrays.fill(mate,-1);
        var stack=new ArrayDeque<Integer>();
        for(int i=0;i<tokens.size();i++){int type=tokens.get(i).type();if(open(type))stack.push(i);else if(close(type)&&!stack.isEmpty()&&pair(tokens.get(stack.peek()).type(),type)){int start=stack.pop();mate[start]=i;mate[i]=start;}}
    }
    GretlAstExtractor.Extraction recover(){
        var declarations=new ArrayList<Declaration>();var opaque=new ArrayList<OpaqueStatement>();int braceDepth=0,parenDepth=0;var shadowed=new HashSet<String>();
        for(int j=0;j<tokens.size();j++)if(tokens.get(j).type()==GroovyLexer.IMPORT){int end=lineEnd(j,tokens.size());for(int k=j+1;k<end;k++){if(tokens.get(k).type()==GroovyLexer.MUL)shadowed.addAll(DSL);if(DSL.contains(tokens.get(k).text()))shadowed.add(tokens.get(k).text());}j=Math.max(j,end-1);}
        for(int i=0;i<tokens.size();i++){
            cancellation.check();var token=tokens.get(i);String name=token.text();int type=token.type();
            if(braceDepth==0&&token.text().equals("def")&&i+1<tokens.size())shadowed.add(tokens.get(i+1).text());
            if(braceDepth==0&&parenDepth==0&&token.channel()==Channel.CODE&&DSL.contains(name)&&!shadowed.contains(name)&&statementStart(i)){
                var recovered=declaration(i);declarations.add(recovered.declaration());i=recovered.lastToken();continue;
            }
            if(type==GroovyLexer.LBRACE)braceDepth++;else if(type==GroovyLexer.RBRACE)braceDepth=Math.max(0,braceDepth-1);
            if(type==GroovyLexer.LPAREN||type==GroovyLexer.LBRACK)parenDepth++;else if(type==GroovyLexer.RPAREN||type==GroovyLexer.RBRACK)parenDepth=Math.max(0,parenDepth-1);
        }
        // Preserve every byte outside known islands with explicit uncertainty.
        int start=0;for(var declaration:declarations){if(declaration.span().startUtf16()>start)opaque.add(new OpaqueStatement(new TextSpan(start,declaration.span().startUtf16()),"RECOVERY_OPAQUE",true));start=declaration.span().endUtf16();}
        if(start<source.text().length())opaque.add(new OpaqueStatement(new TextSpan(start,source.text().length()),"RECOVERY_OPAQUE",true));
        return new GretlAstExtractor.Extraction(declarations,opaque,Coverage.partial());
    }
    private boolean statementStart(int i){
        if(i==0)return true;var previous=tokens.get(i-1);if(previous.type()==GroovyLexer.SEMI||previous.type()==GroovyLexer.RBRACE)return true;
        if(previous.type()==GroovyLexer.DOT||previous.type()==GroovyLexer.SAFE_DOT||previous.text().equals("def"))return false;
        if(previous.type()==GroovyLexer.ASSIGN&&i>=3&&tokens.get(i-3).text().equals("def"))return true;
        String between=source.text().substring(previous.span().endUtf16(),tokens.get(i).span().startUtf16());return between.indexOf('\n')>=0||between.indexOf('\r')>=0;
    }
    private record Recovered(Declaration declaration,int lastToken) { }
    private Recovered declaration(int start){
        DeclarationKind kind=DeclarationKind.valueOf(tokens.get(start).text().toUpperCase(Locale.ROOT));
        int first=start+1,last=first,end=tokens.size(),bodyStart=-1,bodyEnd=-1,headerEnd=-1;boolean parentheses=first<end&&tokens.get(first).type()==GroovyLexer.LPAREN;
        if(parentheses){
            int close=mate[first];first++;
            if(close>=0){headerEnd=close;last=close;if(close+1<end&&tokens.get(close+1).type()==GroovyLexer.LBRACE){bodyStart=close+1;bodyEnd=mate[bodyStart];last=bodyEnd<0?end-1:bodyEnd;}}
            else {headerEnd=end;last=end-1;}
        } else {
            headerEnd=lineEnd(first,end);last=Math.max(start,headerEnd-1);
            for(int i=first;i<headerEnd;i++)if(tokens.get(i).type()==GroovyLexer.LBRACE){bodyStart=i;bodyEnd=mate[i];headerEnd=i;last=bodyEnd<0?end-1:bodyEnd;break;}
        }
        var positional=new ArrayList<ValueExpression>();var options=new ArrayList<MapEntry>();
        for(var range:split(first,headerEnd,GroovyLexer.COMMA)){
            if(range[0]>=range[1])continue;int colon=topLevel(range[0],range[1],GroovyLexer.COLON);
            if(colon==range[0]+1){ValueExpression key=mapKey(range[0],colon);var value=value(colon+1,range[1],0);options.add(new MapEntry(key,value,span(range[0],range[1])));}
            else positional.add(value(range[0],range[1],0));
        }
        String name=positional.isEmpty()?null:positional.getFirst() instanceof LiteralValue l&&l.value() instanceof String s?s:null;
        StringLiteralSpan literal=positional.isEmpty()?null:positional.getFirst() instanceof LiteralValue l?l.literal():null;
        var targets=(kind==DeclarationKind.DEFAULTS||kind==DeclarationKind.INCLUDE)?List.copyOf(positional):List.<ValueExpression>of();
        if(kind==DeclarationKind.DEFAULTS||kind==DeclarationKind.INCLUDE){name=null;literal=null;}
        ValueExpression operation=kind==DeclarationKind.TASK&&positional.size()>1?positional.get(1):null;
        TextSpan full=span(start,Math.min(end,last+1));TextSpan body=bodyStart<0?null:span(bodyStart,bodyEnd<0?end:bodyEnd+1);
        TextSpan header=new TextSpan(full.startUtf16(),body==null?full.endUtf16():body.startUtf16());
        var fields=new ArrayList<FieldAssignment>();var locals=new ArrayList<LocalVariable>();var refs=new ArrayList<CallValue>();var inner=new ArrayList<InnerInvocation>();
        if(bodyStart>=0)readBody(bodyStart+1,bodyEnd<0?end:bodyEnd,kind==DeclarationKind.TASK,fields,locals,refs);
        String binding=start>=3&&tokens.get(start-1).type()==GroovyLexer.ASSIGN&&tokens.get(start-3).text().equals("def")?tokens.get(start-2).text():null;
        var result=new Declaration(kind,name,literal,binding,binding==null?null:tokens.get(start-2).span(),operation,options,targets,binding==null?full:span(start-3,Math.min(end,last+1)),full,header,body,fields,locals,inner,refs,List.of(),Coverage.partial(),true);
        return new Recovered(result,Math.max(start,last));
    }
    private void readBody(int start,int end,boolean configuration,List<FieldAssignment> fields,List<LocalVariable> locals,List<CallValue> refs){
        var localNames=new HashSet<String>();
        for(int i=start;i<end;){
            cancellation.check();int stop=lineEnd(i,end);if(stop<=i)stop=i+1;
            if(tokens.get(i).text().equals("def")&&i+1<stop){locals.add(new LocalVariable(tokens.get(i+1).text(),span(i,stop)));localNames.add(tokens.get(i+1).text());}
            else if(configuration&&i+1<stop&&identifier(tokens.get(i))&&tokens.get(i+1).type()==GroovyLexer.ASSIGN&&!localNames.contains(tokens.get(i).text())){
                var value=value(i+2,stop,0);fields.add(new FieldAssignment(tokens.get(i).text(),tokens.get(i).span(),value,span(i,stop)));collectReferences(value,refs);
            }
            i=stop;if(i<end&&tokens.get(i).type()==GroovyLexer.SEMI)i++;
        }
    }
    private static void collectReferences(ValueExpression value,List<CallValue> out){
        if(value instanceof CallValue c){out.add(c);c.arguments().forEach(v->collectReferences(v,out));}
        if(value instanceof PropertyValue p)collectReferences(p.receiver(),out);
        if(value instanceof ListValue l)l.items().forEach(v->collectReferences(v,out));
        if(value instanceof MapValue m)m.entries().forEach(e->collectReferences(e.value(),out));
    }
    private ValueExpression value(int start,int end,int depth){
        if(start>=end)return new OpaqueExpression(start<tokens.size()?new TextSpan(tokens.get(start).span().startUtf16(),tokens.get(start).span().startUtf16()):new TextSpan(source.text().length(),source.text().length()),"INCOMPLETE_EXPRESSION");
        var span=span(start,end);if(depth>128)return new OpaqueExpression(span,"EXPRESSION_LIMIT");
        var token=tokens.get(start);
        if(end==start+1){
            if(token.channel()==Channel.STRING&&!token.kind().equals("GString"))try{var decoded=LiteralDecoder.decode(source,span);return new LiteralValue(decoded.value(),span,decoded.span());}catch(IllegalArgumentException e){return new OpaqueExpression(span,"INCOMPLETE_STRING");}
            if(token.type()==GroovyLexer.BooleanLiteral)return new LiteralValue(Boolean.valueOf(token.text()),span,null);
            if(token.type()==GroovyLexer.NullLiteral)return new LiteralValue(null,span,null);
            if(token.type()==GroovyLexer.IntegerLiteral)try{return new LiteralValue(integer(token.text()),span,null);}catch(NumberFormatException e){return new OpaqueExpression(span,"NUMERIC_LITERAL");}
            if(token.type()==GroovyLexer.FloatingPointLiteral)try{return new LiteralValue(new java.math.BigDecimal(token.text().replace("_","").replaceFirst("[gGdDfF]$","")),span,null);}catch(NumberFormatException e){return new OpaqueExpression(span,"NUMERIC_LITERAL");}
            if(identifier(token))return new NameValue(token.text(),span);
        }
        if(token.type()==GroovyLexer.LBRACK&&mate[start]==end-1){
            var ranges=split(start+1,end-1,GroovyLexer.COMMA);boolean map=start+1<end-1&&tokens.get(start+1).type()==GroovyLexer.COLON||ranges.stream().anyMatch(r->topLevel(r[0],r[1],GroovyLexer.COLON)>=0);
            if(map){var entries=new ArrayList<MapEntry>();for(var r:ranges){int colon=topLevel(r[0],r[1],GroovyLexer.COLON);if(colon==r[0]&&r[1]==r[0]+1)continue;if(colon<0)return new OpaqueExpression(span,"PARTIAL_MAP");entries.add(new MapEntry(mapKey(r[0],colon),value(colon+1,r[1],depth+1),span(r[0],r[1])));}return new MapValue(entries,span);}
            return new ListValue(ranges.stream().filter(r->r[0]<r[1]).map(r->value(r[0],r[1],depth+1)).toList(),span);
        }
        int open=start+1;String receiver=null,name=token.text();
        if(start+3<end&&tokens.get(start+1).type()==GroovyLexer.DOT&&token.text().equals("ctx")){receiver="ctx";name=tokens.get(start+2).text();open=start+3;}
        ValueExpression base=null;int next=start+1;
        if(HELPERS.contains(name)&&open<end&&tokens.get(open).type()==GroovyLexer.LPAREN&&mate[open]>=0&&mate[open]<end){
            int close=mate[open];var args=split(open+1,close,GroovyLexer.COMMA).stream().filter(r->r[0]<r[1]).map(r->value(r[0],r[1],depth+1)).toList();
            base=new CallValue(name,receiver,args,span(start,close+1));next=close+1;
        }else if(identifier(token))base=new NameValue(token.text(),token.span());
        if(base!=null){while(next+1<end&&tokens.get(next).type()==GroovyLexer.DOT&&identifier(tokens.get(next+1))){base=new PropertyValue(base,tokens.get(next+1).text(),tokens.get(next+1).span(),span(start,next+2));next+=2;}if(next==end)return base;}
        return new OpaqueExpression(span,"RECOVERY_EXPRESSION");
    }
    private ValueExpression mapKey(int start,int end){return end==start+1&&identifier(tokens.get(start))?new LiteralValue(tokens.get(start).text(),tokens.get(start).span(),null):value(start,end,0);}
    private List<int[]> split(int start,int end,int separator){var parts=new ArrayList<int[]>();int from=start;for(int i=start;i<end;i++){if(tokens.get(i).type()==separator){parts.add(new int[]{from,i});from=i+1;}else if(open(tokens.get(i).type())&&mate[i]>=i&&mate[i]<end)i=mate[i];}parts.add(new int[]{from,end});return parts;}
    private int topLevel(int start,int end,int type){for(int i=start;i<end;i++){if(tokens.get(i).type()==type)return i;if(open(tokens.get(i).type())&&mate[i]>=i&&mate[i]<end)i=mate[i];}return -1;}
    private int lineEnd(int start,int end){for(int i=start;i<end;i++){if(tokens.get(i).type()==GroovyLexer.SEMI)return i;if(i>start){String gap=source.text().substring(tokens.get(i-1).span().endUtf16(),tokens.get(i).span().startUtf16());if(gap.indexOf('\n')>=0||gap.indexOf('\r')>=0)return i;}if(open(tokens.get(i).type())&&mate[i]>=i&&mate[i]<end)i=mate[i];}return end;}
    private TextSpan span(int start,int end){if(start>=tokens.size())return new TextSpan(source.text().length(),source.text().length());return new TextSpan(tokens.get(start).span().startUtf16(),end>start?tokens.get(end-1).span().endUtf16():tokens.get(start).span().startUtf16());}
    private static boolean identifier(LexToken token){return token.type()==GroovyLexer.Identifier||token.type()==GroovyLexer.CapitalizedIdentifier;}
    private static boolean open(int t){return t==GroovyLexer.LPAREN||t==GroovyLexer.LBRACK||t==GroovyLexer.LBRACE;}
    private static boolean close(int t){return t==GroovyLexer.RPAREN||t==GroovyLexer.RBRACK||t==GroovyLexer.RBRACE;}
    private static boolean pair(int a,int b){return a==GroovyLexer.LPAREN&&b==GroovyLexer.RPAREN||a==GroovyLexer.LBRACK&&b==GroovyLexer.RBRACK||a==GroovyLexer.LBRACE&&b==GroovyLexer.RBRACE;}
    private static java.math.BigInteger integer(String text){String n=text.replace("_","").replaceFirst("[lLiIgG]$","");int radix=10;if(n.startsWith("0x")||n.startsWith("0X")){radix=16;n=n.substring(2);}else if(n.startsWith("0b")||n.startsWith("0B")){radix=2;n=n.substring(2);}else if(n.length()>1&&n.startsWith("0")){radix=8;n=n.substring(1);}return new java.math.BigInteger(n,radix);}
}
