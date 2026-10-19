package ch.so.agi.gretl.tasks.xslt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XslTransformerOperationTest {
    @TempDir
    Path tempDir;

    @Test
    void transformsOneFileWithALocalStylesheet() throws Exception {
        Path input = writeXml("input.xml", "hello");
        Path stylesheet = writeStylesheet("simple.xsl");
        Path output = tempDir.resolve("out");

        XslTransformerOperation.XslTransformerResult result =
                new XslTransformerOperation.XslTransformerHandler().execute(
                        request(List.of(input), output).setXslFile(stylesheet).build(),
                        new XslTestContext(tempDir));

        Path target = output.resolve("input.xtf");
        assertEquals(List.of(target), result.files());
        assertEquals(1, result.count());
        assertTrue(Files.readString(target).contains("<result>hello</result>"));
    }

    @Test
    void transformsMultipleFilesInInputOrder() throws Exception {
        Path first = writeXml("first.xml", "one");
        Path second = writeXml("second.xml", "two");
        Path stylesheet = writeStylesheet("simple.xsl");
        Path output = tempDir.resolve("out");

        XslTransformerOperation.XslTransformerResult result =
                new XslTransformerOperation.XslTransformerHandler().execute(
                        request(List.of(second, first), output)
                                .setXslFile(stylesheet).build(),
                        new XslTestContext(tempDir));

        assertEquals(2, result.count());
        assertEquals(List.of(output.resolve("second.xtf"), output.resolve("first.xtf")),
                result.files());
        assertTrue(Files.readString(output.resolve("first.xtf")).contains("one"));
    }

    @Test
    void usesAClasspathStylesheetAndCustomExtension() throws Exception {
        Path input = writeXml("resource.xml", "resource");
        Path output = tempDir.resolve("out");

        XslTransformerOperation.XslTransformerResult result =
                new XslTransformerOperation.XslTransformerHandler().execute(
                        request(List.of(input), output)
                                .setXslResource("simple.xsl")
                                .setFileExtension(".xml")
                                .build(),
                        new XslTestContext(tempDir));

        assertEquals(List.of(output.resolve("resource.xml")), result.files());
        assertTrue(Files.readString(output.resolve("resource.xml")).contains("resource"));
    }

    @Test
    void validatesStylesheetAndInputConfiguration() throws Exception {
        Path input = writeXml("input.xml", "x");
        Path stylesheet = writeStylesheet("simple.xsl");
        Path output = tempDir.resolve("out");

        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(input), output).build());
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(input), output)
                        .setXslFile(stylesheet)
                        .setXslResource("simple.xsl")
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(), output).setXslFile(stylesheet).build());
        assertThrows(IllegalArgumentException.class,
                () -> request(List.of(input), output)
                        .setXslFile(stylesheet)
                        .setFileExtension("   ")
                        .build());
    }

    @Test
    void rejectsMissingInputAndOutputCollisions() throws Exception {
        Path stylesheet = writeStylesheet("simple.xsl");
        Path output = tempDir.resolve("out");

        XsltTaskException missing = assertThrows(XsltTaskException.class,
                () -> new XslTransformerOperation.XslTransformerHandler().execute(
                        request(List.of(tempDir.resolve("missing.xml")), output)
                                .setXslFile(stylesheet).build(),
                        new XslTestContext(tempDir)));
        assertEquals("XSLT_INPUT", missing.code());

        Path a = Files.createDirectories(tempDir.resolve("a"));
        Path b = Files.createDirectories(tempDir.resolve("b"));
        Path first = a.resolve("same.xml");
        Path second = b.resolve("same.xml");
        Files.writeString(first, xml("one"));
        Files.writeString(second, xml("two"));
        XsltTaskException collision = assertThrows(XsltTaskException.class,
                () -> new XslTransformerOperation.XslTransformerHandler().execute(
                        request(List.of(first, second), output)
                                .setXslFile(stylesheet).build(),
                        new XslTestContext(tempDir)));
        assertEquals("XSLT_CONFIG", collision.code());
    }

    @Test
    void honorsOverwriteForExistingOutput() throws Exception {
        Path input = writeXml("input.xml", "new");
        Path stylesheet = writeStylesheet("simple.xsl");
        Path output = tempDir.resolve("out");
        Files.createDirectories(output);
        Path target = output.resolve("input.xtf");
        Files.writeString(target, "old");

        XsltTaskException exists = assertThrows(XsltTaskException.class,
                () -> new XslTransformerOperation.XslTransformerHandler().execute(
                        request(List.of(input), output).setXslFile(stylesheet)
                                .setOverwrite(false).build(),
                        new XslTestContext(tempDir)));
        assertEquals("XSLT_TARGET_EXISTS", exists.code());

        new XslTransformerOperation.XslTransformerHandler().execute(
                request(List.of(input), output).setXslFile(stylesheet)
                        .setOverwrite(true).build(),
                new XslTestContext(tempDir));
        assertTrue(Files.readString(target).contains("new"));
    }

    @Test
    void rejectsInvalidXmlAndExternalEntities() throws Exception {
        Path stylesheet = writeStylesheet("simple.xsl");
        Path invalid = tempDir.resolve("invalid.xml");
        Files.writeString(invalid, "<root>");
        Path output = tempDir.resolve("out");

        XsltTaskException invalidXml = assertThrows(XsltTaskException.class,
                () -> new XslTransformerOperation.XslTransformerHandler().execute(
                        request(List.of(invalid), output).setXslFile(stylesheet).build(),
                        new XslTestContext(tempDir)));
        assertEquals("XSLT_TRANSFORM", invalidXml.code());

        Path xxe = tempDir.resolve("xxe.xml");
        Files.writeString(xxe, """
                <?xml version="1.0"?>
                <!DOCTYPE root [<!ENTITY xxe SYSTEM "http://127.0.0.1:9/xxe">]>
                <root><value>&xxe;</value></root>
                """);
        XsltTaskException external = assertThrows(XsltTaskException.class,
                () -> new XslTransformerOperation.XslTransformerHandler().execute(
                        request(List.of(xxe), output).setXslFile(stylesheet).build(),
                        new XslTestContext(tempDir)));
        assertEquals("XSLT_TRANSFORM", external.code());
    }

    @Test
    void supportsLocalStylesheetIncludes() throws Exception {
        Path included = tempDir.resolve("included.xsl");
        Files.writeString(included, """
                <xsl:stylesheet version="2.0"
                                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="value">
                    <included><xsl:value-of select="."/></included>
                  </xsl:template>
                </xsl:stylesheet>
                """);
        Path stylesheet = tempDir.resolve("include-main.xsl");
        Files.writeString(stylesheet, """
                <xsl:stylesheet version="2.0"
                                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:include href="included.xsl"/>
                  <xsl:template match="/">
                    <result><xsl:apply-templates select="/root/value"/></result>
                  </xsl:template>
                </xsl:stylesheet>
                """);
        Path input = writeXml("included.xml", "included");
        Path output = tempDir.resolve("out");

        new XslTransformerOperation.XslTransformerHandler().execute(
                request(List.of(input), output).setXslFile(stylesheet).build(),
                new XslTestContext(tempDir));

        assertTrue(Files.readString(output.resolve("included.xtf"))
                .contains("<included>included</included>"));
    }

    @Test
    void metadataCarriesFieldsDefaultsAndOutputs() {
        var type = XslTransformerOperation.type();
        assertTrue(type.inputs().stream().anyMatch(field -> field.name().equals("xmlFiles")
                && field.required()));
        assertTrue(type.inputs().stream().anyMatch(field -> field.name().equals("outDirectory")
                && field.required()));
        assertEquals("xtf", type.inputs().stream()
                .filter(field -> field.name().equals("fileExtension"))
                .findFirst().orElseThrow().defaultValue().orElseThrow());
        assertEquals(true, type.inputs().stream()
                .filter(field -> field.name().equals("overwrite"))
                .findFirst().orElseThrow().defaultValue().orElseThrow());
        assertTrue(type.outputs().stream().anyMatch(field -> field.name().equals("files")));
        assertTrue(type.outputs().stream().anyMatch(field -> field.name().equals("count")));
        assertTrue(type.example().contains("XslTransformer"));
    }

    private XslTransformerOperation.XslTransformerBuilder request(
            List<Path> xmlFiles, Path outputDirectory) {
        XslTransformerOperation.XslTransformerBuilder builder =
                new XslTransformerOperation.XslTransformerBuilder();
        builder.setXmlFiles(xmlFiles);
        builder.setOutDirectory(outputDirectory);
        return builder;
    }

    private Path writeXml(String name, String value) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, xml(value));
        return path;
    }

    private static String xml(String value) {
        return "<?xml version=\"1.0\"?><root><value>" + value + "</value></root>";
    }

    private Path writeStylesheet(String name) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, """
                <xsl:stylesheet version="2.0"
                                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <result><xsl:value-of select="/root/value"/></result>
                  </xsl:template>
                </xsl:stylesheet>
                """, StandardCharsets.UTF_8);
        return path;
    }
}
