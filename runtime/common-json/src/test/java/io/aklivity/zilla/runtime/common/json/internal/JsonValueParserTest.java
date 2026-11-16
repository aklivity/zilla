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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonStateCorruption;

class JsonValueParserTest
{
    private static final String OBJECT =
        "{\"before\":1,\"v\":{\"a\":[1,2,{\"b\":\"text\"}],\"c\":\"d\\n\"},\"after\":2}";
    private static final String SCALAR = "{\"before\":1,\"v\":\"some text\",\"after\":2}";
    private static final String ARRAY = "{\"before\":1,\"v\":[],\"after\":2}";

    @Test
    void shouldPresentAnObjectAsADocumentAndLeaveTheParserAfterIt()
    {
        assertEquals(
            "START_DOCUMENT START_OBJECT KEY_NAME(before) VALUE_NUMBER(1) KEY_NAME(v) | " +
            "START_DOCUMENT START_OBJECT KEY_NAME(a) START_ARRAY VALUE_NUMBER(1) VALUE_NUMBER(2) " +
            "START_OBJECT KEY_NAME(b) VALUE_STRING(text) END_OBJECT END_ARRAY " +
            "KEY_NAME(c) VALUE_STRING(d\n) END_OBJECT END_DOCUMENT | " +
            "KEY_NAME(after) VALUE_NUMBER(2) END_OBJECT END_DOCUMENT",
            events(OBJECT, OBJECT.length()));
    }

    @Test
    void shouldPresentAScalarAsADocument()
    {
        assertEquals(
            "START_DOCUMENT START_OBJECT KEY_NAME(before) VALUE_NUMBER(1) KEY_NAME(v) | START_DOCUMENT VALUE_STRING(some text) " +
            "END_DOCUMENT | KEY_NAME(after) VALUE_NUMBER(2) END_OBJECT END_DOCUMENT",
            events(SCALAR, SCALAR.length()));
    }

    @Test
    void shouldPresentAnEmptyArrayAsADocument()
    {
        assertEquals(
            "START_DOCUMENT START_OBJECT KEY_NAME(before) VALUE_NUMBER(1) KEY_NAME(v) | START_DOCUMENT START_ARRAY END_ARRAY " +
            "END_DOCUMENT | KEY_NAME(after) VALUE_NUMBER(2) END_OBJECT END_DOCUMENT",
            events(ARRAY, ARRAY.length()));
    }

    @Test
    void shouldPresentTheSameEventsWhateverTheWindows()
    {
        for (String document : new String[] {OBJECT, SCALAR, ARRAY})
        {
            final String expected = events(document, document.length());

            for (int window = 1; window <= document.length(); window++)
            {
                assertEquals(expected, events(document, window), "window " + window + " of " + document);
            }
        }
    }

    @Test
    void shouldPresentTheSameEventsAfterSavingAndRestoringAtEveryWindowBoundary()
    {
        for (String document : new String[] {OBJECT, SCALAR, ARRAY})
        {
            final String expected = events(document, document.length());

            for (int window = 1; window <= document.length(); window++)
            {
                assertEquals(expected, events(document, window, true), "window " + window + " of " + document);
            }
        }
    }

    @Test
    void shouldBeArmedAgainForTheNextValue()
    {
        final byte[] bytes = "{\"v\":{\"a\":1},\"w\":[2]}".getBytes(UTF_8);
        final JsonParserEx parser = JsonEx.createParser();
        final JsonParserEx value = JsonEx.createValueParser(parser);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        final StringBuilder seen = new StringBuilder();

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());

        while (value.hasNextEvent())
        {
            seen.append(value.nextEvent()).append(' ');
        }

        assertFalse(value.hasNextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());

        value.reset();

        while (value.hasNextEvent())
        {
            seen.append(value.nextEvent()).append(' ');
        }

        assertEquals("START_DOCUMENT START_OBJECT KEY_NAME VALUE_NUMBER END_OBJECT END_DOCUMENT " +
            "START_DOCUMENT START_ARRAY VALUE_NUMBER END_ARRAY END_DOCUMENT ", seen.toString());
        assertEquals(JsonEvent.END_OBJECT, parser.nextEvent());
    }

    // The parser's own events up to the key "v", then the value's as a document of its own, then the parser's
    // again, with a '|' between the three, the document fed to the parser in windows of the given size and the
    // bytes it has not consumed presented again at the front of the next.
    private static String events(
        String document,
        int window)
    {
        return events(document, window, false);
    }

    private static String events(
        String document,
        int window,
        boolean roundTrip)
    {
        final byte[] bytes = document.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final JsonParserEx parser = JsonEx.createParser();
        final JsonParserEx value = JsonEx.createValueParser(parser);
        final List<String> seen = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        final UnsafeBufferEx record = new UnsafeBufferEx(new byte[256]);

        int phase = 0;
        int offset = 0;
        int limit = Math.min(window, bytes.length);

        while (true)
        {
            final boolean last = limit == bytes.length;
            parser.wrap(buffer, offset, limit, last);

            JsonEvent event = null;
            do
            {
                final JsonParserEx source = phase == 1 ? value : parser;
                event = source.hasNextEvent() ? source.nextEvent() : null;

                if (event == JsonEvent.VALUE_STRING)
                {
                    text.append(source.getStringView());

                    if (!source.deferredBytes())
                    {
                        seen.add("VALUE_STRING(" + text + ")");
                        text.setLength(0);
                    }
                }
                else if (event == JsonEvent.KEY_NAME || event == JsonEvent.VALUE_NUMBER)
                {
                    seen.add(event + "(" + source.getString() + ")");
                }
                else if (event != null)
                {
                    seen.add(event.name());
                }

                if (phase == 0 && event == JsonEvent.KEY_NAME && "v".equals(source.getString()))
                {
                    seen.add("|");
                    phase = 1;
                }
                else if (phase == 1 && event == JsonEvent.END_DOCUMENT)
                {
                    seen.add("|");
                    phase = 2;
                }
            }
            while (event != null);

            if (last)
            {
                break;
            }

            offset = limit - parser.remaining();
            limit = Math.min(limit + window, bytes.length);

            if (roundTrip)
            {
                final JsonParserEx saved = phase == 1 ? value : parser;
                saved.save(record, 3);
                JsonStateCorruption.corruptParser(parser);
                if (phase == 1)
                {
                    JsonStateCorruption.corrupt(value);
                }

                saved.restore(record, 3);
            }
        }

        return String.join(" ", seen);
    }
}
