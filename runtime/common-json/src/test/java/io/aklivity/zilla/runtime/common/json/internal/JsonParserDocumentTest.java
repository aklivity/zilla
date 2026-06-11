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

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEvent;

public class JsonParserDocumentTest
{
    @Test
    public void shouldFrameObjectDocument()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "{\"a\":1} ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
        assertFalse(parser.hasNextEvent());
    }

    @Test
    public void shouldFrameScalarDocument()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "42 ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
        assertFalse(parser.hasNextEvent());
    }

    @Test
    public void shouldFrameSegmentedDocument()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "{\"a\":1} ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        parser.segmentable();
        assertEquals(JsonEvent.START_SEGMENT, parser.nextEvent());
        assertEquals(JsonEvent.END_SEGMENT, parser.nextEvent());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
        assertFalse(parser.hasNextEvent());
    }

    @Test
    public void shouldFrameArrayDocument()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        wrap(parser, "[1,2] ");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_ARRAY, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals(JsonEvent.END_ARRAY, parser.nextEvent());
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
        assertFalse(parser.hasNextEvent());
    }

    private static void wrap(
        JsonParserImpl parser,
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBuffer buffer = new UnsafeBuffer(bytes);
        parser.wrap(buffer, 0, bytes.length);
    }
}
