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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Validates the {@link YamlParser} YAML 1.2 event layer. Targeted tests pin the event stream, scalar views,
 * styles and document markers for representative inputs; the factory test asserts that every invalid
 * conformance fixture is rejected. End-to-end event correctness across the whole corpus is verified against
 * the canonical {@code test.event} fixtures by {@code YamlConformanceTest}.
 */
class YamlParserTest
{
    private static final String SUITE_TAG = "data-2022-01-17";

    @Test
    void shouldEmitStreamAndDocumentFramingForBlockMapping()
    {
        assertEquals("+STR\n+DOC\nMAP{\nS(a)\nS(NUMBER:1)\nS(b)\nS(STRING:two)\n}MAP\n-DOC\n-STR\n",
            events("a: 1\nb: two\n"));
    }

    @Test
    void shouldEmitSequenceEvents()
    {
        assertEquals("+STR\n+DOC\nSEQ[\nS(STRING:x)\nS(STRING:y)\n]SEQ\n-DOC\n-STR\n",
            events("- x\n- y\n"));
    }

    @Test
    void shouldEmitFlowMappingEvents()
    {
        assertEquals("+STR\n+DOC\nMAP{\nS(a)\nS(NUMBER:1)\nS(b)\nS(TRUE:)\n}MAP\n-DOC\n-STR\n",
            events("{a: 1, b: true}\n"));
    }

    @Test
    void shouldEmitAnchorAndAlias()
    {
        assertEquals("+STR\n+DOC\nMAP{\nS(a)\nS(NUMBER:1)&x\nS(b)\nA(x)\n}MAP\n-DOC\n-STR\n",
            events("a: &x 1\nb: *x\n"));
    }

    @Test
    void shouldEmitTagOnScalar()
    {
        assertEquals("+STR\n+DOC\nMAP{\nS(a)\nS(NUMBER:42)!tag:yaml.org,2002:str\n}MAP\n-DOC\n-STR\n",
            events("a: !!str 42\n"));
    }

    @Test
    void shouldEmitNonScalarMappingKey()
    {
        assertEquals("+STR\n+DOC\nMAP{\nSEQ[\nS(STRING:a)\nS(STRING:b)\n]SEQ\nS(STRING:v)\n}MAP\n-DOC\n-STR\n",
            events("? - a\n  - b\n: v\n"));
    }

    @Test
    void shouldFrameMultipleDocuments()
    {
        assertEquals("+STR\n+DOC\nSEQ[\nS(STRING:a)\n]SEQ\n-DOC\n+DOC\nSEQ[\nS(STRING:b)\n]SEQ\n-DOC\n-STR\n",
            events("---\n- a\n---\n- b\n"));
    }

    @Test
    void shouldExposePlainScalarViewAndStyle()
    {
        YamlParser parser = scalarAt("42\n");
        assertEquals("42", parser.value());
        assertEquals("42", parser.view());
        assertEquals(YamlScalarStyle.PLAIN, parser.style());
    }

    @Test
    void shouldViewHexIntegerInSourceFormWhileValueResolvesToDecimal()
    {
        YamlParser parser = scalarAt("0x10\n");
        assertEquals(YamlScalarType.NUMBER, parser.scalarType());
        assertEquals("16", parser.value());
        assertEquals("0x10", parser.view());
        assertEquals(YamlScalarStyle.PLAIN, parser.style());
    }

    @Test
    void shouldExposeBooleanAndNullViews()
    {
        YamlParser yes = scalarAt("true\n");
        assertEquals(YamlScalarType.TRUE, yes.scalarType());
        assertEquals("true", yes.view());

        YamlParser nil = scalarAt("~\n");
        assertEquals(YamlScalarType.NULL, nil.scalarType());
        assertEquals("~", nil.view());
    }

    @Test
    void shouldExposeQuotedScalarStyles()
    {
        YamlParser single = scalarAt("'abc'\n");
        assertEquals("abc", single.view());
        assertEquals(YamlScalarStyle.SINGLE, single.style());

        YamlParser doubled = scalarAt("\"a\\nb\"\n");
        assertEquals("a\nb", doubled.view());
        assertEquals(YamlScalarStyle.DOUBLE, doubled.style());
    }

    @Test
    void shouldExposeBlockScalarStyles()
    {
        YamlParser literal = scalarAt("|\n  a\n  b\n");
        assertEquals("a\nb\n", literal.view());
        assertEquals(YamlScalarStyle.LITERAL, literal.style());

        YamlParser folded = scalarAt(">\n  a\n  b\n");
        assertEquals("a b\n", folded.view());
        assertEquals(YamlScalarStyle.FOLDED, folded.style());
    }

