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
package io.aklivity.zilla.runtime.common.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import io.aklivity.zilla.runtime.common.yaml.internal.YamlEvent;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlParser;
import io.aklivity.zilla.runtime.common.yaml.internal.YamlScalarStyle;

/**
 * Validates the {@link YamlParser} event layer against the canonical {@code test.event} fixtures vendored from
 * the YAML test suite. For every valid fixture the parser's events are rendered into the canonical event text
 * and compared verbatim; every invalid fixture must be rejected.
 */
final class YamlConformanceTest
{
    private static final String SUITE_TAG = "data-2022-01-17";
    private static final Path SUITE_DIR = resolveSuite();

    @TestFactory
    Stream<DynamicTest> shouldMatchCanonicalEventsForValidCases() throws Exception
    {
        return cases()
            .filter(c -> c.has("test.event") && !c.has("error"))
            .map(c -> DynamicTest.dynamicTest(c.displayName(), () ->
            {
                String yaml = Files.readString(c.path.resolve("in.yaml"));
                String expected = Files.readString(c.path.resolve("test.event"));
                assertEquals(normalize(expected), render(yaml), c.id);
            }));
    }

    @TestFactory
    Stream<DynamicTest> shouldRejectInvalidCases() throws Exception
    {
        return cases()
            .filter(c -> c.has("error"))
            .map(c -> DynamicTest.dynamicTest(c.displayName(), () ->
            {
                String yaml = Files.readString(c.path.resolve("in.yaml"));
                assertThrows(RuntimeException.class, () -> render(yaml), c.id);
            }));
    }

    private static String render(
        String yaml)
    {
        StringBuilder builder = new StringBuilder();
        YamlParser parser = new YamlParser(yaml);
        while (parser.hasNext())
        {
            renderEvent(builder, parser, parser.next());
        }
        return builder.toString();
    }

    private static void renderEvent(
        StringBuilder builder,
        YamlParser parser,
        YamlEvent event)
    {
        switch (event)
        {
        case STREAM_START -> builder.append("+STR\n");
        case STREAM_END -> builder.append("-STR\n");
        case DOCUMENT_START -> builder.append("+DOC").append(parser.explicit() ? " ---" : "").append('\n');
        case DOCUMENT_END -> builder.append("-DOC").append(parser.explicit() ? " ..." : "").append('\n');
        case MAPPING_START -> builder.append("+MAP").append(parser.flow() ? " {}" : "")
            .append(properties(parser)).append('\n');
        case MAPPING_END -> builder.append("-MAP\n");
        case SEQUENCE_START -> builder.append("+SEQ").append(parser.flow() ? " []" : "")
            .append(properties(parser)).append('\n');
        case SEQUENCE_END -> builder.append("-SEQ\n");
        case SCALAR -> builder.append("=VAL").append(properties(parser)).append(' ')
            .append(indicator(parser.style())).append(escape(parser.view())).append('\n');
        case ALIAS -> builder.append("=ALI *").append(parser.alias()).append('\n');
        }
    }

    private static String properties(
        YamlParser parser)
    {
        StringBuilder builder = new StringBuilder();
        if (parser.anchor() != null)
        {
            builder.append(" &").append(parser.anchor());
        }
        if (parser.tag() != null)
        {
            builder.append(" <").append(parser.tag()).append('>');
        }
        return builder.toString();
    }

    private static char indicator(
        YamlScalarStyle style)
    {
        return switch (style)
        {
        case SINGLE -> '\'';
        case DOUBLE -> '"';
        case LITERAL -> '|';
        case FOLDED -> '>';
        default -> ':';
        };
    }

    private static String escape(
        String value)
    {
        StringBuilder builder = new StringBuilder();
        for (int at = 0; at < value.length(); at++)
        {
            char c = value.charAt(at);
            switch (c)
            {
            case '\\' -> builder.append("\\\\");
            case '\n' -> builder.append("\\n");
            case '\t' -> builder.append("\\t");
            case '\r' -> builder.append("\\r");
            case '\b' -> builder.append("\\b");
            case '\0' -> builder.append("\\0");
            default -> builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String normalize(
        String expected)
    {
        return expected.endsWith("\n") ? expected : expected + "\n";
    }

    private static Stream<Case> cases() throws IOException
    {
        return Files.find(SUITE_DIR, 3, (p, a) -> a.isRegularFile() && "in.yaml".equals(p.getFileName().toString()))
            .map(Path::getParent)
            .map(Case::new)
            .sorted(Comparator.comparing(c -> c.id));
    }

    private static Path resolveSuite()
    {
        URL resource = YamlConformanceTest.class.getResource(SUITE_TAG);
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

    private static final class Case
    {
        private final String id;
        private final Path path;

        private Case(
            Path path)
        {
            this.path = path;
            this.id = SUITE_DIR.relativize(path).toString();
        }

        private boolean has(
            String filename)
        {
            return Files.exists(path.resolve(filename));
        }

        private String displayName()
        {
            Path description = path.resolve("===");
            String title = "";
            try
            {
                title = Files.exists(description) ? Files.readString(description).strip() : "";
            }
            catch (IOException ex)
            {
                // Keep test discovery working even if an optional title file is unreadable.
            }
            return title.isEmpty() ? id : id + " " + title;
        }
    }
}
