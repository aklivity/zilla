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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;

class JsonSchemaValidatingParserEventsTest
{
    private static final String STRING_SCHEMA = "{\"type\":\"string\"}";
    private static final String OBJECT_SCHEMA = "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"object\"," +
        "\"properties\":{\"b\":{\"type\":\"integer\"}}}}}";

    @Test
    void shouldAcceptValidInstancePulledAsEvents()
    {
        final List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();

        final List<JsonEvent> events = pull(STRING_SCHEMA, "\"hi\" ", Mode.STRUCTURED, 64, diagnostics);

        assertTrue(diagnostics.isEmpty());
        assertEquals(List.of(JsonEvent.START_DOCUMENT, JsonEvent.VALUE_STRING, JsonEvent.END_DOCUMENT), events);
    }

    @Test
    void shouldReportInvalidInstancePulledAsEvents()
    {
        final List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();

        pull(STRING_SCHEMA, "5 ", Mode.STRUCTURED, 64, diagnostics);

        assertEquals(1, diagnostics.size());
        assertEquals("type", diagnostics.get(0).keyword());
    }

    @Test
    void shouldThrowOnInvalidInstancePulledAsEventsWhenThrowing()
    {
        final JsonParserEx parser = JsonSchema.of(STRING_SCHEMA).newParser(true, JsonEx.createParser());
        final byte[] bytes = "5 ".getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);

        final JsonValidationException ex = assertThrows(JsonValidationException.class, () ->
        {
            while (parser.hasNextEvent())
            {
                parser.nextEvent();
            }
        });

