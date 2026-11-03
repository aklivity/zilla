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

import java.util.ArrayList;
import java.util.List;

import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class JsonTokenizerPathTest
{
    @Test
    public void shouldTrackPathThroughObjectsAndArrays()
    {
        final String json = "{\"tools\":[{\"name\":\"X\"},{\"name\":\"Y\"}],\"id\":7}";
        final JsonTokenizerImpl tokenizer = wholeDocument(json);

        final List<String> pathAtEvent = new ArrayList<>();
        final List<JsonParser.Event> events = new ArrayList<>();
        while (tokenizer.advance())
        {
            events.add(tokenizer.event());
            pathAtEvent.add(tokenizer.currentPath());
            tokenizer.clearEvent();
        }

        assertEquals(15, events.size(), "events: " + events);
        assertEquals("/", pathAtEvent.get(0));
        assertEquals("/tools", pathAtEvent.get(1));
        assertEquals("/tools/0", pathAtEvent.get(2));
        assertEquals("/tools/0/", pathAtEvent.get(3));
        assertEquals("/tools/0/name", pathAtEvent.get(4));
        assertEquals("/tools/0/name", pathAtEvent.get(5));
        assertEquals("/tools/1", pathAtEvent.get(6));
        assertEquals("/tools/1/", pathAtEvent.get(7));
        assertEquals("/tools/1/name", pathAtEvent.get(8));
        assertEquals("/tools/1/name", pathAtEvent.get(9));
        assertEquals("/tools/2", pathAtEvent.get(10));
        assertEquals("/tools", pathAtEvent.get(11));
        assertEquals("/id", pathAtEvent.get(12));
        assertEquals("/id", pathAtEvent.get(13));
        assertEquals("", pathAtEvent.get(14));
    }

    @Test
    public void shouldExposeFullPathForNestedObject()
    {
        final String json = "{\"a\":{\"b\":{\"c\":42}}}";
        final JsonTokenizerImpl tokenizer = wholeDocument(json);

        String pathAtNumber = null;
        while (tokenizer.advance())
        {
            if (tokenizer.event() == JsonParser.Event.VALUE_NUMBER)
            {
                pathAtNumber = tokenizer.currentPath();
            }
            tokenizer.clearEvent();
        }
        assertEquals("/a/b/c", pathAtNumber);
    }

    @Test
    public void shouldTrackPathWithEscapedSlashAndTilde()
    {
        // RFC 6901 escapes in currentPath(): "/" encodes to "~1", "~" encodes to "~0"
        final String json = "{\"a/b\":{\"c~d\":42}}";
        final JsonTokenizerImpl tokenizer = wholeDocument(json);

        String observedPathAtNumber = null;
        while (tokenizer.advance())
        {
            if (tokenizer.event() == JsonParser.Event.VALUE_NUMBER)
            {
                observedPathAtNumber = tokenizer.currentPath();
            }
            tokenizer.clearEvent();
        }
        assertEquals("/a~1b/c~0d", observedPathAtNumber);
    }

    @Test
    public void shouldHandleArrayDocument()
    {
        final String json = "[1,2,3]";
        final JsonTokenizerImpl tokenizer = wholeDocument(json);

        int events = 0;
        while (tokenizer.advance())
        {
            events++;
            tokenizer.clearEvent();
        }
        assertEquals(5, events); // [, 1, 2, 3, ]
    }

    private static JsonTokenizerImpl wholeDocument(
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final DirectBufferEx buffer = new UnsafeBufferEx(bytes);
        final JsonTokenizerImpl tokenizer = new JsonTokenizerImpl(Integer.MAX_VALUE, false);
        tokenizer.wrap(buffer, 0, bytes.length, true);
        tokenizer.window(bytes.length);
        return tokenizer;
    }
}
