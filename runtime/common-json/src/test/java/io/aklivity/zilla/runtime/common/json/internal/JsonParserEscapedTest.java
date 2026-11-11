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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.json.JsonException;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.Mode;

class JsonParserEscapedTest
{
    private static final int WHOLE = 4096;

    @Test
    void shouldParseStringifiedObject()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(b) VALUE_NUMBER(1) END_OBJECT " +
            "END_ESCAPED END_OBJECT", "{\"a\":\"{\\\"b\\\":1}\"}", "a");
    }

    @Test
    void shouldParseStringifiedArray()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED START_ARRAY VALUE_NUMBER(1) VALUE_STRING(x) VALUE_TRUE " +
            "END_ARRAY END_ESCAPED END_OBJECT", "{\"a\":\"[1,\\\"x\\\",true]\"}", "a");
    }

    @Test
    void shouldParseStringifiedScalars()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED VALUE_NUMBER(42) END_ESCAPED END_OBJECT",
            "{\"a\":\"42\"}", "a");
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED VALUE_STRING(x) END_ESCAPED END_OBJECT",
            "{\"a\":\"\\\"x\\\"\"}", "a");
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED VALUE_TRUE END_ESCAPED END_OBJECT",
            "{\"a\":\"true\"}", "a");
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED VALUE_FALSE END_ESCAPED END_OBJECT",
            "{\"a\":\"false\"}", "a");
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED VALUE_NULL END_ESCAPED END_OBJECT",
            "{\"a\":\"null\"}", "a");
    }

    @Test
    void shouldParseWhitespaceAroundContent()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(b) VALUE_NUMBER(1) END_OBJECT " +
            "END_ESCAPED END_OBJECT", "{\"a\":\" \\n {\\\"b\\\" : 1 } \\t \"}", "a");
    }

    @Test
    void shouldParseEmptyContent()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED END_ESCAPED END_OBJECT", "{\"a\":\"\"}", "a");
    }

    @Test
    void shouldParseValueFollowingScope()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED START_ARRAY VALUE_NUMBER(1) END_ARRAY END_ESCAPED " +
            "KEY_NAME(c) VALUE_STRING(d) END_OBJECT", "{\"a\":\"[1]\",\"c\":\"d\"}", "a");
    }

    @Test
    void shouldParseSeveralScopes()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED VALUE_NUMBER(1) END_ESCAPED KEY_NAME(b) VALUE_STRING(x) " +
            "KEY_NAME(c) START_ESCAPED VALUE_NUMBER(2) END_ESCAPED END_OBJECT",
            "{\"a\":\"1\",\"b\":\"x\",\"c\":\"2\"}", "a", "c");
    }

    @Test
    void shouldLeaveStringAloneUnlessRequested()
    {
        assertEvents("START_OBJECT KEY_NAME(a) VALUE_STRING({\"b\":1}) END_OBJECT", "{\"a\":\"{\\\"b\\\":1}\"}");
    }

    @Test
    void shouldDecodeEscapesInsideContent()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(b) VALUE_STRING(x\ny\"z\\) END_OBJECT " +
            "END_ESCAPED END_OBJECT", "{\"a\":\"{\\\"b\\\":\\\"x\\\\ny\\\\\\\"z\\\\\\\\\\\"}\"}", "a");
    }

    @Test
    void shouldDecodeOuterUnicodeEscapes()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(b) VALUE_STRING(é€) END_OBJECT " +
            "END_ESCAPED END_OBJECT", "{\"a\":\"{\\\"b\\\":\\\"\\u00e9\\u20ac\\\"}\"}", "a");
    }

    @Test
    void shouldDecodeInnerUnicodeEscapes()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(b) VALUE_STRING(é) END_OBJECT " +
            "END_ESCAPED END_OBJECT", "{\"a\":\"{\\\"b\\\":\\\"\\\\u00e9\\\"}\"}", "a");
    }

    @Test
    void shouldPairOuterSurrogateEscapes()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(b) VALUE_STRING(😀) END_OBJECT " +
            "END_ESCAPED END_OBJECT", "{\"a\":\"{\\\"b\\\":\\\"\\ud83d\\ude00\\\"}\"}", "a");
    }

    @Test
    void shouldReplaceLoneOuterSurrogateEscape()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(b) VALUE_STRING(�) END_OBJECT " +
            "END_ESCAPED END_OBJECT", "{\"a\":\"{\\\"b\\\":\\\"\\ud83d\\\"}\"}", "a");
    }

    @Test
    void shouldParseRawMultiByteCharacters()
    {
        assertEvents("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(b) VALUE_STRING(é€😀) " +
            "END_OBJECT END_ESCAPED END_OBJECT", "{\"a\":\"{\\\"b\\\":\\\"é€😀\\\"}\"}", "a");
    }

    @Test
    void shouldParseLargeContentAcrossWindows()
    {
        final String text = "x".repeat(500) + "\\\\n" + "y".repeat(500);
        final String json = "{\"a\":\"{\\\"b\\\":\\\"" + text + "\\\"}\"}";
        final String expected = "x".repeat(500) + "\n" + "y".repeat(500);

        for (int window : new int[] { 1, 7, 64, 333, WHOLE })
        {
            assertEquals("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(b) VALUE_STRING(" + expected +
                ") END_OBJECT END_ESCAPED END_OBJECT", String.join(" ", events(json, window, "a")), "window " + window);
        }
    }

    @Test
    void shouldRejectIncompleteContent()
    {
        assertRejected("Incomplete document in escaped string", "{\"a\":\"{\\\"b\\\":\"}");
        assertRejected("Incomplete document in escaped string", "{\"a\":\"  \"}");
        assertRejected("Incomplete document in escaped string", "{\"a\":\"[1,\"}");
    }

    @Test
    void shouldRejectTrailingContent()
    {
        assertRejected("escaped string continues with", "{\"a\":\"1 2\"}");
        assertRejected("escaped string continues with", "{\"a\":\"{} x\"}");
    }

    @Test
    void shouldRejectNonStringValue()
    {
        assertRejected("escaped scope requires a string value", "{\"a\":1}");
        assertRejected("escaped scope requires a string value", "{\"a\":{}}");
    }

    @Test
    void shouldRejectNestedScope()
    {
        assertRejected("escaped scope is already open", "{\"a\":\"{\\\"a\\\":\\\"1\\\"}\"}");
    }

    @Test
    void shouldParseEveryWindowSize()
    {
        final String json = "{\"a\":\"{\\\"b\\\":[1,2.5e3,\\\"x\\\\ny\\\",true,null,{\\\"c\\\":\\\"\\\\u00e9\\\"}]}\",\"d\":4}";
        final String expected = String.join(" ", events(json, WHOLE, "a"));

        assertEquals("START_OBJECT KEY_NAME(a) START_ESCAPED START_OBJECT KEY_NAME(b) START_ARRAY VALUE_NUMBER(1) " +
            "VALUE_NUMBER(2.5e3) VALUE_STRING(x\ny) VALUE_TRUE VALUE_NULL START_OBJECT KEY_NAME(c) VALUE_STRING(é) " +
            "END_OBJECT END_ARRAY END_OBJECT END_ESCAPED KEY_NAME(d) VALUE_NUMBER(4) END_OBJECT", expected);

        for (int window = 1; window <= json.length(); window++)
        {
            assertEquals(expected, String.join(" ", events(json, window, "a")), "window " + window);
        }
    }

    private static void assertRejected(
        String message,
        String json)
    {
        for (int window : new int[] { 1, 3, WHOLE })
        {
            final JsonException ex = assertThrows(JsonException.class, () -> events(json, window, "a"), "window " + window);

            assertTrue(ex.getMessage().contains(message), "window " + window + ": " + ex.getMessage());
        }
    }

    private static void assertEvents(
        String expected,
        String json,
        String... keys)
    {
        for (int window : new int[] { 1, 2, 3, 5, 8, 13, WHOLE })
        {
            assertEquals(expected, String.join(" ", events(json, window, keys)), "window " + window);
        }
    }

    private static List<String> events(
        String json,
        int window,
        String... keys)
    {
        final Set<String> escaped = Set.of(keys);
        final byte[] bytes = (json + " ").getBytes(UTF_8);
        final UnsafeBufferEx input = new UnsafeBufferEx(bytes);
        final JsonParserEx parser = new JsonParserImpl();
        final List<String> events = new ArrayList<>();
        final StringBuilder text = new StringBuilder();

        int progress = 0;
        int limit = 0;
        boolean done = false;

        while (!done)
        {
            limit = Math.min(limit + window, bytes.length);

            final boolean last = limit >= bytes.length;

            parser.wrap(input, progress, limit, last);

            while (parser.hasNextEvent())
            {
                final JsonEvent event = parser.nextEvent(Mode.STRUCTURED);

                switch (event)
                {
                case KEY_NAME:
                    final String key = parser.getStringView().toString();
                    events.add("KEY_NAME(" + key + ")");
                    if (escaped.contains(key))
                    {
                        parser.escaped();
                    }
                    break;
                case VALUE_STRING:
                case VALUE_NUMBER:
                    final CharSequence chars = parser.getStringView();
                    text.append(chars);
                    parser.consumed(chars.length());
                    if (!parser.deferredBytes())
                    {
                        events.add(event + "(" + text + ")");
                        text.setLength(0);
                    }
                    break;
                case START_DOCUMENT:
                    break;
                case END_DOCUMENT:
                    done = true;
                    break;
                default:
                    events.add(event.name());
                    break;
                }
            }

            progress = limit - parser.remaining();

            if (last)
            {
                done = true;
            }
        }

        return events;
    }
}
