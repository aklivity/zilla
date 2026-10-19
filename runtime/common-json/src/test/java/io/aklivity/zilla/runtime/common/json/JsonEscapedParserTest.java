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

import static io.aklivity.zilla.runtime.common.json.JsonEscapedFixture.drive;
import static io.aklivity.zilla.runtime.common.json.JsonEscapedFixture.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonException;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEscapedFixture.Outcome;
import io.aklivity.zilla.runtime.common.json.JsonEscapedFixture.Recorder;
import io.aklivity.zilla.runtime.common.json.JsonEscapedFixture.Unescaper;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink.Delivery;

class JsonEscapedParserTest
{
    private static final int WHOLE = 4096;
    private static final int BOUND = 4096;

    @Test
    void shouldUnescapeStringifiedObject()
    {
        final Outcome outcome = unescape("{\"a\":\"{\\\"b\\\":1}\"}", WHOLE, BOUND, "a");

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals("{\"a\":{\"b\":1}}", outcome.output());
    }

    @Test
    void shouldUnescapeStringifiedArray()
    {
        final Outcome outcome = unescape("{\"a\":\"[1,\\\"x\\\",true]\"}", WHOLE, BOUND, "a");

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals("{\"a\":[1,\"x\",true]}", outcome.output());
    }

    @Test
    void shouldUnescapeStringifiedScalars()
    {
        assertEquals("{\"a\":42}", unescape("{\"a\":\"42\"}", WHOLE, BOUND, "a").output());
        assertEquals("{\"a\":\"x\"}", unescape("{\"a\":\"\\\"x\\\"\"}", WHOLE, BOUND, "a").output());
        assertEquals("{\"a\":true}", unescape("{\"a\":\"true\"}", WHOLE, BOUND, "a").output());
        assertEquals("{\"a\":false}", unescape("{\"a\":\"false\"}", WHOLE, BOUND, "a").output());
        assertEquals("{\"a\":null}", unescape("{\"a\":\"null\"}", WHOLE, BOUND, "a").output());
    }

