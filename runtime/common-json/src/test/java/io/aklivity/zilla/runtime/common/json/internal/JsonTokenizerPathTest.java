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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

public class JsonTokenizerPathTest
{
    @Test
    public void shouldTrackPathThroughObjectsAndArrays() throws IOException
    {
        final String json = "{\"tools\":[{\"name\":\"X\"},{\"name\":\"Y\"}],\"id\":7}";
        final JsonTokenizer tokenizer = new JsonTokenizer();
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

        final List<String> pathAtEvent = new ArrayList<>();
        final List<JsonParser.Event> events = new ArrayList<>();
        while (tokenizer.advance(in))
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
    public void shouldExposeFullPathForNestedObject() throws IOException
    {
        final String json = "{\"a\":{\"b\":{\"c\":42}}}";
        final JsonTokenizer tokenizer = new JsonTokenizer();
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

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

    @Test
    public void shouldTrackPathWithEscapedSlashAndTilde() throws IOException
    {
        // RFC 6901 escapes in currentPath(): "/" encodes to "~1", "~" encodes to "~0"
        final JsonTokenizer tokenizer = new JsonTokenizer();

        final String json = "{\"a/b\":{\"c~d\":42}}";
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

        String observedPathAtNumber = null;
        while (tokenizer.advance(in))
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
    public void shouldFragmentValueExceedingTokenMaxBytes() throws IOException
    {
        final JsonTokenizer tokenizer = new JsonTokenizer(8);

        final String json = "{\"payload\":\"abcdefghijklmnopqrst\"}";
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

        final StringBuilder assembled = new StringBuilder();
        int fragments = 0;
        boolean lastDeferred = true;
        while (tokenizer.advance(in))
        {
            if (tokenizer.event() == JsonParser.Event.VALUE_STRING)
            {
                assembled.append(tokenizer.stringValue());
                fragments++;
                lastDeferred = tokenizer.fragmenting();
            }
            tokenizer.clearEvent();
        }
        assertEquals("abcdefghijklmnopqrst", assembled.toString());
        assertTrue(fragments > 1, "expected multiple fragments, got " + fragments);
        assertFalse(lastDeferred, "final fragment should not defer");
    }

    @Test
    public void shouldHandleArrayDocument() throws IOException
    {
        final JsonTokenizer tokenizer = new JsonTokenizer();

        final String json = "[1,2,3]";
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

        int events = 0;
        while (tokenizer.advance(in))
        {
            events++;
            tokenizer.clearEvent();
        }
        assertEquals(5, events); // [, 1, 2, 3, ]
    }
}
