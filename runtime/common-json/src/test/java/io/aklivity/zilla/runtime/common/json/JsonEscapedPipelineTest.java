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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEscapedFixture.Escaper;
import io.aklivity.zilla.runtime.common.json.JsonEscapedFixture.Misuse;
import io.aklivity.zilla.runtime.common.json.JsonEscapedFixture.Misuse.Mode;
import io.aklivity.zilla.runtime.common.json.JsonEscapedFixture.Outcome;
import io.aklivity.zilla.runtime.common.json.JsonEscapedFixture.Unescaper;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink.Delivery;

class JsonEscapedPipelineTest
{
    private static final int BOUND = 4096;

    // a document whose own text carries escaped quotes, a short escape, a control escape, a backslash, and
    // characters outside ASCII, so that stringifying it exercises every escape the generator writes
    private static final String DOCUMENT = "{\"b\":\"café \\\"q\\\" \\n \\u0001 \\\\ 😀\"," +
        "\"n\":[1,2.5,-3e2,true,false,null,{}],\"o\":{\"p\":\"\"}}";

    @Test
    void shouldEscapeObjectAsString()
    {
        for (int window = 1; window <= 24; window++)
        {
            final Outcome outcome = escape("{\"a\":{\"b\":1},\"c\":2}", window, "a");

            assertEquals(Status.COMPLETED, outcome.status(), "window " + window);
            assertEquals("{\"a\":\"{\\\"b\\\":1}\",\"c\":2}", outcome.output(), "window " + window);
        }
    }

    @Test
    void shouldEscapeArrayAndScalarsAsString()
    {
        assertEquals("{\"a\":\"[1,2]\"}", escape("{\"a\":[1,2]}", BOUND, "a").output());
        assertEquals("{\"a\":\"42\"}", escape("{\"a\":42}", BOUND, "a").output());
        assertEquals("{\"a\":\"\\\"x\\\"\"}", escape("{\"a\":\"x\"}", BOUND, "a").output());
        assertEquals("{\"a\":\"true\"}", escape("{\"a\":true}", BOUND, "a").output());
        assertEquals("{\"a\":\"null\"}", escape("{\"a\":null}", BOUND, "a").output());
    }

    @Test
    void shouldEscapeNestedScopesTwice()
    {
        final Outcome outcome = escape("{\"a\":{\"c\":{\"d\":1}}}", BOUND, "a", "c");

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals("{\"a\":\"{\\\"c\\\":\\\"{\\\\\\\"d\\\\\\\":1}\\\"}\"}", outcome.output());
    }

    @Test
    void shouldRoundTripUnescapedThenEscaped()
    {
        final String json = "{\"a\":" + quote(DOCUMENT) + ",\"z\":1}";
        final JsonStream stream = JsonEx.stream(JsonEx.createParser())
            .transform(new Unescaper(false, "a"))
            .transform(new Escaper("a"));

        final Outcome outcome = drive(stream, Delivery.STRUCTURED, json, BOUND, BOUND);

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals(json, outcome.output());
    }

    @Test
    void shouldRoundTripMarkersFromParserToGenerator()
    {
        final String json = "{\"x\":1,\"a\":" + quote(DOCUMENT) + ",\"z\":[true]}";

        for (int window = 1; window <= json.length() + 1; window += 3)
        {
            for (int bound : new int[] {32, 96, BOUND})
            {
                final JsonStream stream = JsonEx.stream(JsonEx.createParser()).transform(new Unescaper(true, "a"));

                final Outcome outcome = drive(stream, Delivery.STRUCTURED, json, window, bound);

                assertEquals(Status.COMPLETED, outcome.status(), "window " + window + " bound " + bound);
                assertEquals(json, outcome.output(), "window " + window + " bound " + bound);
            }
        }
    }

    @Test
    void shouldRoundTripNestedMarkersFromParserToGenerator()
    {
        final String inner = "{\"c\":" + quote(DOCUMENT) + ",\"e\":2}";
        final String json = "{\"a\":" + quote(inner) + "}";

        for (int window = 1; window <= json.length() + 1; window += 5)
        {
            final JsonStream stream = JsonEx.stream(JsonEx.createParser()).transform(new Unescaper(true, "a", "c"));

            final Outcome outcome = drive(stream, Delivery.STRUCTURED, json, window, 48);

            assertEquals(Status.COMPLETED, outcome.status(), "window " + window);
            assertEquals(json, outcome.output(), "window " + window);
        }
    }

