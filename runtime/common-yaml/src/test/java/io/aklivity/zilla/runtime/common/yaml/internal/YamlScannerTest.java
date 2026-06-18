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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Validates the conservative streaming scanner. The scanner is complete for valid YAML 1.2, so the guarantees
 * tested here are that it accepts every valid construct it is meant to and rejects every invalid one with a
 * stable, located bail message. Event-projection correctness is verified end-to-end against the canonical
 * {@code test.event} fixtures by {@code YamlConformanceTest}; rejection messages are pinned against a vendored
 * snapshot so a regression in the diagnostics is caught here.
 */
class YamlScannerTest
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
        YamlScanner scanner = new YamlScanner();
        assertTrue(scanner.scan(BLOCK_CONFIG), "scanner should accept the block config subset");
    }

    @Test
    void shouldAcceptFlowDocument()
    {
        String doc = """
            {"name":"test","enabled":true,"items":[{"id":1,"name":"a"},{"id":2,"name":"b"}],
             "nested":{"x":1,"y":2},"missing":null}
            """;
        assertTrue(new YamlScanner().scan(doc), "scanner should accept a JSON-style flow document");
    }

    @Test
    void shouldAcceptFlowSequenceDocument()
    {
        assertTrue(new YamlScanner().scan("[1, two, true, null, {a: 1}]\n"));
    }

    @Test
    void shouldAcceptSingleLineFlowValueInBlock()
    {
        String doc = "items: [1, two, true, null]\nnested: {a: 1, b: two}\nmatrix: [[1, 2], [3, 4]]\n";
        assertTrue(new YamlScanner().scan(doc), "scanner should accept single-line flow values in a block mapping");
    }

    @Test
    void shouldAcceptFlowValueInSequenceItem()
    {
        assertTrue(new YamlScanner().scan("routes:\n- [a, b]\n- {exit: http0}\n"));
    }

    @Test
    void shouldAcceptImplicitMapInFlowSequence()
    {
        for (String doc : new String[] {
            "seq: [ a: 1 ]\n",
            "seq: [ a: 1, b: 2 ]\n",
            "seq: [ plain, k: v, other ]\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept an implicit map in a flow sequence: " + doc);
        }
    }

    @Test
    void shouldAcceptFlowMappingOmittedAndEmptyValues()
    {
        for (String doc : new String[] {
            "---\n- { single line, a: b}\n",
            "m: {key, other: val}\n",
            "m: {a: , b: 2}\n",
            "m: {\"x\":adjacent}\n",
            "m: {: empty key, a: b}\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept omitted/empty flow values: " + doc);
        }
    }

    @Test
    void shouldAcceptMultiLineQuotedScalarInFlow()
    {
        for (String doc : new String[] {
            "[\"double\n quoted\", 'single\n quoted', plain]\n",
            "{ k: \"a\n b\" }\n",
            "[\"esc \\n here\nand more\"]\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a multi-line quoted scalar in flow: " + doc);
        }
    }

    @Test
    void shouldAcceptQuestionLedPlainScalarInFlow()
    {
        for (String doc : new String[] {
            "{ ?foo: bar, bar: 42 }\n",
            "[?x]\n",
            "list: [?a, ?b]\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a ?-led plain scalar in flow: " + doc);
        }
    }

    @Test
    void shouldAcceptExplicitKeyIndicatorInFlow()
    {
        for (String doc : new String[] {
            "[\n? foo\n bar : baz\n]\n",
            "{ ? key : value, ? other : 2 }\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a ? explicit key in flow: " + doc);
        }
    }

    @Test
    void shouldAcceptFlowDocumentBodyAndComments()
    {
        for (String doc : new String[] {
            "---\n{ a: [b, c, { d: [e, f] } ] }\n",
            "---\n{\n a: [\n  b, c, {\n   d: [e, f]\n  }\n ]\n}\n",
            "[ word1\n# comment\n, word2]\n",
            "---\n[1, 2]\n...\n---\n[3, 4]\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a flow document body: " + doc);
        }
    }

    @Test
    void shouldAcceptMultiLineFlowScalar()
    {
        for (String doc : new String[] {
            "---\n- { multi\n  line: value}\n",
            "Sammy Sosa: {\n    hr: 63,\n    avg: 0.288\n  }\n",
            "list: [a\n  b, c]\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a multi-line flow scalar: " + doc);
        }
    }

    @Test
    void shouldAcceptMultiLineFlowValueInBlock()
    {
        String doc = "items: [1,\n  2, 3]\nnested: {a: 1,\n  b: 2}\n";
        assertTrue(new YamlScanner().scan(doc), "scanner should accept multi-line flow values in a block");
    }

    @Test
    void shouldAcceptExplicitKeys()
    {
        assertTrue(new YamlScanner().scan("? a\n: 1.3\n? b\n: c\nplain: x\n"), "scanner should accept explicit keys");
    }

    @Test
    void shouldAcceptEscapeFreeQuotedScalars()
    {
        String doc = "name: \"quoted value\"\nkind: 'literal'\n\"quoted key\": 7114\nflag: \"true\"\n";
        assertTrue(new YamlScanner().scan(doc), "scanner should accept escape-free quoted scalars");
    }

    @Test
    void shouldAcceptDecoratedExplicitKeys()
    {
        for (String doc : new String[] {
            "? !!str a\n: !!int 47\n? c\n: d\n",
            "? &a key\n: value\nuse: *a\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a decorated explicit key: " + doc);
        }
    }

    @Test
    void shouldRepresentNonScalarFlowKey()
    {
        // a non-scalar mapping key is valid YAML; the scanner preserves it (the JSON projection rejects it)
        for (String doc : new String[] {
            "{a: [b, c], [d, e]: f}\n",
            "{ {x: 1}: y }\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a non-scalar flow key: " + doc);
        }
    }

    @Test
    void shouldRepresentNonScalarBlockKey()
    {
        // a flow collection used as a block mapping key is valid YAML; the scanner preserves it
        for (String doc : new String[] {
            "[flow]: block\n",
            "{ first: Sammy, last: Sosa }:\n  hr: 65\n  avg: 0.278\n",
            "[a, b]: c\nd: e\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a non-scalar block key: " + doc);
        }
    }

    @Test
    void shouldAcceptDecoratedBlockMappingKeys()
    {
        for (String doc : new String[] {
            "!!str a: b\nc: d\n",
            "&k1 key1: one\nuse: *k1\n",
            "!!str 23: !!bool false\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a decorated mapping key: " + doc);
        }
    }

    @Test
    void shouldAcceptEscapedQuotedScalar()
    {
        for (String doc : new String[] {
            "name: \"a\\tb\"\n",
            "name: \"line\\none\\ttwo\"\n",
            "name: \"quote \\\" and \\\\ slash\"\n",
            "name: \"unicode \\u00e9 \\x41\"\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept an escaped double-quoted scalar: " + doc);
        }
    }

    @Test
    void shouldAcceptSingleQuoteEscape()
    {
        assertTrue(new YamlScanner().scan("name: 'it''s here'\n"), "scanner should accept a single-quoted scalar");
    }

    @Test
    void shouldAcceptInlineMarkerScalar()
    {
        for (String doc : new String[] {
            "--- text\n",
            "%YAML 1.2\n--- example\n",
            "--- \"quoted value\"\n",
            "--- scalar\n  folded over lines\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept an inline marker scalar: " + doc);
        }
    }

    @Test
    void shouldAcceptInlineMarkerBlockScalar()
    {
        for (String doc : new String[] {
            "--- |\n  text\n",
            "--- |\n%!PS-Adobe-2.0\n...\n",
            "--- >\n  folded\n  text\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept an inline marker block scalar: " + doc);
        }
    }

    @Test
    void shouldAcceptCompactSequence()
    {
        for (String doc : new String[] {
            "- - s1_i1\n  - s1_i2\n- s2\n",
            "key: - a\n",
            "matrix:\n  - - 1\n    - 2\n  - - 3\n    - 4\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a compact sequence: " + doc);
        }
    }

    @Test
    void shouldAcceptNestedPlainScalarValue()
    {
        for (String doc : new String[] {
            "plain:\n  this unquoted scalar\n  spans many lines\n",
            "outer:\n  inner:\n    folded value\n    over lines\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a nested plain scalar value: " + doc);
        }
    }

    @Test
    void shouldAcceptMultiLinePlain()
    {
        for (String doc : new String[] {
            "name: this is\n  a long value\n",
            "name: first\n\n  second\n",
            "- item one\n  item two\n- plain\n",
            "this is\na root scalar\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a multi-line plain scalar: " + doc);
        }
    }

    @Test
    void shouldAcceptMultiLineQuotedWithEscapesAndSameIndent()
    {
        for (String doc : new String[] {
            "quoted: \"So does this\n  quoted scalar.\\n\"\n",
            "---\n\"\n  foo \n \n    bar\n\n  baz\n\"\n",
            "--- \"quoted\nstring\"\n--- &node foo\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a multi-line quoted scalar: " + doc);
        }
    }

    @Test
    void shouldAcceptMultiLineQuoted()
    {
        for (String doc : new String[] {
            "name: \"line one\n  line two\"\n",
            "name: 'line one\n  line two'\n",
            "name: \"first\n\n  second\"\n",
            "- \"item one\n  item two\"\n- plain\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a multi-line quoted scalar: " + doc);
        }
    }

    @Test
    void shouldAcceptLiteralBlockScalar()
    {
        assertTrue(new YamlScanner().scan("text: |\n  line one\n  line two\n"),
            "scanner should accept a literal block scalar");
    }

    @Test
    void shouldAcceptFoldedBlockScalar()
    {
        assertTrue(new YamlScanner().scan("text: >\n  line one\n  line two\n\n  next para\n"),
            "scanner should accept a folded block scalar");
    }

    @Test
    void shouldAcceptExplicitIndentBlockScalar()
    {
        assertTrue(new YamlScanner().scan("text: |2\n    line\nmore: >1-\n  folded\n"),
            "scanner should accept explicit-indent block scalars");
    }

    @Test
    void shouldAcceptBlockScalarInSequenceAndRoot()
    {
        for (String doc : new String[] {"- |\n  one\n  two\n- plain\n", "- >\n  folded text\n", ">\n  root folded\n  scalar\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept block scalar: " + doc);
        }
    }

    @Test
    void shouldAcceptSingleDocumentMarkers()
    {
        assertTrue(new YamlScanner().scan("---\nname: example\nport: 7114\n...\n"),
            "scanner should accept a single document with markers");
    }

    @Test
    void shouldAcceptMultiDocumentStream()
    {
        for (String doc : new String[] {
            "---\nname: one\n---\nname: two\n",
            "name: one\n---\nname: two\n",
            "---\nname: one\n...\n---\nname: two\nvalues: [1, 2]\n",
            "doc one\n---\ndoc two\n",
            "---\n---\nname: two\n",
            "%YAML 1.2\n---\nname: one\n---\nname: two\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept a multi-document stream: " + doc);
        }
    }

    @Test
    void shouldBailOnMalformedYamlDirective()
    {
        assertFalse(new YamlScanner().scan("%YAML 1.2 foo\n---\n"));
        assertFalse(new YamlScanner().scan("%YAML 1.2\n%YAML 1.2\n---\n"));
    }

    @Test
    void shouldAcceptYamlDirective()
    {
        assertTrue(new YamlScanner().scan("%YAML 1.2\n---\nname: example\n"), "scanner should accept a %YAML directive");
    }

    @Test
    void shouldAcceptTagDirectives()
    {
        for (String doc : new String[] {
            "%TAG !e! tag:example.com,2000:app/\n---\n- !e!foo \"bar\"\n",
            "%TAG !! tag:example.com,2000:app/\n---\n!!int 1 - 3\n",
            "!<tag:yaml.org,2002:str> foo: bar\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept tag directives/verbatim tags: " + doc);
        }
    }

    @Test
    void shouldBailOnMalformedTagDirective()
    {
        // a directive after content (not at stream start or after ...) is invalid
        assertFalse(new YamlScanner().scan("foo\n%TAG ! tag:x,2000:\n---\nbar\n"));
    }

    @Test
    void shouldAcceptRootScalarDocument()
    {
        for (String doc : new String[] {"\"foo\"\n", "plain text\n", "42\n", "true\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept root scalar: " + doc);
        }
    }

    @Test
    void shouldAcceptTabsInContent()
    {
        for (String doc : new String[] {
            "a: b\tc\n",
            "text: |\n  line\twith\ttabs\n",
            "quoted: \"has\ttab\"\n",
            "list: [a,\tb]\n"})
        {
            assertTrue(new YamlScanner().scan(doc), "scanner should accept tabs in content: " + doc);
        }
    }

    @Test
    void shouldBailOnTabsInIndentation()
    {
        // a tab indenting a structural line or after an indicator is invalid YAML indentation
        assertFalse(new YamlScanner().scan("name:\n\tvalue\n"));
        assertFalse(new YamlScanner().scan("a:\n\tb: c\n"));
        assertFalse(new YamlScanner().scan("-\t- x\n"));
    }

    @Test
    void shouldAcceptAnchorsAliasesMerge()
    {
        String doc = "base: &base\n  host: localhost\nuse:\n  <<: *base\n  port: 7114\nrefs: [*base, plain]\n";
        assertTrue(new YamlScanner().scan(doc), "scanner should accept anchors/aliases/merge");
    }

    @Test
    void shouldAcceptTags()
    {
        String doc = "typed: !!str 42\nverbatim: !<tag:x> hi\ncustom: !foo bar\ntagged: !!map\n  a: 1\n";
        assertTrue(new YamlScanner().scan(doc), "scanner should accept tags");
    }

    @Test
    void shouldAcceptExpectedFixtureCount() throws Exception
    {
        // a ratchet toward full YAML 1.2 conformance: this count only ever rises as the scanner widens. A
        // drop means a regression bailed a previously accepted fixture.
        long accepted = fixtures()
            .filter(path -> accepts(path.resolve("in.yaml")))
            .count();
        assertEquals(308, accepted,
            "accepted-fixture count changed; a drop is a regression, a rise should bump this baseline");
    }

    @TestFactory
    Stream<DynamicTest> shouldRejectInvalidWithVendoredMessage() throws Exception
    {
        // every invalid fixture is rejected with a stable, located bail message pinned against a vendored
        // snapshot; the scanner is the sole rejection authority now that the eager parser is retired
        Map<String, String> expected = rejectMessages();
        return invalidFixtures()
            .map(path -> DynamicTest.dynamicTest(SUITE_DIR.relativize(path).toString(), () ->
            {
                String text = Files.readString(path.resolve("in.yaml"));
                String fixture = SUITE_DIR.relativize(path).toString();
                YamlScanner scanner = new YamlScanner();
                assertFalse(scanner.scan(text), "scanner should reject an invalid document");
                assertEquals(expected.get(fixture), scanner.bailMessage(),
                    "scanner bail message diverged from the vendored snapshot");
            }));
    }

    private static Map<String, String> rejectMessages() throws IOException
    {
        Map<String, String> messages = new HashMap<>();
        URL resource = YamlScannerTest.class.getResource(
            "/io/aklivity/zilla/runtime/common/yaml/" + SUITE_TAG + "-reject-messages.tsv");
        if (resource == null)
        {
            throw new IllegalStateException("Missing vendored reject-message snapshot for " + SUITE_TAG);
        }
        try
        {
            for (String line : Files.readAllLines(Path.of(resource.toURI())))
            {
                if (!line.isEmpty())
                {
                    int tab = line.indexOf('\t');
                    String message = line.substring(tab + 1)
                        .replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
                    messages.put(line.substring(0, tab), message);
                }
            }
        }
        catch (URISyntaxException ex)
        {
            throw new IllegalStateException("Invalid vendored reject-message location: " + resource, ex);
        }
        return messages;
    }

    private static boolean accepts(
        Path path)
    {
        try
        {
            return new YamlScanner().scan(Files.readString(path));
        }
        catch (IOException ex)
        {
            throw new IllegalStateException(ex);
        }
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

    private static Stream<Path> invalidFixtures() throws IOException
    {
        List<Path> directories = new ArrayList<>();
        Files.find(SUITE_DIR, 3, (p, a) -> a.isRegularFile() && "in.yaml".equals(p.getFileName().toString()))
            .map(Path::getParent)
            .filter(p -> Files.exists(p.resolve("error")))
            .sorted(Comparator.comparing(p -> SUITE_DIR.relativize(p).toString()))
            .forEach(directories::add);
        return directories.stream();
    }

    private static Path resolveSuite()
    {
        URL resource = YamlScannerTest.class.getResource("/io/aklivity/zilla/runtime/common/yaml/" + SUITE_TAG);
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
