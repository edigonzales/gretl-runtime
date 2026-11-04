package ch.so.agi.gretl.tooling.syntax;

import ch.so.agi.gretl.language.ProjectNameRules;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.groovy.parser.antlr4.GroovyLexer;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Declarative settings allowlist adapted from the current GretlProjectParser, on the safe parser boundary. */
public final class SettingsDocumentParser {
    public record SettingName(String value,String canonical,StringLiteralSpan literal) { }
    public record SettingsProblem(String code,TextSpan span,String directive,List<TextSpan> related) {
        public SettingsProblem { related=List.copyOf(related); }
    }
    public record SettingsDocument(SourceText source,List<SettingName> includes,List<SettingName> defaults,List<SettingsProblem> problems,boolean complete,TokenIndex tokens,Map<String,TextSpan> directives) {
        public SettingsDocument(SourceText source,List<SettingName> includes,List<SettingName> defaults,List<SettingsProblem> problems,boolean complete){this(source,includes,defaults,problems,complete,new TokenIndex(List.of(),List.of(),true),Map.of());}
        public SettingsDocument { includes=List.copyOf(includes);defaults=List.copyOf(defaults);problems=List.copyOf(problems);directives=Map.copyOf(directives);Objects.requireNonNull(tokens); }
    }
    public SettingsDocument parse(SourceText source,CancellationToken cancellation){
        cancellation.check();var includes=new LinkedHashMap<String,SettingName>();var defaults=new LinkedHashMap<String,SettingName>();var problems=new ArrayList<SettingsProblem>();
        // Runtime settings parse the original string and reject its leading BOM. Keep the source,
        // recover useful directives, but do not claim that the declarative project is valid.
        if(source.hasBom())problems.add(problem("PROJECT_SETTINGS_INVALID",new TextSpan(0,1),"bom"));
        if(source.text().getBytes(StandardCharsets.UTF_8).length>262_144)return new SettingsDocument(source,List.of(),List.of(),List.of(problem("PROJECT_SETTINGS_LIMIT",new TextSpan(0,source.text().length()),"size")),false);
        var tokens=new GroovyTokenReader().lex(source,ParseOptions.DEFAULT,cancellation);
        if(tokens.budgetExceeded())return new SettingsDocument(source,List.of(),List.of(),tokens.problems().stream().map(p->problem("PROJECT_SETTINGS_LIMIT",p.span(),"budget")).toList(),false);
        var parsed=new SafeGroovySyntaxReader().readModule(source,cancellation);var syntax=new ParseResultMerger().problems(tokens.problems(),parsed.problems());
        for(var p:syntax)problems.add(problem(p.code().equals("PARSE_LIMIT")?"PROJECT_SETTINGS_LIMIT":"PROJECT_SETTINGS_INVALID",p.span(),"syntax"));
        var coordinates=new ParserCoordinateMapper(source);var seen=new HashSet<String>();var directives=new LinkedHashMap<String,TextSpan>();
        if(parsed.module()==null){
            for(var declaration:new RecoveringDslParser(source,tokens,cancellation).recover().declarations()){
                String name=declaration.kind()==DeclarationKind.INCLUDE?"include":declaration.kind()==DeclarationKind.DEFAULTS?"defaults":null;
                if(name==null||declaration.body()!=null||!declaration.options().isEmpty())continue;
                var values=new ArrayList<LiteralValue>();for(var value:declaration.targets())if(value instanceof LiteralValue literal&&simple(literal.literal()))values.add(literal);
                directives.putIfAbsent(name,declaration.span());accept(name,declaration.span(),values,seen,includes,defaults,problems);
            }
        }else{
            var module=parsed.module();
            try{new SyntaxDepthGuard(ParseOptions.DEFAULT.maxNesting(),cancellation).check(module);}catch(SyntaxDepthGuard.Limit exceeded){return new SettingsDocument(source,List.of(),List.of(),List.of(problem("PROJECT_SETTINGS_LIMIT",span(exceeded.node,coordinates),"depth")),false);}
            if(module.getPackage()!=null)unsupported(module.getPackage(),coordinates,problems);
            module.getImports().forEach(n->unsupported(n,coordinates,problems));module.getStarImports().forEach(n->unsupported(n,coordinates,problems));
            module.getStaticImports().values().forEach(n->unsupported(n,coordinates,problems));module.getStaticStarImports().values().forEach(n->unsupported(n,coordinates,problems));
            if(!module.getModuleStarImports().isEmpty())problems.add(problem("PROJECT_SETTINGS_UNSUPPORTED",tokens.all().stream().filter(t->t.type()==GroovyLexer.IMPORT).map(TokenIndex.LexToken::span).findFirst().orElse(null),"import"));
            module.getMethods().forEach(n->unsupported(n,coordinates,problems));
            for(var type:module.getClasses()){if(!type.isScript())unsupported(type,coordinates,problems);type.getAnnotations().forEach(n->unsupported(n,coordinates,problems));}
            for(var statement:module.getStatementBlock().getStatements()){
                cancellation.check();if(statement==ReturnStatement.RETURN_NULL_OR_VOID)continue;
                if(!(statement instanceof ExpressionStatement expression)||(statement.getStatementLabels()!=null&&!statement.getStatementLabels().isEmpty())||!(expression.getExpression() instanceof MethodCallExpression call)||!call.isImplicitThis()||call.isSafe()||call.isSpreadSafe()||call.isUsingGenerics()||!(call.getMethod() instanceof ConstantExpression method)||!(method.getValue() instanceof String name)||!(call.getArguments() instanceof ArgumentListExpression arguments)||!(name.equals("include")||name.equals("defaults"))){unsupported(statement,coordinates,problems);continue;}
                var values=new ArrayList<LiteralValue>();boolean supported=true;
                for(var argument:arguments.getExpressions()){
                    if(!(argument instanceof ConstantExpression constant)||!(constant.getValue() instanceof String text)){unsupported(argument,coordinates,problems);supported=false;continue;}
                    var envelope=firstQuotedLiteral(span(argument,coordinates),tokens,source);
                    if(envelope==null){unsupported(argument,coordinates,problems);supported=false;continue;}
                    values.add(new LiteralValue(text,span(argument,coordinates),envelope));
                }
                if(supported){directives.putIfAbsent(name,span(call,coordinates));accept(name,span(call,coordinates),values,seen,includes,defaults,problems);}
            }
        }
        var distinct=new LinkedHashMap<String,SettingsProblem>();for(var p:problems)distinct.putIfAbsent(p.code()+"|"+p.span(),p);
        return new SettingsDocument(source,List.copyOf(includes.values()),List.copyOf(defaults.values()),List.copyOf(distinct.values()),problems.isEmpty()&&parsed.module()!=null,tokens,directives);
    }
    private static void accept(String directive,TextSpan call,List<LiteralValue> values,Set<String> seen,Map<String,SettingName> includes,Map<String,SettingName> defaults,List<SettingsProblem> problems){
        if(!seen.add(directive)){problems.add(problem("PROJECT_SETTINGS_INVALID",call,directive));return;}
        boolean include=directive.equals("include");if(values.isEmpty()){problems.add(problem(include?"PROJECT_INCLUDE_INVALID":"PROJECT_TARGET_INVALID",call,directive));return;}
        for(var value:values){
            if(!(value.value() instanceof String text)||!simple(value.literal())){problems.add(problem("PROJECT_SETTINGS_UNSUPPORTED",value.span(),directive));continue;}
            String canonical;
            try{var name=include?ProjectNameRules.parseInclude(text):ProjectNameRules.parseQualified(text);if(!include&&name.isRoot())throw new IllegalArgumentException("target required");canonical=name.canonical();}
            catch(IllegalArgumentException invalid){problems.add(problem(include?"PROJECT_INCLUDE_INVALID":"PROJECT_TARGET_INVALID",value.span(),directive));continue;}
            var item=new SettingName(text,canonical,value.literal());var target=include?includes:defaults;var old=target.putIfAbsent(canonical,item);
            if(old!=null)problems.add(new SettingsProblem(include?"PROJECT_INCLUDE_DUPLICATE":"DUPLICATE_DEFAULT_TARGET",value.literal().token(),directive,List.of(old.literal().token())));
        }
    }
    private static StringLiteralSpan firstQuotedLiteral(TextSpan node,TokenIndex tokens,SourceText source){
        if(node==null)return null;
        for(var token:tokens.within(node)){
            if(token.channel()==TokenIndex.Channel.WHITESPACE||token.channel()==TokenIndex.Channel.COMMENT||token.type()==GroovyLexer.LPAREN)continue;
            if(token.type()!=GroovyLexer.StringLiteral)return null;
            try{var literal=LiteralDecoder.decode(source,token.span()).span();return simple(literal)?literal:null;}catch(IllegalArgumentException unsupported){return null;}
        }
        return null;
    }
    private static boolean simple(StringLiteralSpan literal){return literal!=null&&(literal.quotes()==QuoteKind.SINGLE||literal.quotes()==QuoteKind.DOUBLE);}
    private static TextSpan span(ASTNode node,ParserCoordinateMapper coordinates){return node==null||node.getLineNumber()<1?null:coordinates.parserSpan(node.getLineNumber(),node.getColumnNumber(),node.getLastLineNumber(),node.getLastColumnNumber());}
    private static void unsupported(ASTNode node,ParserCoordinateMapper coordinates,List<SettingsProblem> problems){problems.add(problem("PROJECT_SETTINGS_UNSUPPORTED",span(node,coordinates),"unsupported"));}
    private static SettingsProblem problem(String code,TextSpan span,String directive){return new SettingsProblem(code,span,directive,List.of());}
}
