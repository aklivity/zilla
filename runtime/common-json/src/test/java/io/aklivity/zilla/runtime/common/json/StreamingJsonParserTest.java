/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.common.json;

import static jakarta.json.stream.JsonParser.Event.END_ARRAY;
import static jakarta.json.stream.JsonParser.Event.END_OBJECT;
import static jakarta.json.stream.JsonParser.Event.KEY_NAME;
import static jakarta.json.stream.JsonParser.Event.START_ARRAY;
import static jakarta.json.stream.JsonParser.Event.START_OBJECT;
import static jakarta.json.stream.JsonParser.Event.VALUE_FALSE;
import static jakarta.json.stream.JsonParser.Event.VALUE_NULL;
import static jakarta.json.stream.JsonParser.Event.VALUE_NUMBER;
import static jakarta.json.stream.JsonParser.Event.VALUE_STRING;
import static jakarta.json.stream.JsonParser.Event.VALUE_TRUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.json.stream.JsonParser;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class StreamingJsonParserTest
{
    @Test
    void shouldParseFlatObject()
    {
        JsonParser parser = parserFor("{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"tools/list\"}");

        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("jsonrpc", parser.getString());
        assertEquals(VALUE_STRING, parser.next());
        assertEquals("2.0", parser.getString());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("id", parser.getString());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("42", parser.getString());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("method", parser.getString());
        assertEquals(VALUE_STRING, parser.next());
        assertEquals("tools/list", parser.getString());
        assertEquals(END_OBJECT, parser.next());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldParseNestedArrays()
    {
        JsonParser parser = parserFor("{\"a\":[1,[2,3],{\"b\":true}]}");

        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("a", parser.getString());
        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("1", parser.getString());
        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("2", parser.getString());
        assertEquals(VALUE_NUMBER, parser.next());
        assertEquals("3", parser.getString());
        assertEquals(END_ARRAY, parser.next());
        assertEquals(START_OBJECT, parser.next());
        assertEquals(KEY_NAME, parser.next());
        assertEquals("b", parser.getString());
        assertEquals(VALUE_TRUE, parser.next());
        assertEquals(END_OBJECT, parser.next());
        assertEquals(END_ARRAY, parser.next());
        assertEquals(END_OBJECT, parser.next());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldParseLiterals()
    {
        JsonParser parser = parserFor("[true,false,null]");

        assertEquals(START_ARRAY, parser.next());
        assertEquals(VALUE_TRUE, parser.next());
        assertEquals(VALUE_FALSE, parser.next());
        assertEquals(VALUE_NULL, parser.next());
        assertEquals(END_ARRAY, parser.next());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldHandleEscapesInString()
    {
        JsonParser parser = parserFor("\"a\\nb\\tc\\\"\\u00e9\"");

        assertEquals(VALUE_STRING, parser.next());
        assertEquals("a\nb\tc\"\u00e9", parser.getString());
        assertFalse(parser.hasNext());
    }

    @Test
    void shouldPauseOnMidTokenEof()
    {
        byte[] full = "{\"image\":\"AAAA\"}".getBytes(UTF_8);
        for (int split = 1; split < full.length; split++)
        {
            DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
            UnsafeBuffer buffer = new UnsafeBuffer(full);

            in.wrap(buffer, 0, split);
            JsonParser parser = StreamingJson.createParser(in);
            while (parser.hasNext())
            {
                parser.next();
            }
            // feed the rest by re-wrapping from the parser's committed position
            int committed = (int) parser.getLocation().getStreamOffset();
            in.wrap(buffer, committed, full.length - committed);
            while (parser.hasNext())
            {
                parser.next();
            }
            assertEquals(full.length, parser.getLocation().getStreamOffset(),
                "failed at split=" + split);
        }
    }

    @Test
    void shouldHandleByteByByteFeed()
    {
        byte[] full = "{\"k\":[1,2,3],\"b\":true}".getBytes(UTF_8);
        UnsafeBuffer buffer = new UnsafeBuffer(full);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(buffer, 0, 1);
        JsonParser parser = StreamingJson.createParser(in);

        int events = 0;
        for (int i = 1; i <= full.length; i++)
        {
            int committed = (int) parser.getLocation().getStreamOffset();
            in.wrap(buffer, committed, i - committed);
            while (parser.hasNext())
            {
                parser.next();
                events++;
            }
        }
        // {"k":[1,2,3],"b":true} → START_OBJECT, KEY_NAME, START_ARRAY,
        // VALUE_NUMBER x3, END_ARRAY, KEY_NAME, VALUE_TRUE, END_OBJECT = 10
        assertEquals(10, events);
        assertEquals(full.length, parser.getLocation().getStreamOffset());
    }

    @Test
    void shouldTrackStreamOffset()
    {
        JsonParser parser = parserFor("{\"a\":1}");
        parser.next(); // START_OBJECT
        assertEquals(1, parser.getLocation().getStreamOffset());
        parser.next(); // KEY_NAME
        assertEquals(4, parser.getLocation().getStreamOffset());
        parser.next(); // VALUE_NUMBER
        assertEquals(6, parser.getLocation().getStreamOffset());
        parser.next(); // END_OBJECT
        assertEquals(7, parser.getLocation().getStreamOffset());
    }

    private static JsonParser parserFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return StreamingJson.createParser(in);
    }
}
