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

import static io.aklivity.zilla.runtime.common.json.internal.JsonEscapeLens.NEEDS_MORE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.json.stream.JsonParsingException;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class JsonEscapeLensTest
{
    @Test
    void shouldPassPlainByteThrough()
    {
        assertUnit("a", 1, new byte[] { 'a' }, true);
    }

    @Test
    void shouldPassRawMultiByteLeadThroughOneByteAtATime()
    {
        final byte[] bytes = "é".getBytes(UTF_8);

        assertUnit("é", 1, new byte[] { bytes[0] }, true);
    }

    @Test
    void shouldDecodeSimpleEscapes()
    {
        assertUnit("\\\"", 2, new byte[] { '"' }, true);
        assertUnit("\\\\", 2, new byte[] { '\\' }, true);
        assertUnit("\\/", 2, new byte[] { '/' }, true);
        assertUnit("\\b", 2, new byte[] { '\b' }, true);
        assertUnit("\\f", 2, new byte[] { '\f' }, true);
        assertUnit("\\n", 2, new byte[] { '\n' }, true);
        assertUnit("\\r", 2, new byte[] { '\r' }, true);
        assertUnit("\\t", 2, new byte[] { '\t' }, true);
    }

    @Test
    void shouldDecodeUnicodeEscapeToUtf8()
    {
        assertUnit("\\u0041", 6, "A".getBytes(UTF_8), true);
        assertUnit("\\u00e9", 6, "é".getBytes(UTF_8), true);
        assertUnit("\\u20ac", 6, "€".getBytes(UTF_8), true);
        assertUnit("\\u20AC", 6, "€".getBytes(UTF_8), true);
    }

    @Test
    void shouldPairSurrogateEscapes()
    {
        assertUnit("\\ud83d\\ude00", 12, "😀".getBytes(UTF_8), true);
    }

    @Test
    void shouldReplaceLoneHighSurrogate()
    {
        final byte[] replacement = "\ufffd".getBytes(UTF_8);

        assertUnit("\\ud83d\"", 6, replacement, true);
        assertUnit("\\ud83dx", 6, replacement, true);
        assertUnit("\\ud83d\\n", 6, replacement, true);
        assertUnit("\\ud83d\\u0041", 6, replacement, true);
        assertUnit("\\ud83d", 6, replacement, true);
    }

    @Test
    void shouldReplaceLoneLowSurrogate()
    {
        assertUnit("\\ude00", 6, "\ufffd".getBytes(UTF_8), true);
    }

    @Test
    void shouldNeedMoreWhenWindowEndsInsideEscape()
    {
        assertEquals(NEEDS_MORE, decode("\\", false));
        assertEquals(NEEDS_MORE, decode("\\u", false));
        assertEquals(NEEDS_MORE, decode("\\u00e", false));
    }

    @Test
    void shouldNeedMoreWhenWindowEndsInsideSurrogatePair()
    {
        final String pair = "\\ud83d\\ude00";

        for (int length = 6; length < pair.length(); length++)
        {
            assertEquals(NEEDS_MORE, decode(pair.substring(0, length), false), "length " + length);
        }
    }

    @Test
    void shouldDecideLoneHighSurrogateOnceTheFollowingByteShowsIt()
    {
        assertEquals(NEEDS_MORE, decode("\\ud83d", false));
        assertEquals(NEEDS_MORE, decode("\\ud83d\\", false));
        assertEquals(6, JsonEscapeLens.size(decode("\\ud83dx", false)));
        assertEquals(6, JsonEscapeLens.size(decode("\\ud83d\\n", false)));
    }

    @Test
    void shouldRejectInvalidEscape()
    {
        assertThrows(JsonParsingException.class, () -> decode("\\x", true));
        assertThrows(JsonParsingException.class, () -> decode("\\u00zz", true));
    }

    private static void assertUnit(
        String source,
        int size,
        byte[] expected,
        boolean last)
    {
        final long unit = decode(source, last);

        assertEquals(size, JsonEscapeLens.size(unit), source);
        assertEquals(expected.length, JsonEscapeLens.count(unit), source);

        final byte[] actual = new byte[JsonEscapeLens.count(unit)];

        for (int index = 0; index < actual.length; index++)
        {
            actual[index] = JsonEscapeLens.byteAt(unit, index);
        }

        assertArrayEquals(expected, actual, source);
    }

    private static long decode(
        String source,
        boolean last)
    {
        final byte[] bytes = source.getBytes(UTF_8);

        return JsonEscapeLens.decode(new UnsafeBufferEx(bytes), 0, bytes.length, last);
    }
}
