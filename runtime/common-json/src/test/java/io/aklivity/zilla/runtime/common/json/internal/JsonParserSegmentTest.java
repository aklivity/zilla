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

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEvent;

public class JsonParserSegmentTest
{
    @Test
    public void shouldSegmentTopLevelObject()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "{\"a\":1} ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        parser.segmentable();
        assertEquals(JsonEvent.START_SEGMENT, parser.nextEvent());
        assertEquals("{\"a\":1}", segment(parser));
        assertEquals(JsonEvent.END_SEGMENT, parser.nextEvent());
        assertEquals("", segment(parser));
    }

    @Test
    public void shouldSegmentObjectWithStringContainingBraces()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "{\"a\":\"}{\"} ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        parser.segmentable();
        assertEquals(JsonEvent.START_SEGMENT, parser.nextEvent());
        assertEquals("{\"a\":\"}{\"}", segment(parser));
        assertEquals(JsonEvent.END_SEGMENT, parser.nextEvent());
    }

    @Test
    public void shouldSegmentArray()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "[1,2,3] ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_ARRAY, parser.nextEvent());
        parser.segmentable();
        assertEquals(JsonEvent.START_SEGMENT, parser.nextEvent());
        assertEquals("[1,2,3]", segment(parser));
        assertEquals(JsonEvent.END_SEGMENT, parser.nextEvent());
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
        parser.segmentable();
        assertEquals(JsonEvent.START_SEGMENT, parser.nextEvent());
        assertEquals("{\"x\":1}", segment(parser));
        assertEquals(JsonEvent.END_SEGMENT, parser.nextEvent());
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
        parser.segmentable();
        assertEquals(JsonEvent.START_SEGMENT, parser.nextEvent());
        final String first = segment(parser);
        assertEquals("{\"a\":1,", first);
        assertFalse(parser.hasNext());

        wrap(parser, "\"b\":2} ");
        assertEquals(JsonEvent.END_SEGMENT, parser.nextEvent());
        final String second = segment(parser);
        assertEquals("\"b\":2}", second);

        assertEquals("{\"a\":1,\"b\":2}", first + second);
    }

    @Test
    public void shouldIgnoreSegmentableOnScalar()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "42 ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        parser.segmentable();
        assertEquals("42", parser.getString());
    }

    @Test
    public void shouldSegmentWholeDocumentAtDocumentStart()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "{ \"a\" : 1 } ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        parser.segmentable();
        assertEquals(JsonEvent.START_SEGMENT, parser.nextEvent());
        assertEquals("{ \"a\" : 1 }", segment(parser));
        assertEquals(JsonEvent.END_SEGMENT, parser.nextEvent());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
    }

    @Test
    public void shouldNotSegmentScalarDocumentAtDocumentStart()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "42 ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        parser.segmentable();
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals("42", parser.getString());
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