    @Test
    void shouldRoundTripEmptyContent()
    {
        final JsonStream stream = JsonEx.stream(JsonEx.createParser()).transform(new Unescaper(true, "a"));

        final Outcome outcome = drive(stream, Delivery.STRUCTURED, "{\"a\":\"\",\"b\":1}", 4, BOUND);

        assertEquals(Status.COMPLETED, outcome.status());
        assertEquals("{\"a\":\"\",\"b\":1}", outcome.output());
    }

    @Test
    void shouldRejectStartWithoutEnd()
    {
        assertEquals(Status.REJECTED, misuse(Mode.START_WITHOUT_END, "{\"a\":1}").status());
    }

    @Test
    void shouldRejectEndWithoutStart()
    {
        assertEquals(Status.REJECTED, misuse(Mode.END_WITHOUT_START, "{\"a\":1}").status());
    }

    @Test
    void shouldRejectStartInKeyPosition()
    {
        assertEquals(Status.REJECTED, misuse(Mode.START_IN_KEY_POSITION, "{\"a\":1}").status());
    }

    @Test
    void shouldRejectEndWithOpenContainer()
    {
        assertEquals(Status.REJECTED, misuse(Mode.END_WITH_OPEN_CONTAINER, "{\"a\":[1]}").status());
    }

    @Test
    void shouldValidateStringifiedDocumentAsValueOfKey()
    {
        final String schema = "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"object\",\"required\":[\"b\"]," +
            "\"properties\":{\"b\":{\"type\":\"integer\"}}}}}";

        // a window narrower than the key would have the stage that matches it decline its fragments, which a
        // byte-preserving sink does not follow with or without a scope, so the windows start at the key's width
        for (int window = 4; window <= 30; window++)
        {
            final Outcome valid = validate(schema, "{\"a\":\"{\\\"b\\\":1}\",\"z\":2}", window);

            assertEquals(Status.COMPLETED, valid.status(), "window " + window);
            assertEquals("{\"a\":\"{\\\"b\\\":1}\",\"z\":2}", valid.output().stripTrailing(), "window " + window);
        }
    }

    @Test
    void shouldRejectStringifiedDocumentInvalidForSchema()
    {
        final String schema = "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"object\",\"required\":[\"b\"]," +
            "\"properties\":{\"b\":{\"type\":\"integer\"}}}}}";

        assertEquals(Status.REJECTED, validate(schema, "{\"a\":\"{\\\"b\\\":\\\"x\\\"}\"}", BOUND).status());
        assertEquals(Status.REJECTED, validate(schema, "{\"a\":\"{}\"}", BOUND).status());
        assertEquals(Status.REJECTED, validate(schema, "{\"a\":\"[]\"}", BOUND).status());
    }

    @Test
    void shouldRejectStringifiedDocumentWhenNotUnescaped()
    {
        final String schema = "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"object\"}}}";
        final JsonStream stream = JsonEx.stream(JsonEx.createParser()).transform(JsonSchema.of(schema).validator());

        final Outcome outcome = drive(stream, Delivery.STRUCTURED, "{\"a\":\"{}\"}", BOUND, BOUND);

        assertEquals(Status.REJECTED, outcome.status());
    }

    @Test
    void shouldProjectRetainedPointerWithinStringifiedDocument()
    {
        final String json = "{\"a\":\"{\\\"b\\\":1,\\\"c\\\":2}\",\"d\":3}";

        for (int window = 1; window <= json.length() + 1; window++)
        {
            assertEquals("{\"a\":\"{\\\"b\\\":1}\"}", project(List.of("/a/b"), json, window).output(), "window " + window);
        }
    }

    @Test
    void shouldProjectStringifiedDocumentAsRetainedValue()
    {
        final String json = "{\"a\":\"{\\\"b\\\":1,\\\"c\\\":2}\",\"d\":3}";

        for (int window = 1; window <= json.length() + 1; window++)
        {
            assertEquals("{\"a\":\"{\\\"b\\\":1,\\\"c\\\":2}\"}", project(List.of("/a"), json, window).output(),
                "window " + window);
        }
    }

    @Test
    void shouldProjectAwayOrEmptyStringifiedDocument()
    {
        final String json = "{\"a\":\"{\\\"b\\\":1,\\\"c\\\":2}\",\"d\":3}";

        for (int window = 1; window <= json.length() + 1; window++)
        {
            assertEquals("{\"d\":3}", project(List.of("/d"), json, window).output(), "window " + window);
            assertEquals("{\"a\":\"{}\"}", project(List.of("/a/x"), json, window).output(), "window " + window);
        }
    }

