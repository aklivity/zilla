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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonVerbatimTest
{
    @Test
    void shouldDescribeRunSpanningStructuredEventsPreservingWhitespace()
    {
        JsonParserEx parser = JsonEx.createParser();
        byte[] bytes = "{\"id\": \"123\", \"status\": \"OK\"}".getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        // drive the structured events a validator inspects to apply schema rules
        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_STRING, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_STRING, parser.nextEvent());
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());

        // re-read the inspected run verbatim, byte-identical with insignificant whitespace preserved; the run
        // opens and closes its own object, so it leaves nothing open and closes nothing that was open before
        JsonVerbatim verbatim = parser.getVerbatim(bytes.length);
        assertEquals("{\"id\": \"123\", \"status\": \"OK\"}", asString(verbatim.getSegment()));
        assertEquals(0, verbatim.getOffset());
        assertEquals("leading=START_OBJECT closed=0 opened=0 arrays=0 occupied=0 occupies=false bare=true last=VALUE",
            describe(verbatim));
    }

    @Test
    void shouldDescribeRunWithNestedContainersAndSeparators()
    {
        JsonParserEx parser = JsonEx.createParser();
        byte[] bytes = "[1, {\"a\": 2}, 3]".getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_ARRAY, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals(JsonEvent.END_ARRAY, parser.nextEvent());

        JsonVerbatim verbatim = parser.getVerbatim(bytes.length);
        assertEquals("[1, {\"a\": 2}, 3]", asString(verbatim.getSegment()));
        assertEquals("leading=START_ARRAY closed=0 opened=0 arrays=0 occupied=0 occupies=false bare=true last=VALUE",
            describe(verbatim));
    }

    @Test
    void shouldRepeatRunDescriptionOnEveryChunkAndCutBytesAnywhere()
    {
        JsonParserEx parser = JsonEx.createParser();
        byte[] bytes = "{\"id\": \"123\"}".getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_STRING, parser.nextEvent());
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());

        // a bounded pull yields the next bytes up to the limit wherever they end and advances the cursor by
        // exactly that, so the next pull continues the run with neither gap nor overlap; each chunk reports
        // where it sits in the run and repeats the structure of the whole run
        String run = "leading=START_OBJECT closed=0 opened=0 arrays=0 occupied=0 occupies=false bare=true last=VALUE";
        JsonVerbatim first = parser.getVerbatim(6);
        assertEquals("{\"id\":", asString(first.getSegment()));
        assertEquals(0, first.getOffset());
        assertEquals(run, describe(first));

        JsonVerbatim rest = parser.getVerbatim(bytes.length);
        assertEquals(" \"123\"}", asString(rest.getSegment()));
        assertEquals(6, rest.getOffset());
        assertEquals(run, describe(rest));

        // the run is fully drained: a further pull yields an empty chunk of a new run that holds no token
        JsonVerbatim drained = parser.getVerbatim(bytes.length);
        assertEquals(0, drained.getSegment().capacity());
        assertEquals(0, drained.getOffset());
        assertNull(drained.getLeading());
    }

    @Test
    void shouldDescribeContainersLeftOpen()
    {
        JsonParserEx parser = JsonEx.createParser();
        byte[] bytes = "{\"a\":[1,2]}".getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.START_ARRAY, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());

        // the object holds the key, the array holds the element: outermost first, bit 0 the object
        JsonVerbatim verbatim = parser.getVerbatim(bytes.length);
        assertEquals("leading=START_OBJECT closed=0 opened=2 arrays=2 occupied=3 occupies=false bare=true last=VALUE",
            describe(verbatim));
    }

    @Test
    void shouldDescribeContainersClosedThatWereOpenBeforeTheRun()
    {
        JsonParserEx parser = JsonEx.createParser();
        byte[] bytes = "{\"a\":[1],\"b\":2}".getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.START_ARRAY, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        parser.getVerbatim(bytes.length);

        // the run closes the array and names a key in the object it returns to, then the value of that key
        assertEquals(JsonEvent.END_ARRAY, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        JsonVerbatim verbatim = parser.getVerbatim(bytes.length);
        assertEquals("leading=END_ARRAY closed=1 opened=0 arrays=0 occupied=0 occupies=true bare=false last=VALUE",
            describe(verbatim));

        // closing the object as well leaves nothing of the run in the container it was in
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());
        JsonVerbatim closing = parser.getVerbatim(bytes.length);
        assertEquals("leading=END_OBJECT closed=1 opened=0 arrays=0 occupied=0 occupies=false bare=false last=null",
            describe(closing));
    }

    @Test
    void shouldDescribeLeadingSeparatorAndKeyAwaitingItsValue()
    {
        JsonParserEx parser = JsonEx.createParser();
        byte[] bytes = "{\"a\":1,\"b\":2}".getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        parser.getVerbatim(bytes.length);

        // a comma precedes the key, so the bytes carry it; the run ends on the key, its value still to come
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        JsonVerbatim verbatim = parser.getVerbatim(bytes.length);
        assertEquals(JsonStep.SEPARATOR, verbatim.getLeading());
        assertEquals(JsonStep.KEY_NAME, verbatim.getLast());
        assertEquals(0, verbatim.getClosed());
        assertEquals(0, verbatim.getOpened());
        assertEquals(true, verbatim.isOccupied());
        assertEquals(false, verbatim.isBareValue());
    }

    private static String describe(
        JsonVerbatim verbatim)
    {
        return "leading=" + verbatim.getLeading() +
            " closed=" + verbatim.getClosed() +
            " opened=" + verbatim.getOpened() +
            " arrays=" + verbatim.getOpenedArrays() +
            " occupied=" + verbatim.getOpenedOccupied() +
            " occupies=" + verbatim.isOccupied() +
            " bare=" + verbatim.isBareValue() +
            " last=" + verbatim.getLast();
    }

    private static String asString(
        DirectBufferEx buffer)
    {
        byte[] copy = new byte[buffer.capacity()];
        buffer.getBytes(0, copy);
        return new String(copy, UTF_8);
    }
}
