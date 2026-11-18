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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;

class JsonVerbatimBoundaryTest
{
    private static final int RECORD_CAPACITY = 256;
    private static final int[] WINDOWS = {1, 2, 3, 5, 8, 13, 21, 64};
    private static final int ELEMENTS = 50_000;
    private static final int WARMUP_ROUNDS = 5;
    private static final int LOOP_GUARD = 10_000;

    private static final String[] CORPUS =
    {
        "{\"id\": \"123\", \"status\": \"OK\"}",
        "[1, {\"a\": 2}, 3, [], {}, \"x\\\"y\", true, null, -1.5e3]",
        "  { \"k\" : [ 1 , 2 ] ,\n \"escaped\\n\" : \"v\\u0041l\" , \"n\" : 12345 }  ",
        "{\"nested\":{\"deep\":[{\"a\":\"b\"},{\"c\":[1,2,3]}]},\"last\":false}",
        "\"scalar string\"",
        "1234567890"
    };

    @Test
    void shouldNotRetainPerEventStateWhileNotReadingVerbatim()
    {
        final byte[] bytes = array().getBytes(UTF_8);
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
        final JsonParserEx parser = JsonEx.createParser();

        for (int round = 0; round < WARMUP_ROUNDS; round++)
        {
            pull(parser, input, bytes.length);
        }

        final com.sun.management.ThreadMXBean threads =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final long threadId = Thread.currentThread().threadId();
        final long before = threads.getThreadAllocatedBytes(threadId);
        final int events = pull(JsonEx.createParser(), input, bytes.length);
        final long perEvent = (threads.getThreadAllocatedBytes(threadId) - before) / events;

        assertTrue(perEvent <= 4, "pulling events allocates " + perEvent + " B per event");
    }

    @Test
    void shouldTranscribeSameBytesAndStepsAtEveryWindowSize()
    {
        for (String document : CORPUS)
        {
            final byte[] bytes = (document + " ").getBytes(UTF_8);
            final Transcript expected = transcribe(bytes, new int[] {bytes.length}, false);
            assertEquals(new String(bytes, UTF_8), expected.bytes.toString());

            for (int window : WINDOWS)
            {
                final Transcript actual = transcribe(bytes, limits(bytes.length, window), false);
                assertEquals(expected.bytes.toString(), actual.bytes.toString(), "bytes, window " + window + " of " + document);
                assertEquals(expected.steps, actual.steps, "steps, window " + window + " of " + document);
            }
        }
    }

    @Test
    void shouldTranscribeSameBytesAndStepsAcrossSaveAndRestore()
    {
        for (String document : CORPUS)
        {
            final byte[] bytes = (document + " ").getBytes(UTF_8);
            final Transcript expected = transcribe(bytes, new int[] {bytes.length}, false);

            for (int window : WINDOWS)
            {
                final Transcript actual = transcribe(bytes, limits(bytes.length, window), true);
                assertEquals(expected.bytes.toString(), actual.bytes.toString(), "bytes, window " + window + " of " + document);
                assertEquals(expected.steps, actual.steps, "steps, window " + window + " of " + document);
            }
        }
    }

    @Test
    void shouldTranscribeSameBytesAndStepsWhenPulledInBoundedBlocks()
    {
        for (String document : CORPUS)
        {
            final byte[] bytes = (document + " ").getBytes(UTF_8);
            final Transcript expected = transcribeBounded(bytes, bytes.length);

            for (int limit = 1; limit <= bytes.length; limit++)
            {
                final Transcript actual = transcribeBounded(bytes, limit);
                assertEquals(expected.bytes.toString(), actual.bytes.toString(), "bytes, limit " + limit + " of " + document);
                assertEquals(expected.steps, actual.steps, "steps, limit " + limit + " of " + document);
            }
        }
    }

    private static int pull(
        JsonParserEx parser,
        UnsafeBufferEx input,
        int length)
    {
        parser.wrap(input, 0, length, true);
        int events = 0;
        while (parser.hasNextEvent())
        {
            parser.nextEvent(Mode.STRUCTURED);
            events++;
        }
        return events;
    }

    private static Transcript transcribe(
        byte[] bytes,
        int[] limits,
        boolean roundTrip)
    {
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
        final UnsafeBufferEx record = new UnsafeBufferEx(new byte[RECORD_CAPACITY]);
        final Transcript transcript = new Transcript();
        JsonParserEx parser = JsonEx.createParser();
        parser.verbatim();

        int progress = 0;
        for (int index = 0; index < limits.length; index++)
        {
            final int limit = limits[index];
            final boolean last = index == limits.length - 1;

            parser.wrap(input, progress, limit, last);
            int guard = 0;
            while (parser.hasNextEvent())
            {
                assertTrue(++guard < LOOP_GUARD, "event loop does not end");
                final JsonEvent event = parser.nextEvent(Mode.STRUCTURED);
                if (event == JsonEvent.VALUE_STRING || event == JsonEvent.VALUE_NUMBER || event == JsonEvent.KEY_NAME)
                {
                    parser.consumed(parser.getStringView().length());
                }
                transcript.append(parser.getVerbatim(Integer.MAX_VALUE));
            }
            transcript.append(parser.getVerbatim(Integer.MAX_VALUE));

            progress = limit - parser.remaining();

            if (roundTrip && !last)
            {
                final int length = parser.save(record, 0);
                assertTrue(length > 0);
                parser = JsonEx.createParser();
                parser.restore(record, 0);
            }
        }
        return transcript;
    }

    private static Transcript transcribeBounded(
        byte[] bytes,
        int limit)
    {
        final Transcript transcript = new Transcript();
        final JsonParserEx parser = JsonEx.createParser();
        parser.verbatim();
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        int depth = 0;
        boolean complete = false;
        while (!complete && parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent(Mode.STRUCTURED);
            depth += event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY ? 1 : 0;
            depth -= event == JsonEvent.END_OBJECT || event == JsonEvent.END_ARRAY ? 1 : 0;
            complete = depth == 0 && event != JsonEvent.START_DOCUMENT;
        }

        int bound = limit;
        int guard = 0;
        boolean drained = false;
        while (!drained)
        {
            assertTrue(++guard < LOOP_GUARD, "bounded pull does not progress at limit " + limit);
            final int before = transcript.bytes.length();
            transcript.append(parser.getVerbatim(bound));
            final int taken = transcript.bytes.length() - before;
            drained = taken == 0 && bound >= bytes.length;
            bound = taken == 0 ? bound * 2 : limit;
        }
        return transcript;
    }

    private static int[] limits(
        int length,
        int window)
    {
        final List<Integer> limits = new ArrayList<>();
        for (int limit = window; limit < length; limit += window)
        {
            limits.add(limit);
        }
        limits.add(length);
        return limits.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String array()
    {
        final StringBuilder array = new StringBuilder("[");
        for (int index = 0; index < ELEMENTS; index++)
        {
            array.append(index == 0 ? "" : ",").append(index);
        }
        return array.append("] ").toString();
    }

    private static final class Transcript
    {
        private final StringBuilder bytes = new StringBuilder();
        private final List<JsonStep> steps = new ArrayList<>();

        private void append(
            JsonVerbatim verbatim)
        {
            final DirectBufferEx segment = verbatim.getSegment();
            final byte[] copy = new byte[segment.capacity()];
            segment.getBytes(0, copy);
            bytes.append(new String(copy, UTF_8));

            final Iterator<JsonStep> iterator = verbatim.getSteps();
            while (iterator.hasNext())
            {
                steps.add(iterator.next());
            }
        }
    }
}