    @Test
    void shouldExposeExplicitDocumentMarkers()
    {
        YamlParser explicit = new YamlParser("---\nfoo\n...\n");
        assertEquals(YamlEvent.STREAM_START, explicit.next());
        assertEquals(YamlEvent.DOCUMENT_START, explicit.next());
        assertEquals(true, explicit.explicit());
        assertEquals(YamlEvent.SCALAR, explicit.next());
        assertEquals(YamlEvent.DOCUMENT_END, explicit.next());
        assertEquals(true, explicit.explicit());

        YamlParser bare = new YamlParser("foo\n");
        assertEquals(YamlEvent.STREAM_START, bare.next());
        assertEquals(YamlEvent.DOCUMENT_START, bare.next());
        assertEquals(false, bare.explicit());
        assertEquals(YamlEvent.SCALAR, bare.next());
        assertEquals(YamlEvent.DOCUMENT_END, bare.next());
        assertEquals(false, bare.explicit());
    }

    private static YamlParser scalarAt(
        String text)
    {
        YamlParser parser = new YamlParser(text);
        YamlEvent event = parser.next();
        while (event != YamlEvent.SCALAR && parser.hasNext())
        {
            event = parser.next();
        }
        return parser;
    }

    @TestFactory
    Stream<DynamicTest> shouldRejectEveryInvalidFixture() throws Exception
    {
        return invalidFixtures()
            .map(path -> DynamicTest.dynamicTest(SUITE_DIR.relativize(path).toString(), () ->
            {
                String text = Files.readString(path.resolve("in.yaml"));
                assertThrows(RuntimeException.class, () -> events(text));
            }));
    }

    private static String events(
        String text)
    {
        StringBuilder builder = new StringBuilder();
        YamlParser parser = new YamlParser(text);
        while (parser.hasNext())
        {
            YamlEvent event = parser.next();
            render(builder, event, parser.value(), parser.scalarType(), parser.anchor(), parser.tag(), parser.alias());
        }
        return builder.toString();
    }

    private static void render(
        StringBuilder builder,
        YamlEvent type,
        CharSequence value,
        YamlScalarType scalarType,
        String anchor,
        String tag,
        String alias)
    {
        switch (type)
        {
        case STREAM_START -> builder.append("+STR\n");
        case STREAM_END -> builder.append("-STR\n");
        case DOCUMENT_START -> builder.append("+DOC\n");
        case DOCUMENT_END -> builder.append("-DOC\n");
        case MAPPING_START -> builder.append("MAP{").append(decorators(anchor, tag)).append('\n');
        case MAPPING_END -> builder.append("}MAP\n");
        case SEQUENCE_START -> builder.append("SEQ[").append(decorators(anchor, tag)).append('\n');
        case SEQUENCE_END -> builder.append("]SEQ\n");
        case SCALAR -> builder.append("S(").append(scalarType != null ? scalarType + ":" : "")
            .append(value != null ? value : "").append(')').append(decorators(anchor, tag)).append('\n');
        case ALIAS -> builder.append("A(").append(alias).append(")\n");
        }
    }

    private static String decorators(
        String anchor,
        String tag)
    {
        return (anchor != null ? "&" + anchor : "") + (tag != null ? "!" + tag : "");
    }

    private static final Path SUITE_DIR = resolveSuite();

    private static Stream<Path> invalidFixtures() throws IOException
    {
        return fixtures().filter(p -> Files.exists(p.resolve("error")));
    }

    private static Stream<Path> fixtures() throws IOException
    {
        List<Path> directories = new ArrayList<>();
        Files.find(SUITE_DIR, 3, (p, a) -> a.isRegularFile() && "in.yaml".equals(p.getFileName().toString()))
            .map(Path::getParent)
            .sorted(Comparator.comparing(p -> SUITE_DIR.relativize(p).toString()))
            .forEach(directories::add);
        return directories.stream();
    }

    private static Path resolveSuite()
    {
        URL resource = YamlParserTest.class.getResource("/io/aklivity/zilla/runtime/common/yaml/" + SUITE_TAG);
        if (resource == null)
        {
            throw new IllegalStateException("Missing vendored YAML test suite: " + SUITE_TAG);
        }
        Path suite;
        try
        {
            suite = Path.of(resource.toURI());
        }
        catch (URISyntaxException ex)
        {
            throw new IllegalStateException(ex);
        }
        return suite;
    }
}
