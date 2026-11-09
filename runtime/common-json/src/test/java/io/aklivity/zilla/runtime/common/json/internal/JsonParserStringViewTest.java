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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonStringView;

class JsonParserStringViewTest
{
    private final JsonParserEx parser = JsonEx.createParser();

    @Test
    void shouldReadAStringThatFitsTheWindowInPlace()
    {
        wrap("{\"text\":\"hello world\"}");

        final CharSequence view = valueString();

        assertInstanceOf(EncodedStringView.class, view);
        assertEquals("hello world", view.toString());
    }

    @Test
    void shouldReadAnEscapedStringInPlace()
    {
        wrap("{\"text\":\"a\\nb\\\"c\\u00e9€😀\"}");

        final CharSequence view = valueString();

        assertInstanceOf(EncodedStringView.class, view);
        assertEquals("a\nb\"cé€😀", view.toString());
        assertEquals(9, view.length());
    }

    @Test
    void shouldReadAnEmptyStringInPlace()
    {
        wrap("{\"text\":\"\"}");

        final CharSequence view = valueString();

        assertInstanceOf(EncodedStringView.class, view);
        assertEquals(0, view.length());
    }

    @Test
    void shouldMaterializeTheStringFromTheView()
    {
        wrap("{\"text\":\"a\\tb\"}");

        nextValueString();

        assertEquals("a\tb", parser.getString());
    }

    @Test
    void shouldReadTheRemainderAfterPartialConsumption()
    {
        wrap("{\"text\":\"abc\\ndef\"}");

        nextValueString();
        parser.consumed(2);

        assertEquals("c\ndef", parser.getStringView().toString());

        parser.consumed(2);

        assertEquals("def", parser.getStringView().toString());
    }

    @Test
    void shouldTransferTheEncodedValueFromTheParser()
    {
        final String encoded = "a\\nb\u00e9\\u20ac\\\"z";
        wrap("{\"text\":\"" + encoded + "\"}");

        final JsonStringView view = assertInstanceOf(JsonStringView.class, valueString());
        final MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[64]);

        final int length = view.transferTo(0, view.length(), dst, 3);

