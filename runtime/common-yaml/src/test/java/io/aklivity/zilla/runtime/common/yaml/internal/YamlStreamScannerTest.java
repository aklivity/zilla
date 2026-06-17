/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.common.yaml.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import io.aklivity.zilla.runtime.common.yaml.YamlConfig;

/**
 * Validates the conservative streaming scanner. The scanner only has to be correct for the subset
 * it accepts; everything else falls back to {@link YamlDocumentParser}. So the core guarantee tested
 * here is differential: for every conformance fixture the scanner ACCEPTS, its projected event stream
 * must be byte-identical to the eager parser's. Any divergence means the gate is too loose.
 */
class YamlStreamScannerTest
{
    private static final String SUITE_TAG = "data-2022-01-17";

    private static final String BLOCK_CONFIG = """
        name: example
        bindings:
          tcp0:
            type: tcp
            kind: server
            options:
              host: 0.0.0.0
              port: 7114
            routes:
            - exit: http0
              when:
              - port: 7114
        """;

    @Test
    void shouldAcceptBlockConfig()
    {
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan(BLOCK_CONFIG), "scanner should accept the block config subset");
        assertEquals(eager(BLOCK_CONFIG), scanned(scanner), "scanner events must match eager projection");
    }

    @Test
    void shouldAcceptFlowDocument()
    {
        String doc = """
            {"name":"test","enabled":true,"items":[{"id":1,"name":"a"},{"id":2,"name":"b"}],
             "nested":{"x":1,"y":2},"missing":null}
            """;
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan(doc), "scanner should accept a JSON-style flow document");
        assertEquals(eager(doc), scanned(scanner), "scanner events must match eager projection");
    }

    @Test
    void shouldAcceptFlowSequenceDocument()
    {
        String doc = "[1, two, true, null, {a: 1}]\n";
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan(doc));
        assertEquals(eager(doc), scanned(scanner));
    }

    @Test
    void shouldAcceptSingleLineFlowValueInBlock()
    {
        String doc = "items: [1, two, true, null]\nnested: {a: 1, b: two}\nmatrix: [[1, 2], [3, 4]]\n";
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan(doc), "scanner should accept single-line flow values in a block mapping");
        assertEquals(eager(doc), scanned(scanner));
    }

    @Test
    void shouldAcceptFlowValueInSequenceItem()
    {
        String doc = "routes:\n- [a, b]\n- {exit: http0}\n";
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan(doc));
        assertEquals(eager(doc), scanned(scanner));
    }

    @Test
    void shouldBailOnMultiLineFlowValueInBlock()
    {
        assertFalse(new YamlStreamScanner().scan("items: [1,\n  2, 3]\n"));
    }

    @Test
    void shouldBailOnFlowAnchor()
    {
        assertFalse(new YamlStreamScanner().scan("{a: &x 1, b: *x}\n"));
    }

    @Test
    void shouldAcceptEscapeFreeQuotedScalars()
    {
        String doc = "name: \"quoted value\"\nkind: 'literal'\n\"quoted key\": 7114\nflag: \"true\"\n";
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan(doc), "scanner should accept escape-free quoted scalars");
        assertEquals(eager(doc), scanned(scanner), "scanner events must match eager projection");
    }

    @Test
    void shouldBailOnEscapedQuotedScalar()
    {
        assertFalse(new YamlStreamScanner().scan("name: \"a\\tb\"\n"));
    }

    @Test
    void shouldBailOnSingleQuoteEscape()
    {
        assertFalse(new YamlStreamScanner().scan("name: 'it''s here'\n"));
    }

    @Test
    void shouldBailOnMultiLineQuoted()
    {
        assertFalse(new YamlStreamScanner().scan("name: \"line one\n  line two\"\n"));
    }

    @Test
    void shouldBailOnAnchorsAndMerge()
    {
        assertFalse(new YamlStreamScanner().scan("base: &b\n  host: localhost\nuse:\n  <<: *b\n"));
    }

    @Test
    void shouldAcceptLiteralBlockScalar()
    {
        String doc = "text: |\n  line one\n  line two\n";
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan(doc), "scanner should accept a literal block scalar");
        assertEquals(eager(doc), scanned(scanner), "scanner events must match eager projection");
    }

    @Test
    void shouldAcceptFoldedBlockScalar()
    {
        String doc = "text: >\n  line one\n  line two\n\n  next para\n";
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan(doc), "scanner should accept a folded block scalar");
        assertEquals(eager(doc), scanned(scanner), "scanner events must match eager projection");
    }

    @Test
    void shouldBailOnExplicitIndentBlockScalar()
    {
        assertFalse(new YamlStreamScanner().scan("text: |2\n    line\n"));
    }

    @Test
    void shouldAcceptSingleDocumentMarkers()
    {
        String doc = "---\nname: example\nport: 7114\n...\n";
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan(doc), "scanner should accept a single document with markers");
        assertEquals(eager(doc), scanned(scanner));
    }

    @Test
    void shouldBailOnMultiDocumentStream()
    {
        assertFalse(new YamlStreamScanner().scan("---\nname: one\n---\nname: two\n"));
    }

    @Test
    void shouldBailOnDirectives()
    {
        assertFalse(new YamlStreamScanner().scan("%YAML 1.2\n---\nname: example\n"));
    }

    @Test
    void shouldBailOnTabs()
    {
        assertFalse(new YamlStreamScanner().scan("name:\n\tvalue\n"));
    }

    @Test
    void shouldAcceptExpectedFixtureCount() throws Exception
    {
        long accepted = fixtures()
            .filter(path -> accepts(path.resolve("in.yaml")))
            .count();
        assertEquals(56, accepted,
            "accepted-fixture count changed; feasibility gate may over-reject or over-accept");
    }

    private static boolean accepts(
        Path path)
    {
        try
        {
            return new YamlStreamScanner().scan(Files.readString(path));
        }
        catch (IOException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    @TestFactory
    Stream<DynamicTest> shouldMatchEagerForEveryAcceptedFixture() throws Exception
    {
        return fixtures()
            .map(path -> DynamicTest.dynamicTest(SUITE_DIR.relativize(path).toString(), () ->
            {
                String text = Files.readString(path.resolve("in.yaml"));
                YamlStreamScanner scanner = new YamlStreamScanner();
                if (scanner.scan(text))
                {
                    assertEquals(eager(text), scanned(scanner),
                        "scanner accepted but diverged from eager parser");
                }
            }));
    }

    private static String scanned(
        YamlStreamScanner scanner)
    {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < scanner.count(); index++)
        {
            builder.append(token(scanner.kind(index)));
            CharSequence view = scanner.stringView(index);
            if (view != null)
            {
                builder.append('(').append(view).append(')');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private static String eager(
        String text)
    {
        StringBuilder builder = new StringBuilder();
        project(YamlReferences.resolve(YamlDocumentParser.parse(text).node, Map.of()), builder);
        return builder.toString();
    }

    private static void project(
        YamlNode node,
        StringBuilder builder)
    {
        if (node instanceof YamlObjectNode object)
        {
            builder.append("START_OBJECT").append('\n');
            for (YamlEntry entry : object.entries)
            {
                String name = entry.name != null ? entry.name : ((YamlScalarNode) entry.key).value;
                builder.append("KEY_NAME").append('(').append(name).append(')').append('\n');
                project(entry.value, builder);
            }
            builder.append("END_OBJECT").append('\n');
        }
        else if (node instanceof YamlArrayNode array)
        {
            builder.append("START_ARRAY").append('\n');
            for (YamlNode value : array.values)
            {
                project(value, builder);
            }
            builder.append("END_ARRAY").append('\n');
        }
        else
        {
            YamlScalarNode scalar = (YamlScalarNode) node;
            switch (scalar.type)
            {
            case STRING -> builder.append("VALUE_STRING").append('(').append(scalar.value).append(')');
            case NUMBER -> builder.append("VALUE_NUMBER").append('(').append(scalar.value).append(')');
            case TRUE -> builder.append("VALUE_TRUE");
            case FALSE -> builder.append("VALUE_FALSE");
            case NULL -> builder.append("VALUE_NULL");
            }
            builder.append('\n');
        }
    }

    @Test
    void shouldAcceptAnchorsAliasesMerge()
    {
        String doc = "base: &base\n  host: localhost\nuse:\n  <<: *base\n  port: 7114\nrefs: [*base, plain]\n";
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan(doc, true), "raw scanner should accept anchors/aliases/merge");
        assertEquals(eagerRaw(doc), scannedRaw(scanner));
    }

    @Test
    void shouldAcceptTagsInRawMode()
    {
        String doc = "typed: !!str 42\nverbatim: !<tag:x> hi\ncustom: !foo bar\ntagged: !!map\n  a: 1\n";
        YamlStreamScanner scanner = new YamlStreamScanner();
        assertTrue(scanner.scan(doc, true), "raw scanner should accept tags");
        assertEquals(eagerRaw(doc), scannedRaw(scanner));
    }

    @Test
    void shouldBailOnTagsInNonRawMode()
    {
        assertFalse(new YamlStreamScanner().scan("typed: !!str 42\n"));
    }

    @TestFactory
    Stream<DynamicTest> shouldMatchRawEagerForEveryAcceptedRawFixture() throws Exception
    {
        return fixtures()
            .map(path -> DynamicTest.dynamicTest(SUITE_DIR.relativize(path).toString(), () ->
            {
                String text = Files.readString(path.resolve("in.yaml"));
                YamlStreamScanner scanner = new YamlStreamScanner();
                if (scanner.scan(text, true))
                {
                    assertEquals(eagerRaw(text), scannedRaw(scanner),
                        "raw scanner accepted but diverged from unresolved eager parser");
                }
            }));
    }

    private static String scannedRaw(
        YamlStreamScanner scanner)
    {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < scanner.count(); index++)
        {
            if (scanner.kind(index) == YamlStreamScanner.ALIAS)
            {
                builder.append("ALIAS*").append(scanner.alias(index)).append('\n');
                continue;
            }
            builder.append(token(scanner.kind(index)));
            CharSequence view = scanner.stringView(index);
            if (view != null)
            {
                builder.append('(').append(view).append(')');
            }
            if (scanner.anchor(index) != null)
            {
                builder.append('&').append(scanner.anchor(index));
            }
            if (scanner.tag(index) != null)
            {
                builder.append('!').append(scanner.tag(index));
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private static String eagerRaw(
        String text)
    {
        StringBuilder builder = new StringBuilder();
        projectRaw(YamlDocumentParser.parse(text, new YamlConfiguration(
            Map.of(YamlConfig.RESOLVE_REFERENCES, false))).node, builder);
        return builder.toString();
    }

    private static void projectRaw(
        YamlNode node,
        StringBuilder builder)
    {
        if (node.alias != null)
        {
            builder.append("ALIAS*").append(node.alias).append('\n');
        }
        else if (node instanceof YamlObjectNode object)
        {
            builder.append("START_OBJECT").append(anchorOf(object)).append('\n');
            for (YamlEntry entry : object.entries)
            {
                String name = entry.name != null ? entry.name : ((YamlScalarNode) entry.key).value;
                builder.append("KEY_NAME").append('(').append(name).append(')').append('\n');
                projectRaw(entry.value, builder);
            }
            builder.append("END_OBJECT").append('\n');
        }
        else if (node instanceof YamlArrayNode array)
        {
            builder.append("START_ARRAY").append(anchorOf(array)).append('\n');
            for (YamlNode value : array.values)
            {
                projectRaw(value, builder);
            }
            builder.append("END_ARRAY").append('\n');
        }
        else
        {
            YamlScalarNode scalar = (YamlScalarNode) node;
            switch (scalar.type)
            {
            case STRING -> builder.append("VALUE_STRING").append('(').append(scalar.value).append(')');
            case NUMBER -> builder.append("VALUE_NUMBER").append('(').append(scalar.value).append(')');
            case TRUE -> builder.append("VALUE_TRUE");
            case FALSE -> builder.append("VALUE_FALSE");
            case NULL -> builder.append("VALUE_NULL");
            }
            builder.append(anchorOf(scalar)).append('\n');
        }
    }

    private static String anchorOf(
        YamlNode node)
    {
        return (node.anchor != null ? "&" + node.anchor : "") + (node.tag != null ? "!" + node.tag : "");
    }

    private static String token(
        byte kind)
    {
        return switch (kind)
        {
        case YamlStreamScanner.START_OBJECT -> "START_OBJECT";
        case YamlStreamScanner.END_OBJECT -> "END_OBJECT";
        case YamlStreamScanner.START_ARRAY -> "START_ARRAY";
        case YamlStreamScanner.END_ARRAY -> "END_ARRAY";
        case YamlStreamScanner.KEY_NAME -> "KEY_NAME";
        case YamlStreamScanner.VALUE_STRING -> "VALUE_STRING";
        case YamlStreamScanner.VALUE_NUMBER -> "VALUE_NUMBER";
        case YamlStreamScanner.VALUE_TRUE -> "VALUE_TRUE";
        case YamlStreamScanner.VALUE_FALSE -> "VALUE_FALSE";
        case YamlStreamScanner.VALUE_NULL -> "VALUE_NULL";
        case YamlStreamScanner.ALIAS -> "ALIAS";
        default -> throw new IllegalStateException("Unexpected kind: " + kind);
        };
    }

    private static final Path SUITE_DIR = resolveSuite();

    private static Stream<Path> fixtures() throws IOException
    {
        List<Path> directories = new ArrayList<>();
        Files.find(SUITE_DIR, 3, (p, a) -> a.isRegularFile() && "in.yaml".equals(p.getFileName().toString()))
            .map(Path::getParent)
            .filter(p -> !Files.exists(p.resolve("error")))
            .sorted(Comparator.comparing(p -> SUITE_DIR.relativize(p).toString()))
            .forEach(directories::add);
        return directories.stream();
    }

    private static Path resolveSuite()
    {
        URL resource = YamlStreamScannerTest.class.getResource("/io/aklivity/zilla/runtime/common/yaml/" + SUITE_TAG);
        if (resource == null)
        {
            throw new IllegalStateException("Missing vendored YAML test suite: " + SUITE_TAG);
        }
        try
        {
            return Path.of(resource.toURI());
        }
        catch (URISyntaxException ex)
        {
            throw new IllegalStateException("Invalid vendored YAML test suite location: " + resource, ex);
        }
    }
}
