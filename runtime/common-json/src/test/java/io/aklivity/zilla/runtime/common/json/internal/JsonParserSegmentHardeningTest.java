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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEvent;

public class JsonParserSegmentHardeningTest
{
    @Test
    public void shouldSegmentAcrossThreeFrames()
    {
        final JsonParserImpl parser = new JsonParserImpl();

        wrap(parser, "{\"a\":1,");
        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        parser.segmentable();
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent());
        final String first = segment(parser);
        assertEquals("{\"a\":1,", first);
        assertTrue(parser.deferredBytes());
        assertFalse(parser.hasNextEvent());

        wrap(parser, "\"b\":2,");
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent());
        final String second = segment(parser);
        assertEquals("\"b\":2,", second);
        assertTrue(parser.deferredBytes());
        assertFalse(parser.hasNextEvent());

        wrap(parser, "\"c\":3} ");
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent());
        final String third = segment(parser);
        assertEquals("\"c\":3}", third);
        assertFalse(parser.deferredBytes());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());

        assertEquals("{\"a\":1,\"b\":2,\"c\":3}", first + second + third);
    }

    @Test
    public void shouldSegmentEmptyObject()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "{} ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        parser.segmentable();
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent());
        assertEquals("{}", segment(parser));
        assertFalse(parser.deferredBytes());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
    }

    @Test
    public void shouldSegmentEmptyArray()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "[] ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_ARRAY, parser.nextEvent());
        parser.segmentable();
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent());
        assertEquals("[]", segment(parser));
        assertFalse(parser.deferredBytes());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
    }

    @Test
    public void shouldClearSegmentStateOnResetForReuse()
    {
        final JsonParserImpl parser = new JsonParserImpl();

        wrap(parser, "{\"a\":1} ");
        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        parser.segmentable();
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent());
        assertEquals("{\"a\":1}", segment(parser));
        assertFalse(parser.deferredBytes());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());

        parser.reset();

        wrap(parser, "[1,2] ");
        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_ARRAY, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals("1", parser.getString());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals("2", parser.getString());
        assertEquals(JsonEvent.END_ARRAY, parser.nextEvent());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
    }

    @Test
    public void shouldRecoverAfterResetMidSegment()
    {
        final JsonParserImpl parser = new JsonParserImpl();

        wrap(parser, "{\"a\":1,");
        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        parser.segmentable();
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent());
        assertEquals("{\"a\":1,", segment(parser));
        assertTrue(parser.deferredBytes());
        assertFalse(parser.hasNextEvent());

        parser.reset();

        wrap(parser, "{\"x\":1} ");
        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals("x", parser.getString());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals("1", parser.getString());
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
    }

    private static void wrap(
        JsonParserImpl parser,
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        parser.wrap(buffer, 0, bytes.length);
    }

    private static String segment(
        JsonParserImpl parser)
    {
        final DirectBuffer seg = parser.getSegment();
        final byte[] out = new byte[seg.capacity()];
        seg.getBytes(0, out);
        return new String(out, UTF_8);
    }
}
