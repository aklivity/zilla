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

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;

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
        assertEquals(JsonEvent.SEGMENT, parser.nextEvent(Mode.SEGMENTED));
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

    @Test
    public void shouldDeferEndDocumentUntilBoundaryKnownOnNonTerminalWindow()
    {
        final JsonParserImpl parser = new JsonParserImpl();
        final byte[] bytes = "{\"a\":1}".getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);

        // a non-terminal window: the top-level value closes but the trailing bytes are still ambiguous (this
        // document's trailing whitespace, or the next document's leading bytes), so END_DOCUMENT must not be
        // emitted yet — it would risk swallowing a following document's start
        parser.wrap(buffer, 0, bytes.length, false);
        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());
        assertEquals(JsonEvent.VALUE_NUMBER, parser.nextEvent());
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());
        assertFalse(parser.hasNextEvent());

        // the terminal window resolves the boundary; END_DOCUMENT now follows
        parser.wrap(buffer, bytes.length, bytes.length, true);
        assertEquals(JsonEvent.END_DOCUMENT, parser.nextEvent());
        assertFalse(parser.hasNextEvent());
    }

    private static void wrap(
        JsonParserImpl parser,
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        parser.wrap(buffer, 0, bytes.length);
    }
}
