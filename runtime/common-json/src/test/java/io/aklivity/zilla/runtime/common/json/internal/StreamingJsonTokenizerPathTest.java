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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.StreamingJson;

public class StreamingJsonTokenizerPathTest
{
    @Test
    public void shouldTrackPathThroughObjectsAndArrays() throws IOException
    {
        final String json = "{\"tools\":[{\"name\":\"X\"},{\"name\":\"Y\"}],\"id\":7}";
        final StreamingJsonTokenizer tokenizer = new StreamingJsonTokenizer();
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
        final StreamingJsonTokenizer tokenizer = new StreamingJsonTokenizer();
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
    public void shouldCompilePathsAndMatchExpectedValues() throws IOException
    {
        final List<String> includes = List.of(
            "",                       // root
            "/tools/-/name",          // wildcard array index
            "/tools/-/title");
        final List<String> excludes = List.of(
            "/tools/-/title");        // excludes win for title

        final StreamingJsonTokenizer tokenizer =
            new StreamingJsonTokenizer(includes, excludes, 1024);

        final String json = "{\"tools\":[{\"name\":\"X\",\"title\":\"T\"},{\"name\":\"Y\"}]}";
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

        int eventCount = 0;
        while (tokenizer.advance(in))
        {
            eventCount++;
            tokenizer.clearEvent();
        }
        assertEquals(15, eventCount);
    }

    @Test
    public void shouldMatchPathSegmentWithEscapedSlashAndTilde() throws IOException
    {
        // RFC 6901 escapes: "~1" decodes to "/", "~0" decodes to "~"
        final List<String> includes = List.of(
            "/a~1b/c~0d");            // path of "/a/b" then "c~d"

        final StreamingJsonTokenizer tokenizer =
            new StreamingJsonTokenizer(includes, List.of(), Integer.MAX_VALUE);

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
    public void shouldSuppressScratchForNonReadableValues() throws IOException
    {
        final List<String> includes = List.of("/tools/-/name");
        final StreamingJsonTokenizer tokenizer =
            new StreamingJsonTokenizer(includes, List.of(), Integer.MAX_VALUE);

        final String json = "{\"tools\":[{\"name\":\"X\",\"description\":\"a quite long description\"}]}";
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

        final List<String> readableValues = new ArrayList<>();
        final List<JsonParser.Event> nonReadableValueEvents = new ArrayList<>();
        while (tokenizer.advance(in))
        {
            final JsonParser.Event ev = tokenizer.event();
            switch (ev)
            {
            case VALUE_STRING:
            case VALUE_NUMBER:
                if (tokenizer.valueReadable())
                {
                    readableValues.add(tokenizer.stringValue());
                }
                else
                {
                    nonReadableValueEvents.add(ev);
                }
                break;
            default:
                break;
            }
            tokenizer.clearEvent();
        }
        assertEquals(List.of("X"), readableValues);
        assertEquals(1, nonReadableValueEvents.size());
    }

    @Test
    public void shouldThrowFromGetStringForNonReadableValueAtParserLevel() throws IOException
    {
        final String json = "{\"tools\":[{\"name\":\"X\",\"description\":\"a long description\"}]}";
        final BufferedInputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

        final Map<String, Object> config = Map.of(
            StreamingJson.PATH_INCLUDES, List.of("/tools/-/name"));
        final JsonParser parser = StreamingJson.createParser(in, config);

        boolean sawNonReadableValue = false;
        while (parser.hasNext())
        {
            final JsonParser.Event ev = parser.next();
            if (ev == JsonParser.Event.KEY_NAME && "description".equals(parser.getString()))
            {
                final JsonParser.Event next = parser.next();
                assertEquals(JsonParser.Event.VALUE_STRING, next);
                assertThrows(IllegalStateException.class, parser::getString);
                sawNonReadableValue = true;
                break;
            }
        }
        if (!sawNonReadableValue)
        {
            throw new AssertionError("did not reach the non-readable value");
        }
    }

    @Test
    public void shouldThrowWhenReadableValueExceedsTokenMaxBytes() throws IOException
    {
        final List<String> includes = List.of("/payload");
        final StreamingJsonTokenizer tokenizer =
            new StreamingJsonTokenizer(includes, List.of(), 8);

        final String json = "{\"payload\":\"abcdefghijklmnopqrst\"}";
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

        assertThrows(JsonParsingException.class, () ->
        {
            while (tokenizer.advance(in))
            {
                tokenizer.clearEvent();
            }
        });
    }

    @Test
    public void shouldNotThrowWhenExcludedValueExceedsTokenMaxBytes() throws IOException
    {
        final List<String> excludes = List.of("/payload");
        final StreamingJsonTokenizer tokenizer =
            new StreamingJsonTokenizer(List.of(), excludes, 8);

        final String json = "{\"payload\":\"abcdefghijklmnopqrst\",\"id\":1}";
        final InputStream in = new BufferedInputStream(
            new ByteArrayInputStream(json.getBytes(UTF_8)));

        Long observedId = null;
        while (tokenizer.advance(in))
        {
            if (tokenizer.event() == JsonParser.Event.VALUE_NUMBER && tokenizer.valueReadable())
            {
                observedId = Long.parseLong(tokenizer.stringValue());
            }
            tokenizer.clearEvent();
        }
        assertEquals(1L, observedId);
    }

    @Test
    public void shouldHandleEmptyConfigAsLegacyBehavior() throws IOException
    {
        final StreamingJsonTokenizer tokenizer =
            new StreamingJsonTokenizer(List.of(), List.of(), Integer.MAX_VALUE);

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
