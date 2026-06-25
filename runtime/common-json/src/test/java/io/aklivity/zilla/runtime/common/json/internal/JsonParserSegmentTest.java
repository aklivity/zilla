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
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;

public class JsonParserSegmentTest
{
    @Test
    public void shouldSegmentTopLevelObject()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "{\"a\":1} ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent(Mode.SEGMENTED));
        assertEquals("{\"a\":1}", segment(parser));
        assertFalse(parser.deferredBytes());
    }

    @Test
    public void shouldSegmentObjectWithStringContainingBraces()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "{\"a\":\"}{\"} ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent(Mode.SEGMENTED));
        assertEquals("{\"a\":\"}{\"}", segment(parser));
        assertFalse(parser.deferredBytes());
    }

    @Test
    public void shouldSegmentArray()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "[1,2,3] ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_ARRAY, parser.nextEvent());
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent(Mode.SEGMENTED));
        assertEquals("[1,2,3]", segment(parser));
        assertFalse(parser.deferredBytes());
    }

    @Test
    public void shouldResumeStructuredAfterSegment()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "{\"a\":{\"x\":1},\"b\":2} ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals("a", parser.getString());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent(Mode.SEGMENTED));
        assertEquals("{\"x\":1}", segment(parser));
        assertFalse(parser.deferredBytes());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals("b", parser.getString());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals("2", parser.getString());
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());
    }

    @Test
    public void shouldSegmentAcrossFrames()
    {
        final JsonParserImpl parser = new JsonParserImpl();

        wrap(parser, "{\"a\":1,");
        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent(Mode.SEGMENTED));
        final String first = segment(parser);
        assertEquals("{\"a\":1,", first);
        assertTrue(parser.deferredBytes());
        assertFalse(parser.hasNext());

        wrap(parser, "\"b\":2} ");
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent());
        final String second = segment(parser);
        assertEquals("\"b\":2}", second);
        assertFalse(parser.deferredBytes());

        assertEquals("{\"a\":1,\"b\":2}", first + second);
    }

    @Test
    public void shouldIgnoreSegmentableOnScalar()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "42 ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals("42", parser.getString());
        // requesting SEGMENTED with a scalar as the last delivered event is ignored
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent(Mode.SEGMENTED));
    }

    @Test
    public void shouldSegmentWholeDocumentAtDocumentStart()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "{ \"a\" : 1 } ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent(Mode.SEGMENTED));
        assertEquals("{ \"a\" : 1 }", segment(parser));
        assertFalse(parser.deferredBytes());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
    }

    @Test
    public void shouldNotSegmentScalarDocumentAtDocumentStart()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "42 ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent(Mode.SEGMENTED));
        assertEquals("42", parser.getString());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
    }

    private static void wrap(
        JsonParserImpl parser,
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
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
