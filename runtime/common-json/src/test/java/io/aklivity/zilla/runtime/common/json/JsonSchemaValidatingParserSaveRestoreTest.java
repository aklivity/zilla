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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
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

        final List<List<String>> traces = interleaved(schema, new String[] {FIRST, INVALID}, 256);

        assertTrue(traces.get(0).stream().noneMatch(event -> event.startsWith("INVALID")), "valid " + traces.get(0));
        assertEquals(1, traces.get(1).stream().filter(event -> event.startsWith("INVALID")).count(), "invalid " + traces.get(1));
    }

    @Test
    void shouldRestoreIntoAnotherParserBuiltFromTheSameSchema()
    {
        final JsonSchema schema = JsonSchema.of(SCHEMA);
        final byte[] bytes = FIRST.getBytes(UTF_8);

        for (int split = 1; split < bytes.length; split++)
        {
            assertEquals(twoWindows(schema, bytes, split, false), twoWindows(schema, bytes, split, true), "split " + split);
        }
    }

    @Test
    void shouldRestoreIntoAParserThatNeitherThrowsNorReportsDiagnostics()
    {
        final JsonSchema schema = JsonSchema.of(SCHEMA);
        final byte[] bytes = FIRST.getBytes(UTF_8);

        for (int split = 1; split < bytes.length; split++)
        {
            final List<String> unsplit = silentTwoWindows(schema, bytes, split, false);
            final List<String> restored = silentTwoWindows(schema, bytes, split, true);
            assertEquals(unsplit, restored, "split " + split);
        }
    }

    @Test
    void shouldRejectARecordOfAParserWithDifferentDiagnostics()
    {
        final JsonSchema schema = JsonSchema.of(SCHEMA);
        final byte[] bytes = FIRST.getBytes(UTF_8);
        final UnsafeBufferEx record = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);

        final JsonParserEx silent = schema.newParser(false, JsonEx.createParser());
        silent.wrap(new UnsafeBufferEx(bytes), 0, 31, false);
        pull(silent, new ArrayList<>());
        silent.save(record, RECORD_OFFSET);

        final JsonParserEx throwing = schema.newParser(true, JsonEx.createParser());
        assertThrows(IllegalStateException.class, () -> throwing.restore(record, RECORD_OFFSET));
    }

    @Test
    void shouldNotSaveWhileAnOpenFrameHoldsStateThatCannotBeSaved()
    {
        assertSaveRefused("{\"uniqueItems\":true}", "[1,2", IllegalStateException.class, "uniqueItems");
        assertSaveRefused("{\"properties\":{\"a\":{\"const\":{\"x\":[1,2]}}}}", "{\"a\":{\"x\":[1,",
            IllegalStateException.class, "const");
        assertSaveRefused("{\"properties\":{\"a\":{\"enum\":[[1,2],[3]]}}}", "{\"a\":[1,",
            IllegalStateException.class, "enum");
        assertSaveRefused("{\"properties\":{\"a\":{\"unevaluatedProperties\":false}}}", "{\"a\":{\"x\":1",
            IllegalStateException.class, "unevaluated");
    }

    @Test
    void shouldNotSaveAParserWhoseSchemaIsNotEvaluatedOnFrames()
    {
        assertSaveRefused("{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\"}", "{\"a\":",
            UnsupportedOperationException.class, "");
    }

    @Test
    void shouldSaveWithoutAllocating()
    {
        final JsonSchema schema = JsonSchema.of(SCHEMA);
        final UnsafeBufferEx record = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);
        final byte[] bytes = FIRST.getBytes(UTF_8);

        for (boolean diagnostics : new boolean[] {false, true})
        {
            final JsonParserEx parser = diagnostics
                ? schema.newParser(false, JsonEx.createParser(), d ->
                {
                })
                : schema.newParser(false, JsonEx.createParser());
            parser.wrap(new UnsafeBufferEx(bytes), 0, 40, false);
            pull(parser, new ArrayList<>());

            for (int round = 0; round < 50_000; round++)
            {
                parser.save(record, RECORD_OFFSET);
            }

            final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            final long threadId = Thread.currentThread().threadId();
            long allocated = Long.MAX_VALUE;
            for (int sample = 0; sample < 5; sample++)
            {
                final long before = threads.getThreadAllocatedBytes(threadId);
                for (int round = 0; round < 1_000; round++)
                {
                    parser.save(record, RECORD_OFFSET);
                }
                allocated = Math.min(allocated, threads.getThreadAllocatedBytes(threadId) - before);
            }

            assertEquals(0L, allocated, "save allocated " + allocated + " B over 1000 saves at best, diagnostics " + diagnostics);
        }
    }

    private static List<String> twoWindows(
        JsonSchema schema,
        byte[] bytes,
        int split,
        boolean save)
    {
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
        final UnsafeBufferEx record = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);
        final List<String> trace = new ArrayList<>();

        JsonParserEx parser = schema.newParser(true, JsonEx.createParser());
        parser.wrap(input, 0, split, false);
        final boolean valid = pull(parser, trace);
        final int progress = split - parser.remaining();
        if (valid)
        {
            if (save)
            {
                parser.save(record, RECORD_OFFSET);
                parser = schema.newParser(true, JsonEx.createParser());
                parser.restore(record, RECORD_OFFSET);
            }
            parser.wrap(input, progress, bytes.length, true);
            pull(parser, trace);
        }

        return trace;
    }

    @Test
    void shouldRestoreWithoutAllocating()
    {
        final JsonSchema schema = JsonSchema.of(SCHEMA);
        final UnsafeBufferEx record = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);
        final byte[] bytes = FIRST.getBytes(UTF_8);

        for (boolean diagnostics : new boolean[] {false, true})
        {
            final JsonParserEx parser = diagnostics
                ? schema.newParser(false, JsonEx.createParser(), d ->
                {
                })
                : schema.newParser(false, JsonEx.createParser());
            parser.wrap(new UnsafeBufferEx(bytes), 0, 40, false);
            pull(parser, new ArrayList<>());
            parser.save(record, RECORD_OFFSET);

            for (int round = 0; round < 50_000; round++)
            {
                parser.restore(record, RECORD_OFFSET);
            }

            final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            final long threadId = Thread.currentThread().threadId();
            long allocated = Long.MAX_VALUE;
            for (int sample = 0; sample < 5; sample++)
            {
                final long before = threads.getThreadAllocatedBytes(threadId);
                for (int round = 0; round < 1_000; round++)
                {
                    parser.restore(record, RECORD_OFFSET);
                }
                allocated = Math.min(allocated, threads.getThreadAllocatedBytes(threadId) - before);
            }

            assertEquals(0L, allocated,
                "restore allocated " + allocated + " B over 1000 restores at best, diagnostics " + diagnostics);
        }
    }

    @Test
    void shouldRestoreThePointerOfAKeyOfAnyCharacters()
    {
        final JsonSchema schema = JsonSchema.of("{\"additionalProperties\":{\"type\":\"number\"}}");
        final byte[] bytes = "{\"na\u00efve\u20ac\ud83d\ude00\":\"x\",\"a/b\":true} ".getBytes(UTF_8);

        for (int split = 1; split < bytes.length; split++)
        {
            assertEquals(pointers(schema, bytes, split, false), pointers(schema, bytes, split, true), "split " + split);
        }
        assertTrue(pointers(schema, bytes, 17, true).contains("/na\u00efve\u20ac\ud83d\ude00 type"),
            "got " + pointers(schema, bytes, 17, true));
    }

    private static List<String> pointers(
        JsonSchema schema,
        byte[] bytes,
        int split,
        boolean save)
    {
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
        final UnsafeBufferEx record = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);
        final List<String> reported = new ArrayList<>();

        JsonParserEx parser = schema.newParser(false, JsonEx.createParser(), d -> reported.add(d.pointer() + " " + d.keyword()));
        parser.wrap(input, 0, split, false);
        pull(parser, new ArrayList<>());
        final int progress = split - parser.remaining();
        if (save)
        {
            parser.save(record, RECORD_OFFSET);
            parser = schema.newParser(false, JsonEx.createParser(), d -> reported.add(d.pointer() + " " + d.keyword()));
            parser.restore(record, RECORD_OFFSET);
        }
        parser.wrap(input, progress, bytes.length, true);
        pull(parser, new ArrayList<>());

        return reported;
    }

    private static List<String> silentTwoWindows(
        JsonSchema schema,
        byte[] bytes,
        int split,
        boolean save)
    {
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
        final UnsafeBufferEx record = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);
        final List<String> trace = new ArrayList<>();

        JsonParserEx parser = schema.newParser(false, JsonEx.createParser());
        parser.wrap(input, 0, split, false);
        pull(parser, trace);
        final int progress = split - parser.remaining();
        if (save)
        {
            parser.save(record, RECORD_OFFSET);
            parser = schema.newParser(false, JsonEx.createParser());
            parser.restore(record, RECORD_OFFSET);
        }
        parser.wrap(input, progress, bytes.length, true);
        pull(parser, trace);

        return trace;
    }

    private static void assertSaveRefused(
        String schemaText,
        String window,
        Class<? extends RuntimeException> expected,
        String mentions)
    {
        final JsonSchema schema = JsonSchema.of(schemaText);
        final JsonParserEx parser = schema.newParser(false, JsonEx.createParser());
        final byte[] bytes = window.getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, false);
        pull(parser, new ArrayList<>());

        final RuntimeException refusal = assertThrows(expected,
            () -> parser.save(new UnsafeBufferEx(new byte[RECORD_CAPACITY]), RECORD_OFFSET), schemaText);
        assertTrue(refusal.getMessage() == null || refusal.getMessage().contains(mentions),
            "message " + refusal.getMessage() + " for " + schemaText);
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