    @Test
    void shouldProjectWithinNestedStringifiedDocuments()
    {
        final String json = "{\"a\":\"{\\\"c\\\":\\\"{\\\\\\\"e\\\\\\\":1,\\\\\\\"f\\\\\\\":2}\\\"}\"}";

        for (int window = 1; window <= json.length() + 1; window += 2)
        {
            final Outcome outcome = project(List.of("/a/c/e"), json, window, "a", "c");

            assertEquals(Status.COMPLETED, outcome.status(), "window " + window);
            assertEquals("{\"a\":\"{\\\"c\\\":\\\"{\\\\\\\"e\\\\\\\":1}\\\"}\"}", outcome.output(), "window " + window);
        }
    }

    @Test
    void shouldProjectEmptyAndScalarStringifiedDocuments()
    {
        assertEquals("{\"a\":\"\"}", project(List.of("/a"), "{\"a\":\"\",\"d\":3}", 3).output());
        assertEquals("{\"d\":3}", project(List.of("/d"), "{\"a\":\"\",\"d\":3}", 3).output());
        assertEquals("{}", project(List.of("/a/b"), "{\"a\":\"\",\"d\":3}", 3).output());
        assertEquals("{}", project(List.of("/a/b"), "{\"a\":\"5\",\"d\":3}", 3).output());
        assertEquals("{\"a\":\"5\"}", project(List.of("/a"), "{\"a\":\"5\",\"d\":3}", 3).output());
    }

    @Test
    void shouldFlattenPathWithinStringifiedDocument()
    {
        final String json = "{\"a\":\"{\\\"b\\\":1,\\\"c\\\":2}\"}";

        for (int window = 1; window <= json.length() + 1; window++)
        {
            final JsonStream stream = JsonEx.stream(JsonEx.createParser())
                .transform(new Unescaper(true, "a"))
                .transform(JsonTransforms.flatten(Map.of("a.b", "b")));

            final Outcome outcome = drive(stream, Delivery.STRUCTURED, json, window, BOUND);

            assertEquals(Status.COMPLETED, outcome.status(), "window " + window);
            assertEquals("{\"b\":1}", outcome.output(), "window " + window);
        }
    }

    @Test
    void shouldFlattenStringifiedDocumentAsTarget()
    {
        final String json = "{\"a\":\"{\\\"b\\\":1,\\\"c\\\":[2]}\",\"d\":3}";

        for (int window = 1; window <= json.length() + 1; window++)
        {
            final JsonStream stream = JsonEx.stream(JsonEx.createParser())
                .transform(new Unescaper(true, "a"))
                .transform(JsonTransforms.flatten(Map.of("a", "x")));

            final Outcome outcome = drive(stream, Delivery.STRUCTURED, json, window, BOUND);

            assertEquals(Status.COMPLETED, outcome.status(), "window " + window);
            assertEquals("{\"x\":\"{\\\"b\\\":1,\\\"c\\\":[2]}\"}", outcome.output(), "window " + window);
        }
    }

    private static Outcome escape(
        String json,
        int window,
        String... keys)
    {
        final JsonStream stream = JsonEx.stream(JsonEx.createParser()).transform(new Escaper(keys));

        return drive(stream, Delivery.STRUCTURED, json, window, BOUND);
    }

    private static Outcome misuse(
        Mode mode,
        String json)
    {
        final JsonStream stream = JsonEx.stream(JsonEx.createParser()).transform(new Misuse(mode));

        return drive(stream, Delivery.STRUCTURED, json, BOUND, BOUND);
    }

    private static Outcome validate(
        String schema,
        String json,
        int window)
    {
        final JsonStream stream = JsonEx.stream(JsonEx.createParser())
            .transform(new Unescaper(true, "a"))
            .transform(JsonSchema.of(schema).validator());

        return drive(stream, Delivery.SEGMENTABLE, json, window, BOUND);
    }

    private static Outcome project(
        List<String> pointers,
        String json,
        int window,
        String... keys)
    {
        final JsonStream stream = JsonEx.stream(JsonEx.createParser())
            .transform(new Unescaper(true, keys.length != 0 ? keys : new String[] {"a"}))
            .transform(JsonTransforms.projector(pointers));

        return drive(stream, Delivery.STRUCTURED, json, window, BOUND);
    }

    private static String quote(
        String text)
    {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
