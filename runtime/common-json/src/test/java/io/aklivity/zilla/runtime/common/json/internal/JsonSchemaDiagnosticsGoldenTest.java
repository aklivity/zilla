/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json.internal;

import static io.aklivity.zilla.runtime.common.json.internal.JsonSchemaConformanceTest.DRAFT2019_FILES;
import static io.aklivity.zilla.runtime.common.json.internal.JsonSchemaConformanceTest.DRAFT2020_FILES;
import static io.aklivity.zilla.runtime.common.json.internal.JsonSchemaConformanceTest.DRAFT4_FILES;
import static io.aklivity.zilla.runtime.common.json.internal.JsonSchemaConformanceTest.DRAFT6_FILES;
import static io.aklivity.zilla.runtime.common.json.internal.JsonSchemaConformanceTest.DRAFT7_FILES;
import static io.aklivity.zilla.runtime.common.json.internal.JsonSchemaConformanceTest.ROOT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchema.Draft;
import io.aklivity.zilla.runtime.common.json.JsonSchemaDiagnostic;
import io.aklivity.zilla.runtime.common.json.JsonValidationException;

/**
 * Pins, for every case of the vendored JSON Schema Test Suite, the verdict and the diagnostics
 * (line, column, instance pointer, keyword and message) the validator reports, both when the whole
 * instance is in one window and when it is delivered in small windows. The expected text lives in
 * {@code conformance/diagnostics-<draft>.golden}; regenerate it deliberately with
 * {@code -Dzilla.json.golden.update=true} and review the difference.
 */
class JsonSchemaDiagnosticsGoldenTest
{
    private static final String UPDATE = "zilla.json.golden.update";
    private static final int WINDOW = 7;

    @Test
    void shouldReportDraft4Diagnostics() throws IOException
    {
        verify(Draft.DRAFT_04, "draft4", DRAFT4_FILES);
    }

    @Test
    void shouldReportDraft6Diagnostics() throws IOException
    {
        verify(Draft.DRAFT_06, "draft6", DRAFT6_FILES);
    }

    @Test
    void shouldReportDraft7Diagnostics() throws IOException
    {
        verify(Draft.DRAFT_07, "draft7", DRAFT7_FILES);
    }

    @Test
    void shouldReportDraft2019Diagnostics() throws IOException
    {
        verify(Draft.DRAFT_2019_09, "draft2019-09", DRAFT2019_FILES);
    }

    @Test
    void shouldReportDraft2020Diagnostics() throws IOException
    {
        verify(Draft.DRAFT_2020_12, "draft2020-12", DRAFT2020_FILES);
    }

    private static void verify(
        Draft draft,
        String dir,
        String[] files) throws IOException
    {
        final List<String> actual = new ArrayList<>();
        for (String file : files)
        {
            final JsonNode root = JsonNode.parse(JsonSchemaConformanceTest.read(ROOT + dir + "/" + file + ".json"));
            int groupIndex = 0;
            for (JsonNode group : root.elements())
            {
                describe(draft, file, groupIndex++, group, actual);
            }
        }

        final String name = "diagnostics-" + dir + ".golden";
        final Path golden = Path.of("src", "test", "resources", ROOT, name);
        if (Boolean.getBoolean(UPDATE))
        {
            Files.writeString(golden, String.join("\n", actual) + "\n");
        }
        else
        {
            final List<String> expected = List.of(read(ROOT + name).split("\n"));
            assertEquals(expected.size(), actual.size(), "case count for " + name);
            for (int index = 0; index < expected.size(); index++)
            {
                assertEquals(expected.get(index), actual.get(index), name + " line " + (index + 1));
            }
        }
    }

    private static void describe(
        Draft draft,
        String file,
        int groupIndex,
        JsonNode group,
        List<String> lines)
    {
        final String schema = JsonSchemaConformanceTest.toJson(group.get("schema"));
        final String prefix = file + "#" + groupIndex;
        try
        {
            final JsonSchema compiled =
                JsonSchema.of(schema, JsonSchemaConformanceTest::resolveRemote, draft);
            int testIndex = 0;
            for (JsonNode test : group.get("tests").elements())
            {
                final String data = JsonSchemaConformanceTest.toJson(test.get("data")) + " ";
                final String label = prefix + "." + testIndex++;
                lines.add(label + " whole " + run(compiled, data, data.length()));
                lines.add(label + " windowed " + run(compiled, data, WINDOW));
            }
        }
        catch (UnsupportedOperationException ex)
        {
            lines.add(prefix + " unsupported");
        }
        catch (PatternSyntaxException ex)
        {
            lines.add(prefix + " pattern");
        }
        catch (IllegalArgumentException ex)
        {
            lines.add(prefix + " remote");
        }
    }

    private static String run(
        JsonSchema schema,
        String data,
        int window)
    {
        final List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        final byte[] bytes = data.getBytes(UTF_8);
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
        final JsonParserEx parser = schema.newParser(false, JsonEx.createParser(), diagnostics::add);

        String outcome;
        try
        {
            int progress = 0;
            int limit = 0;
            int guard = 0;
            while (progress < bytes.length && guard++ < 4 * bytes.length + 8)
            {
                limit = Math.min(bytes.length, limit + window);
                parser.wrap(input, progress, limit, limit == bytes.length);
                while (parser.hasNextEvent())
                {
                    final JsonParserEx.Mode mode = JsonParserEx.Mode.STRUCTURED;
                    final var event = parser.nextEvent(mode);
                    switch (event)
                    {
                    case VALUE_STRING:
                    case VALUE_NUMBER:
                    case KEY_NAME:
                        parser.consumed(parser.getStringView().length());
                        break;
                    default:
                        break;
                    }
                }
                progress = limit - parser.remaining();
            }
            outcome = diagnostics.isEmpty() ? "valid" : "invalid";
        }
        catch (JsonValidationException ex)
        {
            outcome = "invalid";
        }
        catch (RuntimeException ex)
        {
            outcome = "error " + ex.getClass().getSimpleName();
        }

        final StringBuilder text = new StringBuilder(outcome);
        for (JsonSchemaDiagnostic diagnostic : diagnostics)
        {
            text.append(" | ").append(diagnostic.line()).append(',').append(diagnostic.column())
                .append(' ').append(diagnostic.pointer().isEmpty() ? "<root>" : diagnostic.pointer())
                .append(' ').append(diagnostic.keyword()).append(": ").append(diagnostic.message());
        }
        return text.toString().replace('\n', ' ');
    }

    private static String read(
        String resource)
    {
        try (InputStream in = JsonSchemaDiagnosticsGoldenTest.class.getClassLoader().getResourceAsStream(resource))
        {
            if (in == null)
            {
                throw new IllegalStateException("missing golden resource: " + resource + ", regenerate with -D" + UPDATE);
            }
            return new String(in.readAllBytes(), UTF_8);
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
    }
}
