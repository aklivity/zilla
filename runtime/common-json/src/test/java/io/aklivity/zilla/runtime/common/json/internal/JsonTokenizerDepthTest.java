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
package io.aklivity.zilla.runtime.common.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.stream.JsonParsingException;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;

public class JsonTokenizerDepthTest
{
    private static final int DEPTH_LIMIT = 64;
    private static final int NO_ARM = -1;

    @Test
    public void shouldParseArraysNestedToTheDepthLimit()
    {
        final String json = "[".repeat(DEPTH_LIMIT) + "1" + "]".repeat(DEPTH_LIMIT);

        final List<JsonEvent> events = events(json, NO_ARM);

        assertEquals(DEPTH_LIMIT, count(events, JsonEvent.START_ARRAY));
        assertEquals(DEPTH_LIMIT, count(events, JsonEvent.END_ARRAY));
    }

    @Test
    public void shouldParseObjectsNestedToTheDepthLimit()
    {
        final String json = "{\"k\":".repeat(DEPTH_LIMIT) + "1" + "}".repeat(DEPTH_LIMIT);

        final List<JsonEvent> events = events(json, NO_ARM);

        assertEquals(DEPTH_LIMIT, count(events, JsonEvent.START_OBJECT));
        assertEquals(DEPTH_LIMIT, count(events, JsonEvent.END_OBJECT));
    }

    @Test
    public void shouldReturnToTheEnclosingContainerAtEveryLevelOfAMixedPath()
    {
        final StringBuilder json = new StringBuilder();
        final StringBuilder tail = new StringBuilder();
        for (int depth = 0; depth < DEPTH_LIMIT; depth++)
        {
            if (depth % 3 == 0)
            {
                json.append("[0,");
                tail.insert(0, ",1]");
            }
            else
            {
                json.append("{\"a\":0,\"b\":");
                tail.insert(0, ",\"c\":2}");
            }
        }
        json.append("null").append(tail);

        final List<JsonEvent> events = events(json.toString(), NO_ARM);

        assertEquals(count(events, JsonEvent.START_ARRAY), count(events, JsonEvent.END_ARRAY));
        assertEquals(count(events, JsonEvent.START_OBJECT), count(events, JsonEvent.END_OBJECT));
        assertEquals(DEPTH_LIMIT, count(events, JsonEvent.START_ARRAY) + count(events, JsonEvent.START_OBJECT));
        assertEquals(2 * DEPTH_LIMIT, count(events, JsonEvent.VALUE_NUMBER));
        assertEquals(1, count(events, JsonEvent.VALUE_NULL));
    }

    @Test
    public void shouldRejectAContainerPastTheDepthLimit()
    {
        final String json = "[".repeat(DEPTH_LIMIT + 1) + "]".repeat(DEPTH_LIMIT + 1);

        final JsonParsingException ex = assertThrows(JsonParsingException.class, () -> events(json, NO_ARM));

        assertEquals("JSON depth exceeds " + DEPTH_LIMIT, ex.getMessage());
    }

    @Test
    public void shouldRejectAnObjectPastTheDepthLimit()
    {
        final String json = "{\"k\":".repeat(DEPTH_LIMIT + 1) + "1" + "}".repeat(DEPTH_LIMIT + 1);

        final JsonParsingException ex = assertThrows(JsonParsingException.class, () -> events(json, NO_ARM));

        assertEquals("JSON depth exceeds " + DEPTH_LIMIT, ex.getMessage());
    }

    @Test
    public void shouldParseAnEscapedScopeOpenedInsideADeepContainer()
    {
        final int outer = DEPTH_LIMIT - 2;
        final String json = "[".repeat(outer) + "\"{\\\"a\\\":[1]}\"" + "]".repeat(outer);

        final List<JsonEvent> events = events(json, outer);

        assertEquals(outer + 1, count(events, JsonEvent.START_ARRAY));
        assertEquals(outer + 1, count(events, JsonEvent.END_ARRAY));
        assertEquals(1, count(events, JsonEvent.START_ESCAPED));
        assertEquals(1, count(events, JsonEvent.END_ESCAPED));
        assertEquals(1, count(events, JsonEvent.START_OBJECT));
    }

    @Test
    public void shouldParseAnEscapedScopeAtTheDepthLimit()
    {
        final int outer = DEPTH_LIMIT - 1;
        final String json = "[".repeat(outer) + "\"[1]\"" + "]".repeat(outer);

        final List<JsonEvent> events = events(json, outer);

        assertEquals(DEPTH_LIMIT, count(events, JsonEvent.START_ARRAY));
        assertEquals(DEPTH_LIMIT, count(events, JsonEvent.END_ARRAY));
    }

    @Test
    public void shouldParseAnEscapedScopeOfAnObjectMember()
    {
        final String json = "{\"a\":{\"b\":\"[{\\\"c\\\":2}]\",\"d\":3},\"e\":[4]}";

        final List<JsonEvent> events = events(json, 4);

        assertEquals(List.of(
            JsonEvent.START_OBJECT, JsonEvent.KEY_NAME,
            JsonEvent.START_OBJECT, JsonEvent.KEY_NAME,
            JsonEvent.START_ESCAPED, JsonEvent.START_ARRAY, JsonEvent.START_OBJECT, JsonEvent.KEY_NAME,
            JsonEvent.VALUE_NUMBER, JsonEvent.END_OBJECT, JsonEvent.END_ARRAY, JsonEvent.END_ESCAPED,
            JsonEvent.KEY_NAME, JsonEvent.VALUE_NUMBER, JsonEvent.END_OBJECT,
            JsonEvent.KEY_NAME, JsonEvent.START_ARRAY, JsonEvent.VALUE_NUMBER, JsonEvent.END_ARRAY,
            JsonEvent.END_OBJECT), events);
    }

    @Test
    public void shouldParseAnEscapedScopeAsTheWholeDocument()
    {
        final List<JsonEvent> events = events("\"[1]\"", 0);

        assertEquals(List.of(
            JsonEvent.START_ESCAPED, JsonEvent.START_ARRAY, JsonEvent.VALUE_NUMBER, JsonEvent.END_ARRAY,
            JsonEvent.END_ESCAPED), events);
    }

    private static List<JsonEvent> events(
        String content,
        int armAfter)
    {
        final JsonTokenizerImpl tokenizer = new JsonTokenizerImpl(false);
        final byte[] bytes = content.getBytes(UTF_8);
        tokenizer.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        tokenizer.window(bytes.length);

        final List<JsonEvent> events = new ArrayList<>();
        boolean advanced = true;
        while (advanced)
        {
            if (events.size() == armAfter)
            {
                tokenizer.escape();
            }

            advanced = tokenizer.advance();
            if (advanced)
            {
                events.add(tokenizer.event());
                tokenizer.clearEvent();
            }
        }
        return events;
    }

    private static int count(
        List<JsonEvent> events,
        JsonEvent event)
    {
        return (int) events.stream().filter(e -> e == event).count();
    }
}