        assertEquals("type", ex.diagnostics().get(0).keyword());
    }

    @Test
    void shouldReportNestedPointerPulledAsEvents()
    {
        final List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();

        pull(OBJECT_SCHEMA, "{\"a\":{\"b\":\"oops\"}} ", Mode.STRUCTURED, 64, diagnostics);

        assertTrue(diagnostics.stream().anyMatch(d -> "type".equals(d.keyword()) && "/a/b".equals(d.pointer())),
            "expected diagnostic at /a/b, got " + diagnostics);
    }

    @Test
    void shouldValidateWhenSegmentedDeliveryIsRequested()
    {
        final List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();

        final List<JsonEvent> events = pull(STRING_SCHEMA, "5 ", Mode.SEGMENTED, 64, diagnostics);

        assertEquals(1, diagnostics.size());
        assertFalse(events.stream().anyMatch(JsonEvent::segmented));
    }

    @Test
    void shouldValidateValueStraddlingWindows()
    {
        final List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();

        pull(OBJECT_SCHEMA, "{\"a\":{\"b\":\"oops\"}} ", Mode.STRUCTURED, 7, diagnostics);

        assertTrue(diagnostics.stream().anyMatch(d -> "/a/b".equals(d.pointer())), "got " + diagnostics);
    }

    @Test
    void shouldReportTheKeyOfAStraddlingValueUnderASchemaThatNamesNoKey()
    {
        final List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();

        pull("{\"uniqueItems\":true}", "[{\"foo\":\"bar\",\"bar\":\"foo\"},{\"bar\":\"foo\",\"foo\":\"bar\"}] ",
            Mode.STRUCTURED, 7, diagnostics);

        assertTrue(diagnostics.stream().anyMatch(d -> "content".equals(d.keyword()) && d.pointer().startsWith("/0/")),
            "got " + diagnostics);
    }

    @Test
    void shouldAcceptStringLargerThanWindowWhenNoKeywordReadsContent()
    {
        final List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();

        pull(STRING_SCHEMA, "\"" + "x".repeat(200) + "\" ", Mode.STRUCTURED, 16, diagnostics);

        assertTrue(diagnostics.isEmpty(), "got " + diagnostics);
    }

    @Test
    void shouldRejectStringLargerThanWindowWhenAKeywordReadsContent()
    {
        final List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();

        pull("{\"type\":\"string\",\"maxLength\":300}", "\"" + "x".repeat(200) + "\" ", Mode.STRUCTURED, 16, diagnostics);

        assertEquals(1, diagnostics.size(), "got " + diagnostics);
    }

    @Test
    void shouldRejectMismatchedTypeOfStringLargerThanWindow()
    {
        final List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();

        pull("{\"type\":\"integer\"}", "\"" + "x".repeat(200) + "\" ", Mode.STRUCTURED, 16, diagnostics);

        assertEquals(1, diagnostics.size(), "got " + diagnostics);
    }

    @Test
    void shouldNotSkipValuesThatCannotBeValidated()
    {
        final JsonParserEx parser = JsonSchema.of(STRING_SCHEMA).newParser(false, JsonEx.createParser());

        assertThrows(UnsupportedOperationException.class, parser::skipValue);
    }

    @Test
    void shouldReportSameDiagnosticsAsJakartaPull()
    {
        final String[] documents = {"5 ", "\"a\" ", "{\"a\":{\"b\":1}} ", "{\"a\":{\"b\":\"x\"}} ", "[1,2] "};

        for (String document : documents)
        {
            final List<JsonSchemaDiagnostic> expected = new ArrayList<>();
            final jakarta.json.stream.JsonParser jakarta = JsonSchema.of(OBJECT_SCHEMA)
                .newParser(false, JsonEx.createParser(new java.io.ByteArrayInputStream(document.getBytes(UTF_8))), expected::add);
            while (jakarta.hasNext())
            {
                jakarta.next();
            }

            final List<JsonSchemaDiagnostic> actual = new ArrayList<>();
            pull(OBJECT_SCHEMA, document, Mode.STRUCTURED, 64, actual);

            assertEquals(expected.stream().map(JsonSchemaDiagnostic::pointer).toList(),
                actual.stream().map(JsonSchemaDiagnostic::pointer).toList(), document);
            assertEquals(expected.stream().map(JsonSchemaDiagnostic::keyword).toList(),
                actual.stream().map(JsonSchemaDiagnostic::keyword).toList(), document);
        }
    }

    @Test
    void shouldRelayPartialConsumptionOfAFragmentedValue()
    {
        final String document = "{\"a\":\"" + "x".repeat(200) + "\"} ";
        final String schema = "{\"type\":\"object\"}";

        final List<String> expected = fragments(JsonEx.createParser(), document);
        final List<String> actual = fragments(JsonSchema.of(schema).newParser(false, JsonEx.createParser()), document);

        assertFalse(expected.isEmpty());
        assertEquals(expected, actual);
    }

    private static List<String> fragments(
        JsonParserEx parser,
        String document)
    {
        final byte[] bytes = document.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final List<String> events = new ArrayList<>();

        int offset = 0;
        int limit = Math.min(16, bytes.length);

        while (events.size() < 1000 && !events.contains("END_DOCUMENT 0 false"))
        {
            parser.wrap(buffer, offset, limit, limit == bytes.length);

            while (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();
                final boolean scalar = event == JsonEvent.VALUE_STRING;
                final int length = scalar ? parser.getStringView().length() : 0;

                events.add(event + " " + length + " " + parser.deferredBytes());

                if (scalar && parser.deferredBytes())
                {
                    parser.consumed(Math.max(1, length / 2));
                }
            }

            offset = limit - parser.remaining();
            limit = Math.min(offset + 16, bytes.length);
        }

        return events;
    }

    private static List<JsonEvent> pull(
        String schema,
        String document,
        Mode mode,
        int window,
        List<JsonSchemaDiagnostic> diagnostics)
    {
        final JsonParserEx parser = JsonSchema.of(schema).newParser(false, JsonEx.createParser(),
            diagnostics::add);
        final byte[] bytes = document.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final List<JsonEvent> events = new ArrayList<>();

        int offset = 0;
        int limit = Math.min(window, bytes.length);

        while (true)
        {
            parser.wrap(buffer, offset, limit, limit == bytes.length);

            while (parser.hasNextEvent())
            {
                events.add(parser.nextEvent(mode));
            }

            if (limit == bytes.length && !events.isEmpty() && events.get(events.size() - 1) == JsonEvent.END_DOCUMENT)
            {
                break;
            }

            offset = limit - parser.remaining();
            limit = Math.min(offset + window, bytes.length);
        }

        return events;
    }
}
