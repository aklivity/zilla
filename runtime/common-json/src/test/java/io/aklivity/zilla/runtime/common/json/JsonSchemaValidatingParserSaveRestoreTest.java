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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;

class JsonSchemaValidatingParserSaveRestoreTest
{
    private static final String SCHEMA =
        "{\"type\":\"object\",\"required\":[\"id\",\"status\"],\"properties\":{" +
            "\"id\":{\"type\":\"string\",\"minLength\":1}," +
            "\"status\":{\"enum\":[\"on\",\"off\"]}," +
            "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"maxItems\":4}," +
            "\"location\":{\"type\":\"object\",\"required\":[\"lat\"]," +
            "\"properties\":{\"lat\":{\"type\":\"number\"},\"lon\":{\"type\":\"number\"}}}}," +
            "\"additionalProperties\":false}";

    private static final String FIRST =
        "{\"id\":\"lamp-1\",\"status\":\"on\",\"tags\":[\"north\",\"outdoor\"]," +
            "\"location\":{\"lat\":51.5,\"lon\":-0.12}} ";

    private static final String SECOND =
        "{\"status\":\"off\",\"location\":{\"lon\":3,\"lat\":4},\"tags\":[],\"id\":\"lamp-22\"} ";

    private static final String INVALID =
        "{\"id\":\"lamp-3\",\"tags\":[\"a\",\"b\",\"c\"],\"location\":{\"lat\":1},\"status\":\"broken\"} ";

    private static final String INVALID_NESTED =
        "{\"id\":\"lamp-4\",\"status\":\"on\",\"location\":{\"lon\":2}} ";

    private static final int[] WINDOWS = {1, 2, 3, 5, 8, 13, 21, 34, 64, 256};
    private static final int RECORD_OFFSET = 5;
    private static final int RECORD_CAPACITY = 4096;

    @Test
    void shouldValidateInterleavedStreamsOnOneParserLikeSeparateParsers()
    {
        final JsonSchema schema = JsonSchema.of(SCHEMA);
        final String[][] pairs = {{FIRST, SECOND}, {SECOND, INVALID}, {INVALID, FIRST}, {INVALID, INVALID_NESTED}};

        for (String[] pair : pairs)
        {
            for (int window : WINDOWS)
            {
                final List<List<String>> expected = new ArrayList<>();
                for (String document : pair)
                {
                    expected.add(separate(schema, document, window));
                }

                assertEquals(expected, interleaved(schema, pair, window), "window " + window + " of " + pair[0] + pair[1]);
            }
        }
    }

    @Test
    void shouldReportFailureOnlyOnTheStreamThatFailed()
    {
        final JsonSchema schema = JsonSchema.of(SCHEMA);

        final List<List<String>> traces = interleaved(schema, new String[] {FIRST, INVALID}, 8);

        assertTrue(traces.get(0).stream().noneMatch(event -> event.startsWith("INVALID")), "valid " + traces.get(0));
        assertEquals(1, traces.get(1).stream().filter(event -> event.startsWith("INVALID")).count(), "invalid " + traces.get(1));
    }

    private static List<List<String>> interleaved(
        JsonSchema schema,
        String[] documents,
        int window)
    {
        final JsonParserEx parser = schema.newParser(true, JsonEx.createParser());
        final int streams = documents.length;
        final UnsafeBufferEx[] inputs = new UnsafeBufferEx[streams];
        final UnsafeBufferEx[] records = new UnsafeBufferEx[streams];
        final int[] progress = new int[streams];
        final int[] limits = new int[streams];
        final boolean[] failed = new boolean[streams];
        final List<List<String>> traces = new ArrayList<>();

        for (int stream = 0; stream < streams; stream++)
        {
            inputs[stream] = new UnsafeBufferEx(documents[stream].getBytes(UTF_8));
            records[stream] = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);
            parser.reset();
            parser.save(records[stream], RECORD_OFFSET);
            traces.add(new ArrayList<>());
        }

        boolean pending = true;
        while (pending)
        {
            pending = false;
            for (int stream = 0; stream < streams; stream++)
            {
                final int length = inputs[stream].capacity();
                if (!failed[stream] && progress[stream] < length)
                {
                    final int limit = Math.min(length, limits[stream] + window);
                    limits[stream] = limit;

                    parser.restore(records[stream], RECORD_OFFSET);
                    parser.wrap(inputs[stream], progress[stream], limit, limit == length);
                    failed[stream] = !pull(parser, traces.get(stream));
                    progress[stream] = limit - parser.remaining();
                    parser.save(records[stream], RECORD_OFFSET);

                    pending |= !failed[stream] && progress[stream] < length;
                }
            }
        }

        return traces;
    }

    private static List<String> separate(
        JsonSchema schema,
        String document,
        int window)
    {
        final JsonParserEx parser = schema.newParser(true, JsonEx.createParser());
        final UnsafeBufferEx input = new UnsafeBufferEx(document.getBytes(UTF_8));
        final List<String> trace = new ArrayList<>();

        int progress = 0;
        int limit = 0;
        boolean ok = true;
        while (ok && progress < input.capacity())
        {
            limit = Math.min(input.capacity(), limit + window);
            parser.wrap(input, progress, limit, limit == input.capacity());
            ok = pull(parser, trace);
            progress = limit - parser.remaining();
        }

        return trace;
    }

    private static boolean pull(
        JsonParserEx parser,
        List<String> trace)
    {
        boolean ok = true;
        try
        {
            while (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent(Mode.STRUCTURED);
                switch (event)
                {
                case VALUE_STRING:
                case VALUE_NUMBER:
                    parser.consumed(parser.getStringView().length());
                    trace.add(event.name());
                    break;
                default:
                    trace.add(event.name());
                    break;
                }
            }
        }
        catch (JsonValidationException ex)
        {
            trace.add("INVALID " + ex.diagnostics());
            ok = false;
        }
        return ok;
    }
}
