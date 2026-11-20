package ch.so.agi.gretl.tooling.syntax;

import ch.so.agi.gretl.language.ProjectNameRules;
import ch.so.agi.gretl.syntax.SettingsAstInspector;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.*;
import groovyjarjarantlr4.v4.runtime.Token;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.codehaus.groovy.ast.ASTNode;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;

/** Static settings analysis, with source spans and recovery for incomplete editor buffers. */
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
        var astTokens = new ArrayList<Token>();
        var tokens=new GroovyTokenReader().lex(source,ParseOptions.DEFAULT,cancellation,astTokens::add);
        if(tokens.budgetExceeded())return new SettingsDocument(source,List.of(),List.of(),tokens.problems().stream().map(p->problem("PROJECT_SETTINGS_LIMIT",p.span(),"budget")).toList(),false);
        var parsed=new SafeGroovySyntaxReader().readModule(source,cancellation);var syntax=new ParseResultMerger().problems(tokens.problems(),parsed.problems());
        for(var p:syntax)problems.add(problem(p.code().equals("PARSE_LIMIT")?"PROJECT_SETTINGS_LIMIT":"PROJECT_SETTINGS_INVALID",p.span(),"syntax"));
        var coordinates=new ParserCoordinateMapper(source);var directives=new LinkedHashMap<String,TextSpan>();
        if(parsed.module()==null){
            for(var declaration:new RecoveringDslParser(source,tokens,cancellation).recover().declarations()){
                String name=declaration.kind()==DeclarationKind.INCLUDE?"include":declaration.kind()==DeclarationKind.DEFAULTS?"defaults":null;
                if(name==null||declaration.body()!=null||!declaration.options().isEmpty())continue;
                var values=new ArrayList<LiteralValue>();for(var value:declaration.targets())if(value instanceof LiteralValue literal&&literal.value() instanceof String&&simple(literal.literal()))values.add(literal);
                boolean repeated = directives.putIfAbsent(name,declaration.span()) != null;
                accept(name,declaration.span(),values,repeated,includes,defaults,problems);
            }
        }else{
            var module=parsed.module();
            try{new SyntaxDepthGuard(ParseOptions.DEFAULT.maxNesting(),cancellation).check(module);}catch(SyntaxDepthGuard.Limit exceeded){return new SettingsDocument(source,List.of(),List.of(),List.of(problem("PROJECT_SETTINGS_LIMIT",span(exceeded.node,coordinates),"depth")),false);}
            for (var finding : new SettingsAstInspector().inspect(module, astTokens, cancellation::check)) {
                if (finding instanceof SettingsAstInspector.Unsupported unsupported) {
                    var token = unsupported.importToken();
                    if (token == null) unsupported(unsupported.node(), coordinates, problems);
                    else problems.add(problem("PROJECT_SETTINGS_UNSUPPORTED", tokenSpan(token, coordinates), "import"));
                    continue;
                }
                var directive = (SettingsAstInspector.Directive) finding;
                var values = new ArrayList<LiteralValue>();
                for (var argument : directive.arguments()) {
                    if (argument.literal() == null) {
                        unsupported(argument.node(), coordinates, problems);
                        continue;
                    }
                    var literal = LiteralDecoder.decode(source, tokenSpan(argument.literal(), coordinates)).span();
                    values.add(new LiteralValue(argument.value(), span(argument.node(), coordinates), literal));
                }
                if (values.size() == directive.arguments().size()) {
                    var call = span(directive.node(), coordinates);
                    directives.putIfAbsent(directive.name(), call);
                    accept(directive.name(), call, values, directive.repeated(),
                            includes, defaults, problems);
                }
            }
        }
        var distinct=new LinkedHashMap<String,SettingsProblem>();for(var p:problems)distinct.putIfAbsent(p.code()+"|"+p.span(),p);
        return new SettingsDocument(source,List.copyOf(includes.values()),List.copyOf(defaults.values()),List.copyOf(distinct.values()),problems.isEmpty()&&parsed.module()!=null,tokens,directives);
    }
    private static void accept(String directive,TextSpan call,List<LiteralValue> values,boolean repeated,Map<String,SettingName> includes,Map<String,SettingName> defaults,List<SettingsProblem> problems){
        if(repeated){problems.add(problem("PROJECT_SETTINGS_INVALID",call,directive));return;}
        boolean include=directive.equals("include");
        if(values.isEmpty()){problems.add(problem(include?"PROJECT_INCLUDE_INVALID":"PROJECT_TARGET_INVALID",call,directive));return;}
        for(var value:values){
            String text = (String) value.value();
            String canonical;
            try{var name=include?ProjectNameRules.parseInclude(text):ProjectNameRules.parseQualified(text);if(!include&&name.isRoot())throw new IllegalArgumentException("target required");canonical=name.canonical();}
            catch(IllegalArgumentException invalid){problems.add(problem(include?"PROJECT_INCLUDE_INVALID":"PROJECT_TARGET_INVALID",value.span(),directive));continue;}
            var item=new SettingName(text,canonical,value.literal());var target=include?includes:defaults;var old=target.putIfAbsent(canonical,item);
            if(old!=null)problems.add(new SettingsProblem(include?"PROJECT_INCLUDE_DUPLICATE":"DUPLICATE_DEFAULT_TARGET",value.literal().token(),directive,List.of(old.literal().token())));
        }
    }
    private static TextSpan tokenSpan(Token token, ParserCoordinateMapper coordinates) {
        return new TextSpan(coordinates.lexerOffset(token.getStartIndex()),
                coordinates.lexerOffset(token.getStopIndex() + 1));
    }
    private static boolean simple(StringLiteralSpan literal){return literal!=null&&(literal.quotes()==QuoteKind.SINGLE||literal.quotes()==QuoteKind.DOUBLE);}
    private static TextSpan span(ASTNode node,ParserCoordinateMapper coordinates){return node==null||node.getLineNumber()<1?null:coordinates.parserSpan(node.getLineNumber(),node.getColumnNumber(),node.getLastLineNumber(),node.getLastColumnNumber());}
    private static void unsupported(ASTNode node,ParserCoordinateMapper coordinates,List<SettingsProblem> problems){problems.add(problem("PROJECT_SETTINGS_UNSUPPORTED",span(node,coordinates),"unsupported"));}
    private static SettingsProblem problem(String code,TextSpan span,String directive){return new SettingsProblem(code,span,directive,List.of());}
}
