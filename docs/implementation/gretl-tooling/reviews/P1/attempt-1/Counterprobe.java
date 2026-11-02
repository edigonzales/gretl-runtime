import ch.so.agi.gretl.tooling.syntax.*;
import ch.so.agi.gretl.tooling.source.*;
import static ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import java.util.*;

/** Independent P1 counterprobes, run against the built candidate classes. */
public class Counterprobe {
  static ParsedDocument parse(String text){ return new GretlDocumentParser().parse(SourceText.job(text)); }
  static Declaration byName(ParsedDocument d,String name){ return d.declarations().stream().filter(x->name.equals(x.name())).findFirst().orElseThrow(()->new AssertionError("missing "+name+" in "+d.declarations())); }
  static String slice(ParsedDocument d,TextSpan s){ return d.source().text().substring(s.startUtf16(),s.endUtf16()); }
  static void check(boolean condition,String message){ if(!condition) throw new AssertionError(message); }
  public static void main(String[] args){
    int failures=0;
    // 1. Mixed safe/unsafe islands: actual spans, not just task count.
    try {
      String text="// task('phantom', Echo) { input='phantom' }\ndef safe = 1\ntask('before', Echo) { input='a' }\ndef broken = ?\naction('after') { def x = \"unterminated\n";
      ParsedDocument d=parse(text);
      check(d.declarations().stream().noneMatch(x->"phantom".equals(x.name())),"phantom declaration");
      Declaration before=byName(d,"before");
      check(text.indexOf("task('before'")==before.span().startUtf16(),"before start");
      check("'before'".equals(slice(d,before.nameLiteral().token())),"before token");
      check("'a'".equals(slice(d,before.fields().getFirst().value().span())),"before field span");
      Declaration after=byName(d,"after");
      check(text.indexOf("action('after'")==after.span().startUtf16(),"after start");
      check(text.indexOf("action('after'") == after.header().startUtf16(),"after header");
      check(after.recovered(),"after should be recovered");
      check(d.coverage().syntax()==Completeness.PARTIAL,"mixed syntax coverage");
      check(!d.problems().isEmpty(),"mixed problems");
      System.out.println("PASS mixed-safe-unsafe-islands spans, phantom suppression, partial syntax");
    } catch(Throwable t){ failures++; System.out.println("FAIL mixed-safe-unsafe-islands: "+t); }
    // 2. Foreign receiver inside a task option closure must reduce reference coverage.
    try {
      ParsedDocument d=parse("task('a', Echo, when: { c -> def tool = new StringBuilder(); tool.append('x'); true }) { input='x' }");
      Declaration a=byName(d,"a");
      check(a.coverage().references()==Completeness.PARTIAL,"references must be PARTIAL, actual "+a.coverage().references());
      System.out.println("PASS when-option foreign receiver coverage PARTIAL");
    } catch(Throwable t){ failures++; System.out.println("FAIL when-option foreign receiver coverage: "+t); }
    // 3. Inner ctx.run in a task option closure is an inner invocation, not a DAG task.
    try {
      ParsedDocument d=parse("task('a', Echo, when: { c -> c.run(Echo) { input='y' } }) { input='x' }");
      Declaration a=byName(d,"a");
      check(a.invocations().size()==1,"expected one inner invocation, actual "+a.invocations().size());
      System.out.println("PASS when-option inner ctx.run modeled");
    } catch(Throwable t){ failures++; System.out.println("FAIL when-option inner ctx.run modeled: "+t); }
    // 4. Unterminated huge GString must hit the documented literal budget.
    try {
      String text="def s = \"${"+"x".repeat(70000);
      ParsedDocument d=parse(text);
      check(d.problems().stream().anyMatch(p->p.code().equals("PARSE_LIMIT")),"expected PARSE_LIMIT for huge unterminated GString, actual "+d.problems());
      System.out.println("PASS huge unterminated GString PARSE_LIMIT");
    } catch(Throwable t){ failures++; System.out.println("FAIL huge unterminated GString PARSE_LIMIT: "+t); }
    System.out.println("failures="+failures);
    if(failures>0) System.exit(1);
  }
}
