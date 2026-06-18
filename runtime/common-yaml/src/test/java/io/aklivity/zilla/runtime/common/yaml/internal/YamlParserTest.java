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
 * Validates the {@link YamlParser} YAML 1.2 event layer. Targeted tests pin the event stream for
 * representative inputs; the differential factory tests assert that for every conformance fixture the
 * parser's events are identical to an independent walk of the eager {@link YamlDocumentParser} tree (the
 * reference), and that every invalid fixture is rejected.
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

    @TestFactory
    Stream<DynamicTest> shouldMatchEagerForEveryValidFixture() throws Exception
    {
        return validFixtures()
            .map(path -> DynamicTest.dynamicTest(SUITE_DIR.relativize(path).toString(), () ->
            {
                String text = Files.readString(path.resolve("in.yaml"));
                assertEquals(eagerEvents(text), events(text),
                    "parser events diverged from the eager reference");
            }));
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
            render(builder, event.type(), event.value(), event.scalarType(), event.anchor(), event.tag(), event.alias());
        }
        return builder.toString();
    }

    private static String eagerEvents(
        String text)
    {
        StringBuilder builder = new StringBuilder();
        render(builder, YamlEventType.STREAM_START, null, null, null, null, null);
        for (YamlDocumentParser.Result result : YamlDocumentParser.parseAll(text, YamlConfiguration.DEFAULT))
        {
            render(builder, YamlEventType.DOCUMENT_START, null, null, null, null, null);
            walk(builder, result.node);
            render(builder, YamlEventType.DOCUMENT_END, null, null, null, null, null);
        }
        render(builder, YamlEventType.STREAM_END, null, null, null, null, null);
        return builder.toString();
    }

    private static void walk(
        StringBuilder builder,
        YamlNode node)
    {
        if (node.alias != null)
        {
            render(builder, YamlEventType.ALIAS, null, null, null, null, node.alias);
        }
        else if (node instanceof YamlObjectNode object)
        {
            render(builder, YamlEventType.MAPPING_START, null, null, node.anchor, node.tag, null);
            for (YamlEntry entry : object.entries)
            {
                if (entry.name == null && entry.key != null &&
                    (!(entry.key instanceof YamlScalarNode) || entry.key.alias != null))
                {
                    walk(builder, entry.key);
                }
                else if (entry.name != null)
                {
                    render(builder, YamlEventType.SCALAR, entry.name, null, null, null, null);
                }
                else
                {
                    YamlScalarNode key = (YamlScalarNode) entry.key;
                    render(builder, YamlEventType.SCALAR, key.value, null, key.anchor, key.tag, null);
                }
                walk(builder, entry.value);
            }
            render(builder, YamlEventType.MAPPING_END, null, null, null, null, null);
        }
        else if (node instanceof YamlArrayNode array)
        {
            render(builder, YamlEventType.SEQUENCE_START, null, null, node.anchor, node.tag, null);
            for (YamlNode value : array.values)
            {
                walk(builder, value);
            }
            render(builder, YamlEventType.SEQUENCE_END, null, null, null, null, null);
        }
        else
        {
            YamlScalarNode scalar = (YamlScalarNode) node;
            render(builder, YamlEventType.SCALAR, scalar.value, scalar.type, scalar.anchor, scalar.tag, null);
        }
    }

    private static void render(
        StringBuilder builder,
        YamlEventType type,
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

    private static Stream<Path> validFixtures() throws IOException
    {
        return fixtures().filter(p -> !Files.exists(p.resolve("error")));
    }

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
