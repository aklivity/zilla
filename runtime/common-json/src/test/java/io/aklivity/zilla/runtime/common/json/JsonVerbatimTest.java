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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonVerbatimTest
{
    @Test
    void shouldPullVerbatimBlockSpanningStructuredEventsPreservingWhitespace()
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

        // re-read the inspected run verbatim — byte-identical, insignificant whitespace preserved — with its
        // structural transcript; the separated second member is preceded by a SEPARATOR step
        JsonVerbatim verbatim = parser.getVerbatim(bytes.length);
        assertEquals("{\"id\": \"123\", \"status\": \"OK\"}", asString(verbatim.getSegment()));
        assertEquals(
            List.of(JsonStep.START_OBJECT, JsonStep.KEY_NAME, JsonStep.VALUE,
                JsonStep.SEPARATOR, JsonStep.KEY_NAME, JsonStep.VALUE, JsonStep.END_OBJECT),
            steps(verbatim));
    }

    @Test
    void shouldTranscribeOneSeparatorPerCommaAcrossArraysAndNesting()
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

        // exactly one SEPARATOR per source comma, and none before an object value, a first key/element, or a
        // close — the structure never over- or under-states the commas actually in the bytes
        JsonVerbatim verbatim = parser.getVerbatim(bytes.length);
        assertEquals("[1, {\"a\": 2}, 3]", asString(verbatim.getSegment()));
        assertEquals(
            List.of(JsonStep.START_ARRAY, JsonStep.VALUE, JsonStep.SEPARATOR, JsonStep.START_OBJECT,
                JsonStep.KEY_NAME, JsonStep.VALUE, JsonStep.END_OBJECT, JsonStep.SEPARATOR, JsonStep.VALUE,
                JsonStep.END_ARRAY),
            steps(verbatim));
    }

    @Test
    void shouldBoundVerbatimBlockToTokenBoundaryWithinLimitAndAdvanceCursor()
    {
        JsonParserEx parser = JsonEx.createParser();
        byte[] bytes = "{\"id\": \"123\"}".getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_STRING, parser.nextEvent());
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());

        // a bounded pull yields the highest whole-token prefix that fits the limit and advances the cursor by
        // exactly that, so the next pull continues the run with neither gap nor overlap; structure tracks bytes
        JsonVerbatim first = parser.getVerbatim(5);
        assertEquals("{\"id\"", asString(first.getSegment()));
        assertEquals(List.of(JsonStep.START_OBJECT, JsonStep.KEY_NAME), steps(first));

        JsonVerbatim rest = parser.getVerbatim(bytes.length);
        assertEquals(": \"123\"}", asString(rest.getSegment()));
        assertEquals(List.of(JsonStep.VALUE, JsonStep.END_OBJECT), steps(rest));

        // the run is fully drained: a further pull yields an empty block (no steps, zero-length segment)
        JsonVerbatim drained = parser.getVerbatim(bytes.length);
        assertEquals(0, drained.getSegment().capacity());
        assertEquals(List.of(), steps(drained));
    }

    private static List<JsonStep> steps(
        JsonVerbatim verbatim)
    {
        List<JsonStep> steps = new ArrayList<>();
        Iterator<JsonStep> iterator = verbatim.getSteps();
        while (iterator.hasNext())
        {
            steps.add(iterator.next());
        }
        return steps;
    }

    private static String asString(
        DirectBufferEx buffer)
    {
        byte[] copy = new byte[buffer.capacity()];
        buffer.getBytes(0, copy);
        return new String(copy, UTF_8);
    }
}
