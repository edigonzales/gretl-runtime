package ch.so.agi.gretl.tooling.syntax;

import ch.so.agi.gretl.tooling.source.*;
import java.util.*;

/** Neutral syntax values; Groovy ASTs and executable objects never leave the parsing boundary. */
public final class SyntaxModel {
    private SyntaxModel() { }
    public enum Completeness { COMPLETE, PARTIAL, UNKNOWN }
    public record Coverage(Completeness declarations,Completeness references,Completeness fieldAssignments,Completeness syntax) {
        public static Coverage complete() { return new Coverage(Completeness.COMPLETE,Completeness.COMPLETE,Completeness.COMPLETE,Completeness.COMPLETE); }
        public static Coverage partial() { return new Coverage(Completeness.PARTIAL,Completeness.PARTIAL,Completeness.PARTIAL,Completeness.PARTIAL); }
    }
    public record SyntaxProblem(String code,TextSpan span,String message) { }
    public enum QuoteKind { SINGLE, DOUBLE, TRIPLE_SINGLE, TRIPLE_DOUBLE, SLASHY, DOLLAR_SLASHY }
    public record StringLiteralSpan(TextSpan token,TextSpan content,QuoteKind quotes,List<Integer> decodedToSource) {
        public StringLiteralSpan { decodedToSource=List.copyOf(decodedToSource); }
    }
    public sealed interface ValueExpression permits LiteralValue,ListValue,MapValue,CallValue,PropertyValue,NameValue,OpaqueExpression {
        TextSpan span();
    }
    public record LiteralValue(Object value,TextSpan span,StringLiteralSpan literal) implements ValueExpression {
        public LiteralValue {
            Objects.requireNonNull(span);
            if(value!=null&&!Set.of(String.class,Boolean.class,Character.class,Byte.class,Short.class,Integer.class,Long.class,Float.class,Double.class,java.math.BigInteger.class,java.math.BigDecimal.class).contains(value.getClass()))
                throw new IllegalArgumentException("Syntax literals must be immutable primitive values");
        }
    }
    public record ListValue(List<ValueExpression> items,TextSpan span) implements ValueExpression { public ListValue { items=List.copyOf(items); } }
    public record MapEntry(ValueExpression key,ValueExpression value,TextSpan span) { }
    public record MapValue(List<MapEntry> entries,TextSpan span) implements ValueExpression { public MapValue { entries=List.copyOf(entries); } }
    /** Recognized safe helper syntax, never an invocation. Receiver remains explicit for binding. */
    public record CallValue(String name,String receiver,List<ValueExpression> arguments,TextSpan span) implements ValueExpression { public CallValue { arguments=List.copyOf(arguments); } }
    public record PropertyValue(ValueExpression receiver,String property,TextSpan propertySpan,TextSpan span) implements ValueExpression { }
    public record NameValue(String name,TextSpan span) implements ValueExpression { }
    public record OpaqueExpression(TextSpan span,String reason) implements ValueExpression { }
    public record FieldAssignment(String name,TextSpan nameSpan,ValueExpression value,TextSpan span) { }
    public record LocalVariable(String name,TextSpan span) { }
    public record InnerInvocation(ValueExpression operation,TextSpan span,TextSpan body,List<FieldAssignment> fields,Coverage coverage) {
        public InnerInvocation { fields=List.copyOf(fields); }
    }
    public enum DeclarationKind { TASK, ACTION, PARAMETER, DEFAULTS, CLEANUP, INCLUDE }
    public record Declaration(DeclarationKind kind,String name,StringLiteralSpan nameLiteral,String bindingName,TextSpan bindingSpan,ValueExpression operation,
            List<MapEntry> options,List<ValueExpression> targets,TextSpan statementSpan,TextSpan span,TextSpan header,TextSpan body,List<FieldAssignment> fields,
            List<LocalVariable> locals,List<InnerInvocation> invocations,List<CallValue> references,List<PropertyValue> properties,Coverage coverage,boolean recovered) {
        public Declaration { options=List.copyOf(options);targets=List.copyOf(targets);fields=List.copyOf(fields);locals=List.copyOf(locals);invocations=List.copyOf(invocations);references=List.copyOf(references);properties=List.copyOf(properties); }
    }
    public record OpaqueStatement(TextSpan span,String reason,boolean mayRegisterDeclarations) { }
    public enum DocumentKind { JOB, SETTINGS, GROOVY_SOURCE, SQL }
    public record ParsedDocument(SourceText source,DocumentKind kind,TokenIndex tokens,List<Declaration> declarations,
            List<OpaqueStatement> opaque,List<SyntaxProblem> problems,Coverage coverage) {
        public ParsedDocument { declarations=List.copyOf(declarations);opaque=List.copyOf(opaque);problems=List.copyOf(problems); }
    }
}