        assertEquals(length, view.encodedLength(0, view.length()));
        assertEquals(encoded, dst.getStringWithoutLengthUtf8(3, length));
    }

    @Test
    void shouldReadAPartlyConsumedStringAsPlainChars()
    {
        wrap("{\"text\":\"abc\"}");

        nextValueString();
        parser.consumed(1);

        assertFalse(parser.getStringView() instanceof JsonStringView);
        assertEquals("bc", parser.getStringView().toString());
    }

    @Test
    void shouldNotReadAKeyInPlace()
    {
        wrap("{\"te\\nxt\":1}");

        assertEquals(JsonEvent.START_DOCUMENT, parser.nextEvent());
        assertEquals(JsonEvent.START_OBJECT, parser.nextEvent());
        assertEquals(JsonEvent.KEY_NAME, parser.nextEvent());

        assertEquals("te\nxt", parser.getStringView().toString());
        assertFalse(parser.getStringView() instanceof EncodedStringView);
    }

    @Test
    void shouldAllowAStringUpToTheMaximumValueSize()
    {
        final JsonParserEx limited = JsonEx.createParser(Map.of(JsonParserEx.MAX_VALUE_SIZE, 16));
        final byte[] bytes = "{\"text\":\"0123456789abcdef\"}".getBytes(UTF_8);

        limited.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals("0123456789abcdef", valueString(limited).toString());
    }

    @Test
    void shouldRejectAStringPastTheMaximumValueSize()
    {
        final JsonParserEx limited = JsonEx.createParser(Map.of(JsonParserEx.MAX_VALUE_SIZE, 16));
        final byte[] bytes = "{\"text\":\"0123456789abcdefg\"}".getBytes(UTF_8);

        limited.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertThrows(RuntimeException.class, () -> valueString(limited));
    }

    @Test
    void shouldRejectAnEscapedStringPastTheMaximumValueSize()
    {
        final JsonParserEx limited = JsonEx.createParser(Map.of(JsonParserEx.MAX_VALUE_SIZE, 8));
        final byte[] bytes = "{\"text\":\"\\n\\n\\n\\n\\n\\n\\n\\n\\n\"}".getBytes(UTF_8);

        limited.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertThrows(RuntimeException.class, () -> valueString(limited));
    }

    @Test
    void shouldReadEveryStringWhateverTheWindows()
    {
        final Random random = new Random(5L);

        for (int round = 0; round < 1500; round++)
        {
            final String value = random(random, random.nextInt(120));
            final String document = "{\"a\":\"" + encode(value, random.nextBoolean()) + "\",\"b\":[\"" +
                encode(value, false) + "\",1]}";

            assertEquals(value + value, strings(document, 1 + random.nextInt(40), random), document);
        }
    }

    @Test
    void shouldReadEveryStringWhenTheWindowIsDeliveredOneByteAtATime()
    {
        final String value = "ab\ncdéef😀gh€i";
        final String document = "{\"a\":\"" + encode(value, false) + "\",\"b\":[\"" + encode(value, true) + "\"]}";

        assertEquals(value + value, strings(document, 1, new Random(1L)));
    }

    @Test
    void shouldNotAllocateToDeliverAString()
    {
        final StringBuilder value = new StringBuilder();
        while (value.length() < 20_000)
        {
            value.append("the model returns a response\\n with \\\"tokens\\\" é ");
        }
        final byte[] bytes = ("{\"text\":\"" + value + "\"}").getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);

        final com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final long threadId = Thread.currentThread().threadId();

        long sink = 0;
        for (int warmup = 0; warmup < 200; warmup++)
        {
            sink += read(buffer, bytes.length);
        }

        final int rounds = 100;
        final long before = threads.getThreadAllocatedBytes(threadId);
        for (int round = 0; round < rounds; round++)
        {
            sink += read(buffer, bytes.length);
        }
        final long allocated = (threads.getThreadAllocatedBytes(threadId) - before) / rounds;

        assertTrue(sink != 0);
        assertTrue(allocated < 512, "allocated " + allocated + " bytes per document");
    }

    private long read(
        UnsafeBufferEx buffer,
        int length)
    {
        long result = 0;

        parser.reset();
        parser.wrap(buffer, 0, length);

        while (parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.VALUE_STRING)
            {
                final CharSequence chars = parser.getStringView();

                for (int index = 0; index < chars.length(); index++)
                {
                    result += chars.charAt(index);
                }
            }
        }

        return result;
    }

    private String strings(
        String document,
        int window,
        Random random)
    {
        final byte[] bytes = document.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final JsonParserEx windowed = JsonEx.createParser();
        final StringBuilder strings = new StringBuilder();

        int offset = 0;
        int limit = Math.min(window, bytes.length);

        while (true)
        {
            final boolean last = limit == bytes.length;
            windowed.wrap(buffer, offset, limit, last);

            while (windowed.hasNextEvent())
            {
                if (windowed.nextEvent() == JsonEvent.VALUE_STRING)
                {
                    final CharSequence chars = windowed.getStringView();
                    strings.append(chars);
                    windowed.consumed(chars.length());
                }
            }

            if (last)
            {
                break;
            }

            offset = limit - windowed.remaining();
            limit = Math.min(limit + 1 + random.nextInt(window), bytes.length);
        }

        return strings.toString();
    }

    private void wrap(
        String document)
    {
        final byte[] bytes = document.getBytes(UTF_8);

        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private CharSequence valueString()
    {
        return valueString(parser);
    }

    private CharSequence valueString(
        JsonParserEx parser)
    {
        CharSequence view = null;

        while (view == null && parser.hasNextEvent())
        {
            if (parser.nextEvent() == JsonEvent.VALUE_STRING)
            {
                view = parser.getStringView();
            }
        }

        assertNotNull(view);

        return view;
    }

    private void nextValueString()
    {
        while (parser.nextEvent() != JsonEvent.VALUE_STRING)
        {
        }
    }

    private static String random(
        Random random,
        int length)
    {
        final String alphabet = "abc xyz\n\t\"\\/\u0001é€";
        final StringBuilder value = new StringBuilder();

        while (value.length() < length)
        {
            if (random.nextInt(25) == 0)
            {
                value.appendCodePoint(0x1f600 + random.nextInt(50));
            }
            else
            {
                value.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
        }

        return value.toString();
    }

    private static String encode(
        String value,
        boolean escapeNonAscii)
    {
        final StringBuilder encoded = new StringBuilder();

        for (int index = 0; index < value.length(); index++)
        {
            final char c = value.charAt(index);

            switch (c)
            {
            case '"' -> encoded.append("\\\"");
            case '\\' -> encoded.append("\\\\");
            case '\n' -> encoded.append("\\n");
            case '\t' -> encoded.append("\\t");
            default ->
            {
                if (c < 0x20 || escapeNonAscii && c >= 0x80)
                {
                    encoded.append(String.format("\\u%04x", (int) c));
                }
                else
                {
                    encoded.append(c);
                }
            }
            }
        }

        return encoded.toString();
    }
}
