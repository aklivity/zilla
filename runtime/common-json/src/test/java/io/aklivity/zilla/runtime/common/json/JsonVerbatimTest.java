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

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class JsonVerbatimTest
{
    @Test
    void shouldPullVerbatimBytesSpanningStructuredEventsPreservingWhitespace()
    {
        JsonParserEx parser = JsonEx.createParser();
        byte[] bytes = "{\"id\": \"123\", \"status\": \"OK\"}".getBytes(UTF_8);
        parser.wrap(new UnsafeBuffer(bytes), 0, bytes.length);

        // drive the structured events a validator inspects to apply schema rules
        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_STRING, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_STRING, parser.nextEvent());
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());

        // re-read the inspected run verbatim — byte-identical, insignificant whitespace preserved
        DirectBuffer verbatim = parser.getVerbatim(bytes.length);
        assertEquals("{\"id\": \"123\", \"status\": \"OK\"}", asString(verbatim));
    }

    @Test
    void shouldBoundVerbatimPullByLimitAndAdvanceCursor()
    {
        JsonParserEx parser = JsonEx.createParser();
        byte[] bytes = "{\"id\": \"123\"}".getBytes(UTF_8);
        parser.wrap(new UnsafeBuffer(bytes), 0, bytes.length);

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_STRING, parser.nextEvent());
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());

        // a bounded pull returns at most `limit` source bytes and advances the cursor by exactly that,
        // so the next pull continues the run — no overlap, no gap
        assertEquals("{\"id\"", asString(parser.getVerbatim(5)));
        assertEquals(": \"123\"}", asString(parser.getVerbatim(bytes.length)));
    }

    private static String asString(
        DirectBuffer buffer)
    {
        byte[] copy = new byte[buffer.capacity()];
        buffer.getBytes(0, copy);
        return new String(copy, UTF_8);
    }
}