    @Test
    void shouldDeliverMarkersAroundObject()
    {
        final Outcome outcome = record(stream(true, "a"), new Recorder(), "{\"a\":\"{\\\"b\\\":1}\"}", WHOLE);

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(b) VALUE_NUMBER(1) END_OBJECT " +
            "END_ESCAPED END_OBJECT", outcome.output());
    }

    @Test
    void shouldDeliverMarkersAroundArray()
    {
        final Outcome outcome = record(stream(true, "a"), new Recorder(), "{\"a\":\"[1,2]\"}", WHOLE);

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals("START_OBJECT KEY_NAME(a) START_ESCAPED START_ARRAY VALUE_NUMBER(1) VALUE_NUMBER(2) END_ARRAY " +
            "END_ESCAPED END_OBJECT", outcome.output());
    }

    @Test
    void shouldDeliverMarkersAroundScalar()
    {
        final Outcome outcome = record(stream(true, "a"), new Recorder(), "{\"a\":\"\\\"x\\\"\"}", WHOLE);

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals("START_OBJECT KEY_NAME(a) START_ESCAPED VALUE_STRING(x) END_ESCAPED END_OBJECT", outcome.output());
    }

    @Test
    void shouldDeliverNoMarkersUnlessRequested()
    {
        final Outcome outcome = record(stream(true), new Recorder(), "{\"a\":\"{\\\"b\\\":1}\"}", WHOLE);

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals("START_OBJECT KEY_NAME(a) VALUE_STRING({\"b\":1}) END_OBJECT", outcome.output());
    }

    @Test
    void shouldDeliverNoEventsForEmptyContent()
    {
        for (int window = 1; window <= 20; window++)
        {
            final Outcome outcome = record(stream(true, "a"), new Recorder(), "{\"a\":\"\"}", window);

            assertEquals(Status.COMPLETED, outcome.status(), "window " + window);
            assertEquals("START_OBJECT KEY_NAME(a) START_ESCAPED END_ESCAPED END_OBJECT", outcome.output(), "window " + window);
        }
    }

    @Test
    void shouldIgnoreWhitespaceAroundContent()
    {
        final Outcome outcome = unescape("{\"a\":\" \\n\\t {\\\"b\\\":1}\\r\\n \"}", WHOLE, BOUND, "a");

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals("{\"a\":{\"b\":1}}", outcome.output());
    }

    @Test
    void shouldDecodeEscapeSequencesOfTheString()
    {
        // the document the string holds carries an escaped e-acute and an escaped newline of its own, while the
        // string carries an escaped e-acute and a pair of surrogate escapes, which a window can split between halves
        final String json = "{\"a\":\"{\\\"b\\\":\\\"\\u00e9 \\\\u00e9 \\\\n \\ud83d\\ude00\\\"}\"}";
        final String expected = "{\"a\":{\"b\":\"é é \\n 😀\"}}";

        for (int window = 1; window <= json.length() + 1; window++)
        {
            final Outcome outcome = unescape(json, window, BOUND, "a");

            assertEquals(Status.COMPLETED, outcome.status(), "window " + window);
            assertEquals(expected, outcome.output(), "window " + window);
        }
    }

    @Test
    void shouldUnescapeAtEveryWindowAndBound()
    {
        final String json = "{\"x\":1,\"a\":\"{\\\"b\\\":[1,2,{\\\"c\\\":\\\"caf\\u00e9 \\\\u00e9 \\ud83d\\ude00 \\\\\\\\ " +
            "\\\\\\\"q\\\\\\\"\\\"}],\\\"d\\\":null}\",\"y\":true}";
        final String expected = "{\"x\":1,\"a\":{\"b\":[1,2,{\"c\":\"café é 😀 \\\\ \\\"q\\\"\"}]," +
            "\"d\":null},\"y\":true}";

        for (int window = 1; window <= json.length() + 1; window++)
        {
            for (int bound : new int[] {24, 64, BOUND})
            {
                final Outcome outcome = unescape(json, window, bound, "a");

                assertEquals(Status.COMPLETED, outcome.status(), "window " + window + " bound " + bound);
                assertEquals(expected, outcome.output(), "window " + window + " bound " + bound);
            }
        }
    }

    @Test
    void shouldUnescapeDocumentLargerThanWindow()
    {
        final String value = "x".repeat(300);
        final String json = "{\"a\":\"{\\\"k\\\":\\\"" + value + "\\\",\\\"n\\\":" + "9".repeat(40) + "}\"}";
        final String expected = "{\"a\":{\"k\":\"" + value + "\",\"n\":" + "9".repeat(40) + "}}";

        for (int window : new int[] {7, 64, 128, 301})
        {
            final Outcome outcome = unescape(json, window, 96, "a");

            assertEquals(Status.COMPLETED, outcome.status(), "window " + window);
            assertEquals(expected, outcome.output(), "window " + window);
        }
    }

    @Test
    void shouldUnescapeNestedDocuments()
    {
        final String json = "{\"a\":\"{\\\"c\\\":\\\"{\\\\\\\"d\\\\\\\":1}\\\"}\"}";

        for (int window = 1; window <= json.length() + 1; window++)
        {
            final Outcome outcome = unescape(json, window, BOUND, "a", "c");

            assertEquals(Status.COMPLETED, outcome.status(), "window " + window);
            assertEquals("{\"a\":{\"c\":{\"d\":1}}}", outcome.output(), "window " + window);
        }
    }

    @Test
    void shouldDeliverNestedMarkers()
    {
        final String json = "{\"a\":\"{\\\"c\\\":\\\"{\\\\\\\"d\\\\\\\":1}\\\"}\"}";

        final Outcome outcome = record(stream(true, "a", "c"), new Recorder(), json, WHOLE);

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(c) START_ESCAPED START_OBJECT " +
            "KEY_NAME(d) VALUE_NUMBER(1) END_OBJECT END_ESCAPED END_OBJECT END_ESCAPED END_OBJECT", outcome.output());
    }

    @Test
    void shouldUnescapeOnlyRequestedKeys()
    {
        final Outcome outcome = unescape("{\"a\":\"{\\\"b\\\":1}\",\"s\":\"{\\\"b\\\":1}\"}", WHOLE, BOUND, "a");

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals("{\"a\":{\"b\":1},\"s\":\"{\\\"b\\\":1}\"}", outcome.output());
    }

    @Test
    void shouldReusePipelineAcrossDocuments()
    {
        final JsonGeneratorEx generator = JsonEx.createGenerator();
        final MutableDirectBufferEx output = new UnsafeBufferEx(new byte[BOUND]);
        final JsonPipeline pipeline = stream(false, "a")
            .into(JsonEx.createSink(generator, Map.of(JsonSink.DELIVERY, Delivery.STRUCTURED)));

        final List<String> outputs = new ArrayList<>();
        for (String json : new String[] {"{\"a\":\"{\\\"b\\\":1}\"}", "{\"a\":2}", "{\"a\":\"[3]\"}", "{\"a\":\"\\\"s\\\"\"}"})
        {
            final Outcome outcome = drive(pipeline, generator, output, json, 5, BOUND);
            outputs.add(outcome.status() == Status.COMPLETED ? outcome.output() : outcome.status().name());
        }

        assertEquals(List.of("{\"a\":{\"b\":1}}", "REJECTED", "{\"a\":[3]}", "{\"a\":\"s\"}"), outputs);
    }

    @Test
    void shouldLocateWithinDecodedContent()
    {
        final List<Long> offsets = new ArrayList<>();
        final JsonTransform locator = new JsonTransform()
        {
            @Override
            public Status transform(
                JsonController control,
                JsonSource source,
                JsonEvent event,
                JsonSink sink)
            {
                if (event == JsonEvent.END_OBJECT)
                {
                    offsets.add(source.getLocation().getStreamOffset());
                }
                return sink.transform(control, source, event);
            }

            @Override
            public boolean deterministic()
            {
                return true;
            }
        };
        final JsonStream stream = JsonEx.stream(JsonEx.createParser())
            .transform(new Unescaper(true, "a"))
            .transform(locator);

        final Outcome outcome = record(stream, new Recorder(), "{\"a\":\"{\\\"b\\\":1}\"}", WHOLE);

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals(List.of(7L, 17L), offsets);
    }

    @Test
    void shouldRejectUnescapeOfNonString()
    {
        for (String json : new String[] {"{\"a\":1}", "{\"a\":{\"b\":1}}", "{\"a\":[1]}", "{\"a\":true}", "{\"a\":null}"})
        {
            for (int window : new int[] {1, 3, WHOLE})
            {
                assertEquals(Status.REJECTED, unescape(json, window, BOUND, "a").status(), json + " window " + window);
            }
        }
    }

    @Test
    void shouldRejectMalformedContent()
    {
        for (String content : new String[] {"{\\\"b\\\":}", "{\\\"b\\\" 1}", "[1,]", "{b:1}", "nul", "{\\\"b\\\":1}}", "]"})
        {
            for (int window : new int[] {1, 3, 8, WHOLE})
            {
                final String json = "{\"a\":\"" + content + "\"}";
                assertEquals(Status.REJECTED, unescape(json, window, BOUND, "a").status(), json + " window " + window);
            }
        }
    }

    @Test
    void shouldRejectIncompleteContent()
    {
        for (String content : new String[] {"{\\\"b\\\":1", "[1,2", "{", "\\\"x", "  "})
        {
            for (int window : new int[] {1, 3, 8, WHOLE})
            {
                final String json = "{\"a\":\"" + content + "\"}";
                assertEquals(Status.REJECTED, unescape(json, window, BOUND, "a").status(), json + " window " + window);
            }
        }
    }

    @Test
    void shouldRejectContentWithTrailingValue()
    {
        for (String content : new String[] {"1 2", "{} {}", "{\\\"b\\\":1} x"})
        {
            for (int window : new int[] {1, 3, 8, WHOLE})
            {
                final String json = "{\"a\":\"" + content + "\"}";
                assertEquals(Status.REJECTED, unescape(json, window, BOUND, "a").status(), json + " window " + window);
            }
        }
    }

    @Test
    void shouldRejectEscapedFromStageThatCannotParseIt()
    {
        final JsonController declining = new JsonController()
        {
            @Override
            public void segmentable()
            {
            }
        };

        assertThrows(JsonException.class, declining::escaped);
    }

    private static JsonStream stream(
        boolean forward,
        String... keys)
    {
        return JsonEx.stream(JsonEx.createParser()).transform(new Unescaper(forward, keys));
    }

    private static Outcome unescape(
        String json,
        int window,
        int bound,
        String... keys)
    {
        return drive(stream(false, keys), Delivery.STRUCTURED, json, window, bound);
    }
}
