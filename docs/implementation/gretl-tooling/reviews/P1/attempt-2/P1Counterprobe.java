import ch.so.agi.gretl.tooling.source.*;
import ch.so.agi.gretl.tooling.syntax.*;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import java.nio.file.*;
import java.util.*;

public final class P1Counterprobe {
    static int checks = 0;
    static final List<String> failures = new ArrayList<>();
    static void check(boolean condition, String message) {
        checks++;
        if (!condition) failures.add(message);
    }
    static Declaration named(ParsedDocument doc, String name) {
        return doc.declarations().stream().filter(d -> name.equals(d.name())).findFirst().orElse(null);
    }
    static String slice(ParsedDocument doc, TextSpan span) { return doc.source().slice(span); }

    public static void main(String[] args) throws Exception {
        Path marker = Path.of(args[0]);
        Files.deleteIfExists(marker);
        String markerText = marker.toAbsolutePath().toString().replace("\\", "\\\\").replace("'", "\\'");

        String mixed = "new File('" + markerText + "').text = 'pwn'\n"
            + "// task('phantomComment', Echo) { output = '}' }\n"
            + "action('before') { def s = \"😀 ${parameter('p')} // task('phantomGString', Echo) { }\" }\n"
            + "def broken = ?\n"
            + "task('after', Echo, when: { c -> c.run(Echo) { input = result('before').file } }) {\n"
            + "    input = 'ok'\n"
            + "    output = $/C:\\temp\\fake/$\n"
            + "}\n"
            + "/* unfinished task('phantomBlock', Echo) {}\n";
        ParsedDocument doc = new GretlDocumentParser().parse(SourceText.job(mixed));
        check(!Files.exists(marker), "MIXED: parser executed top-level file write");
        check(named(doc, "before") != null, "MIXED: safe action before error missing");
        check(named(doc, "after") != null, "MIXED: safe task after error missing");
        check(named(doc, "phantomComment") == null, "MIXED: comment phantom declaration");
        check(named(doc, "phantomGString") == null, "MIXED: GString phantom declaration");
        check(named(doc, "phantomBlock") == null, "MIXED: block-comment phantom declaration");
        Declaration before = named(doc, "before");
        Declaration after = named(doc, "after");
        if (before != null) {
            check(before.recovered(), "MIXED: before not recovered");
            check(slice(doc, before.nameLiteral().token()).equals("'before'"), "MIXED: before name span");
            check(before.references().stream().anyMatch(r -> r.name().equals("parameter")
                && r.arguments().size() == 1
                && r.arguments().get(0) instanceof LiteralValue l
                && "p".equals(l.value())), "MIXED: parameter reference in GString action");
        }
        if (after != null) {
            check(after.recovered(), "MIXED: after not recovered");
            check(slice(doc, after.nameLiteral().token()).equals("'after'"), "MIXED: after name span");
            check(after.invocations().size() == 1, "MIXED: expected one inner invocation, got " + after.invocations().size());
            check(after.fields().stream().map(FieldAssignment::name).toList().equals(List.of("input", "output")),
                "MIXED: after fields " + after.fields().stream().map(FieldAssignment::name).toList());
            if (after.fields().size() == 2) {
                check(slice(doc, after.fields().get(0).value().span()).equals("'ok'"), "MIXED: input value span");
                check(after.fields().get(1).value() instanceof LiteralValue l && "C:\\temp\\fake".equals(l.value()),
                    "MIXED: dollar-slashy value");
            }
            if (after.invocations().size() == 1) {
                InnerInvocation inv = after.invocations().get(0);
                check(slice(doc, inv.span()).startsWith("c.run(Echo)"), "MIXED: invocation span " + slice(doc, inv.span()));
                check(inv.fields().size() == 1 && "input".equals(inv.fields().get(0).name()),
                    "MIXED: invocation fields " + inv.fields());
                if (inv.fields().size() == 1) {
                    ValueExpression v = inv.fields().get(0).value();
                    check(v instanceof PropertyValue p && "file".equals(p.property()),
                        "MIXED: invocation result property " + v);
                    if (v instanceof PropertyValue p) {
                        check(p.receiver() instanceof CallValue c && "result".equals(c.name())
                            && c.arguments().size() == 1
                            && c.arguments().get(0) instanceof LiteralValue l && "before".equals(l.value()),
                            "MIXED: invocation result receiver");
                    }
                }
            }
        }
        String rebuilt = doc.tokens().all().stream().map(TokenIndex.LexToken::text).reduce("", String::concat);
        check(rebuilt.equals(mixed), "MIXED: token ledger does not reconstruct original text");
        check(!doc.problems().isEmpty(), "MIXED: syntax problems disappeared");
        check(doc.problems().stream().map(p -> p.code() + p.span()).distinct().count() == doc.problems().size(),
            "MIXED: duplicate syntax problems");
        check(doc.coverage().syntax() == Completeness.PARTIAL, "MIXED: syntax coverage not PARTIAL");

        String badGString = "action('safe') { def x = 1 }\n"
            + "def g = \"prefix ${1} // task('phantom', Echo) {} 😀😀😀";
        ParsedDocument g = new GretlDocumentParser().parse(SourceText.job(badGString));
        check(named(g, "safe") != null, "GSTRING: safe island missing");
        check(named(g, "phantom") == null, "GSTRING: phantom task from string");
        check(!g.problems().isEmpty(), "GSTRING: diagnostics missing");
        String rebuiltG = g.tokens().all().stream().map(TokenIndex.LexToken::text).reduce("", String::concat);
        check(rebuiltG.equals(badGString), "GSTRING: token ledger does not reconstruct original text");
        check(g.coverage().syntax() == Completeness.PARTIAL, "GSTRING: syntax coverage not PARTIAL");

        String exec = "new File('" + markerText + "').text = 'pwn'\n"
            + "class Init { static { new File('" + markerText + "').text = 'pwn2' } }\n"
            + "new Init()\naction('valid') {}\n";
        Files.deleteIfExists(marker);
        ParsedDocument ex = new GretlDocumentParser().parse(SourceText.job(exec));
        check(!Files.exists(marker), "EXEC: parser executed top-level/static initializer");
        check(named(ex, "valid") != null, "EXEC: valid declaration missing");

        System.out.println("checks=" + checks + " failures=" + failures.size());
        for (String f : failures) System.out.println("FAIL " + f);
        if (!failures.isEmpty()) System.exit(1);
        System.out.println("P1_COUNTERPROBE_PASS");
    }
}
