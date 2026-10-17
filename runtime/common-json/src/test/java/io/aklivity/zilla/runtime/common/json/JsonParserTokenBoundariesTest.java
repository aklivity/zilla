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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonParserTokenBoundariesTest
{
    private static final String DOCUMENT = "{\"a\":{\"content\" : \"Hello, world\" , \"n\":1}}";

    @Test
    void shouldPositionKeyAtValueStart()
    {
        final List<String> events = events(DOCUMENT, DOCUMENT.length(), true);

        assertEquals(DOCUMENT.indexOf("\"Hello"), position(events, JsonEvent.KEY_NAME, 1));
        assertEquals(DOCUMENT.indexOf('1'), position(events, JsonEvent.KEY_NAME, 2));
    }

    @Test
    void shouldPositionKeyAtNestedValueStart()
    {
        final List<String> events = events(DOCUMENT, DOCUMENT.length(), true);

        assertEquals(DOCUMENT.indexOf("{\"content"), position(events, JsonEvent.KEY_NAME, 0));
    }

    @Test
    void shouldPositionStringValueAfterClosingQuote()
    {
        final List<String> events = events(DOCUMENT, DOCUMENT.length(), true);

        assertEquals(DOCUMENT.indexOf("world\"") + "world\"".length(), position(events, JsonEvent.VALUE_STRING, 0));
    }

    @Test
    void shouldPositionKeyAheadOfSeparatorByDefault()
    {
        final List<String> events = events(DOCUMENT, DOCUMENT.length(), false);

        assertEquals(DOCUMENT.indexOf(" : "), position(events, JsonEvent.KEY_NAME, 1));
    }

    @Test
    void shouldPositionKeyAtEachValueKind()
    {
        final String document = "{\"o\":{},\"a\":[],\"n\":-1.5e3,\"t\":true,\"f\":false,\"z\":null,\"s\":\"\"}";

        final List<String> events = events(document, document.length(), true);

        assertEquals(document.indexOf("{}"), position(events, JsonEvent.KEY_NAME, 0));
        assertEquals(document.indexOf("[]"), position(events, JsonEvent.KEY_NAME, 1));
        assertEquals(document.indexOf("-1.5e3"), position(events, JsonEvent.KEY_NAME, 2));
        assertEquals(document.indexOf("true"), position(events, JsonEvent.KEY_NAME, 3));
        assertEquals(document.indexOf("false"), position(events, JsonEvent.KEY_NAME, 4));
        assertEquals(document.indexOf("null"), position(events, JsonEvent.KEY_NAME, 5));
        assertEquals(document.indexOf("\"\"}"), position(events, JsonEvent.KEY_NAME, 6));
    }

    @Test
    void shouldPositionEmptyStringValueAfterClosingQuote()
    {
        final String document = "{\"text\":\"\"}";

        final List<String> events = events(document, document.length(), true);

        assertEquals(document.indexOf("\"\"}"), position(events, JsonEvent.KEY_NAME, 0));
        assertEquals(document.indexOf("}"), position(events, JsonEvent.VALUE_STRING, 0));
    }

    @Test
    void shouldPositionKeyAcrossWhitespaceKinds()
    {
        final String document = "{\"a\"\r\n\t :\n \t\"b\"}";

        final List<String> events = events(document, document.length(), true);

        assertEquals(document.indexOf("\"b\""), position(events, JsonEvent.KEY_NAME, 0));
    }

    @Test
    void shouldDeliverSameEventsAtEverySplit()
    {
        final List<String> whole = events(DOCUMENT, DOCUMENT.length(), true);

        for (int split = 1; split < DOCUMENT.length(); split++)
        {
            if (!fragments(DOCUMENT, split))
            {
                assertEquals(whole, events(DOCUMENT, split, true), "split at " + split);
            }
        }
    }

    @Test
    void shouldDeliverSameEventsAtEverySplitAcrossWhitespace()
    {
        final String document = "{ \"a\" \n:\t \"x\" ,\r\n \"b\" : [ 1 , \"y\" ] , \"c\":\"\" }";
        final List<String> whole = events(document, document.length(), true);

        for (int split = 1; split < document.length(); split++)
        {
            if (!fragments(document, split))
            {
                assertEquals(whole, events(document, split, true), "split at " + split);
            }
        }
    }

    @Test
    void shouldHoldKeyUntilValueFirstByteArrives()
    {
        final int afterColon = DOCUMENT.indexOf("\"content\" :") + "\"content\" : ".length();

        final byte[] bytes = DOCUMENT.getBytes(UTF_8);
        final JsonParserEx parser = parser(true);
        parser.wrap(new UnsafeBufferEx(bytes), 0, afterColon, false);

        JsonEvent last = null;
        while (parser.hasNextEvent())
        {
            last = parser.nextEvent();
        }

        assertEquals(JsonEvent.START_OBJECT, last);
        assertEquals(DOCUMENT.indexOf("\"content\""), afterColon - parser.remaining());
        assertFalse(parser.hasNextEvent());
    }

    @Test
    void shouldNotDeliverKeyOnStarvedWindowEndingAtKey()
    {
        final String document = "{\"a\":1}";
        final byte[] bytes = document.getBytes(UTF_8);
        final JsonParserEx parser = parser(true);
        parser.wrap(new UnsafeBufferEx(bytes), 0, document.indexOf(':'), false);

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertNull(parser.nextEvent());
        assertEquals(document.indexOf(':') - 1, parser.remaining());
    }

    private static boolean fragments(
        String document,
        int split)
    {
        boolean insideString = false;

        for (int index = 0; index < split; index++)
        {
            insideString ^= document.charAt(index) == '"';
        }

        return insideString || Character.isDigit(document.charAt(split - 1));
    }

    private static JsonParserEx parser(
        boolean boundaries)
    {
        return JsonEx.createParser(Map.of(JsonParserEx.TOKEN_BOUNDARIES, boundaries));
    }

    private static int position(
        List<String> events,
        JsonEvent kind,
        int ordinal)
    {
        int seen = 0;
        int position = -1;

        for (int index = 0; position == -1 && index < events.size(); index++)
        {
            final String event = events.get(index);
            if (event.startsWith(kind.name() + "@") && seen++ == ordinal)
            {
                position = Integer.parseInt(event.substring(kind.name().length() + 1));
            }
        }

        return position;
    }

    private static List<String> events(
        String document,
        int split,
        boolean boundaries)
    {
        final byte[] bytes = document.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final JsonParserEx parser = parser(boundaries);
        final List<String> events = new ArrayList<>();

        int offset = 0;
        int limit = Math.min(split, bytes.length);
        boolean last = limit == bytes.length;
        parser.wrap(buffer, offset, limit, last);

        while (true)
        {
            while (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent();
                events.add(event.name() + "@" + (limit - parser.remaining()));
            }

            if (last)
            {
                break;
            }

            offset = limit - parser.remaining();
            limit = bytes.length;
            last = true;
            parser.wrap(buffer, offset, limit, last);
        }

        return events;
    }
}
