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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

class StreamingJsonTokenizerPathTest
{
    @Test
    void shouldTrackPathThroughObjectsAndArrays() throws IOException
    {
        final String json = "{\"tools\":[{\"name\":\"X\"},{\"name\":\"Y\"}],\"id\":7}";
        final StreamingJsonTokenizer tokenizer = new StreamingJsonTokenizer();
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

        // Capture path observed at each event in the order they fire
        final List<String> pathAtEvent = new ArrayList<>();
        final List<JsonParser.Event> events = new ArrayList<>();
        while (tokenizer.advance(in))
        {
            // path is recorded immediately after each successful event consumption
            events.add(tokenizer.event());
            pathAtEvent.add(tokenizer.currentPath());
            tokenizer.clearEvent();
        }

        // Sanity check on event sequence
        assertEquals(15, events.size(), "events: " + events);

        // Spot-check key paths:
        // [0]  START_OBJECT outer  -> "/" (entered outer object; key null)
        assertEquals("/", pathAtEvent.get(0));
        // [1]  KEY_NAME "tools" -> /tools
        assertEquals("/tools", pathAtEvent.get(1));
        // [2]  START_ARRAY -> /tools/0 (entered array; index 0)
        assertEquals("/tools/0", pathAtEvent.get(2));
        // [3]  START_OBJECT item 0 -> /tools/0/ (entered nested object)
        assertEquals("/tools/0/", pathAtEvent.get(3));
        // [4]  KEY_NAME "name" -> /tools/0/name
        assertEquals("/tools/0/name", pathAtEvent.get(4));
        // [5]  VALUE_STRING "X" -> still /tools/0/name
        assertEquals("/tools/0/name", pathAtEvent.get(5));
        // [6]  END_OBJECT closing item 0 -> /tools/1 (popped; index incremented)
        assertEquals("/tools/1", pathAtEvent.get(6));
        // [7]  START_OBJECT item 1 -> /tools/1/
        assertEquals("/tools/1/", pathAtEvent.get(7));
        // [8]  KEY_NAME "name"
        assertEquals("/tools/1/name", pathAtEvent.get(8));
        // [9]  VALUE_STRING "Y"
        assertEquals("/tools/1/name", pathAtEvent.get(9));
        // [10] END_OBJECT closing item 1 -> /tools/2 (popped; index incremented)
        assertEquals("/tools/2", pathAtEvent.get(10));
        // [11] END_ARRAY closing tools -> /tools (popped array; outer object's most-recent key remains "tools")
        assertEquals("/tools", pathAtEvent.get(11));
        // [12] KEY_NAME "id" -> /id
        assertEquals("/id", pathAtEvent.get(12));
        // [13] VALUE_NUMBER 7 -> still /id
        assertEquals("/id", pathAtEvent.get(13));
        // [14] END_OBJECT outer -> "" (popped; back at root)
        assertEquals("", pathAtEvent.get(14));
    }

    @Test
    void shouldExposeFullPathForNestedObject() throws IOException
    {
        final String json = "{\"a\":{\"b\":{\"c\":42}}}";
        final StreamingJsonTokenizer tokenizer = new StreamingJsonTokenizer();
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

        // Walk to the VALUE_NUMBER event and check path
        String pathAtNumber = null;
        while (tokenizer.advance(in))
        {
            if (tokenizer.event() == JsonParser.Event.VALUE_NUMBER)
            {
                pathAtNumber = tokenizer.currentPath();
            }
            tokenizer.clearEvent();
        }
        assertEquals("/a/b/c", pathAtNumber);
    }
}
