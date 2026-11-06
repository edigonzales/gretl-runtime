package ch.so.agi.gretl.tooling.query;

import ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace;
import ch.so.agi.gretl.tooling.query.LanguageQueries;
import ch.so.agi.gretl.tooling.query.QueryContext;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace.*;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotNavigationTest {
    @Test void navigationHoverOutlineFoldingAndLinksShareTheActualProjectCapture() {
        try (var fixture = new EditTestWorkspace()) {
            URI producer = URI.create("file:/work/a/job.groovy"), consumer = URI.create("file:/work/b/job.groovy");
            fixture.files.put(SETTINGS, "include 'a','b'\ndefaults ':b:use'");
            String source = "/* preserved\n comment */\ntask('source', Echo) {\n  input=file('data.txt')\n}\n";
            String use = "action('use', dependsOn:':a:source') { result(':a:source') }";
            fixture.files.put(producer, source);
            fixture.files.put(consumer, use);
            var snapshot = fixture.capture();
            var queries = new LanguageQueries();
            int position = use.indexOf("result(':a:source')") + "result('".length();
            var context = new QueryContext(snapshot, consumer, position);
            var definitions = queries.definitions(context);
            assertEquals(1, definitions.size());
            assertEquals(producer, definitions.getFirst().documentId());
            assertEquals("source", snapshot.source(producer).orElseThrow().slice(definitions.getFirst().span()));
            var hover = queries.hover(context).orElseThrow();
            assertEquals(consumer, hover.anchor().documentId());
            assertEquals(":a:source", context.source().slice(hover.anchor().span()));
            var references = queries.references(context, true);
            assertTrue(references.complete());
            assertEquals(3, references.locations().size());
            var producerContext = new QueryContext(snapshot, producer, 0);
            assertEquals(List.of("source"), queries.documentSymbols(producerContext).stream().map(n -> n.name()).toList());
            assertEquals(List.of("comment", "region"), queries.foldingRanges(producerContext).stream().map(r -> r.kind()).toList());
            assertEquals(List.of(URI.create("file:/work/a/data.txt")), queries.documentLinks(producerContext).stream().map(l -> l.target()).toList());
            assertEquals(List.of("include", "defaults"), queries.documentSymbols(new QueryContext(snapshot, SETTINGS, 0)).stream().map(n -> n.name()).toList());
            assertThrows(IllegalArgumentException.class, () -> new QueryContext(snapshot, consumer, use.length() + 1));
        }
    }

    @Test void ambiguousDefinitionsRemainMultipleAndReferenceCoverageIsNotClaimedComplete() {
        try (var fixture = new EditTestWorkspace()) {
            String text = "task('a', Echo) { input='one' }; task('a', Echo) { input='two' }; action('use', dependsOn:'a') {}";
            fixture.files.put(JOB, text);
            var snapshot = fixture.capture();
            var context = new QueryContext(snapshot, JOB, text.lastIndexOf("'a'") + 1);
            var queries = new LanguageQueries();
            assertEquals(2, queries.definitions(context).size());
            assertFalse(queries.references(context, true).complete());
            assertTrue(queries.hover(context).isEmpty());
        }
    }
}
