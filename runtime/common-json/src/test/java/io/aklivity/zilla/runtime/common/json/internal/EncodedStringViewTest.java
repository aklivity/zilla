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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

class EncodedStringViewTest
{
    private static final int PADDING = 3;

    private final EncodedStringView view = new EncodedStringView();

    @Test
    void shouldViewPlainAscii()
    {
        wrap("hello world");

        assertTrue(view.plain());
        assertEquals(11, view.length());
        assertEquals('h', view.charAt(0));
        assertEquals('d', view.charAt(10));
        assertEquals("hello world", view.toString());
        assertEquals("lo wo", view.subSequence(3, 8).toString());
    }

    @Test
    void shouldViewEmpty()
    {
        wrap("");

        assertTrue(view.plain());
        assertEquals(0, view.length());
        assertEquals("", view.toString());
    }

    @Test
    void shouldViewSimpleEscapes()
    {
        wrap("a\\\"b\\\\c\\/d\\be\\ff\\ng\\rh\\ti");

        assertFalse(view.plain());
        assertEquals("a\"b\\c/d\be\ff\ng\rh\ti", view.toString());
        assertEquals(17, view.length());
    }

    @Test
    void shouldViewUnicodeEscapes()
    {
        wrap("A\\u0041\\u00e9\\u20AC\\u0000");

        assertEquals("AAé€\u0000", view.toString());
        assertEquals(5, view.length());
    }

    @Test
    void shouldViewEscapedSurrogatePair()
    {
        wrap("x\\ud83d\\ude00y");

        assertEquals("x😀y", view.toString());
        assertEquals(4, view.length());
        assertEquals('\ud83d', view.charAt(1));
        assertEquals('\ude00', view.charAt(2));
    }

    @Test
    void shouldViewMultiByteCharacters()
    {
        wrap("aéb€c😀d");

        assertFalse(view.plain());
        assertEquals("aéb€c😀d", view.toString());
        assertEquals(8, view.length());
        assertEquals('\ud83d', view.charAt(5));
        assertEquals('\ude00', view.charAt(6));
        assertEquals('d', view.charAt(7));
    }

    @Test
    void shouldViewMixedEncodings()
    {
        wrap("line\\none \\\"\u00e9\\\" \\u20ac \ud83d\ude00 \\ud83d\\ude00 end");

        assertEquals("line\none \"\u00e9\" \u20ac \ud83d\ude00 \ud83d\ude00 end", view.toString());
    }

    @Test
    void shouldReadBackwardsAndAtRandom()
    {
        final String value = "ab\ncdéef😀gh€i";
        wrap(encode(value, false));

        for (int index = value.length() - 1; index >= 0; index--)
        {
            assertEquals(value.charAt(index), view.charAt(index), "index " + index);
        }

        for (int index = 0; index < value.length(); index++)
        {
            assertEquals(value.charAt(index), view.charAt(index), "index " + index);
        }

        assertEquals(value.charAt(7), view.charAt(7));
        assertEquals(value.charAt(2), view.charAt(2));
        assertEquals(value.charAt(10), view.charAt(10));
    }

    @Test
    void shouldRejectIndexesOutsideTheValue()
    {
        wrap("ab");

        assertThrows(IndexOutOfBoundsException.class, () -> view.charAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> view.charAt(2));
        assertThrows(IndexOutOfBoundsException.class, () -> view.subSequence(1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> view.subSequence(2, 1));
    }

    @Test
    void shouldBeReusedForAnotherValue()
    {
        wrap("first\\n");
        assertEquals("first\n", view.toString());

        wrap("second");
        assertEquals("second", view.toString());
        assertTrue(view.plain());

        wrap("é");
        assertEquals("é", view.toString());
    }

    @Test
    void shouldViewWithoutScanningWhenCountsAreKnown()
    {
        final byte[] bytes = "a\\nb".getBytes(UTF_8);

        view.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, 3, false);

        assertEquals(3, view.length());
        assertEquals("a\nb", view.toString());
    }

    @Test
    void shouldTransferEncodedBytesVerbatim()
    {
        final String encoded = "ab\\ncd\\u00e9efégh😀ij\\ud83d\\ude00k";
        wrap(encoded);

        final String decoded = view.toString();
        final int cut = decoded.indexOf('g');
        final MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[encoded.getBytes(UTF_8).length + 8]);

        final int head = view.transferTo(0, cut, dst, 2);
        final int tail = view.transferTo(cut, view.length(), dst, 2 + head);

        final byte[] expected = encoded.getBytes(UTF_8);
        final byte[] actual = new byte[head + tail];
        dst.getBytes(2, actual);

        assertEquals(expected.length, head + tail);
        assertArrayEquals(expected, actual);
        assertEquals(head, view.encodedLength(0, cut));
        assertEquals(tail, view.encodedLength(cut, view.length()));
    }

    @Test
    void shouldTransferBetweenEscapedSurrogates()
    {
        wrap("a\\ud83d\\ude00b");

        final MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[32]);

        assertEquals(7, view.transferTo(0, 2, dst, 0));
        assertEquals(6, view.transferTo(2, 3, dst, 7));
    }

    @Test
    void shouldNotTransferInsideAFourByteSequence()
    {
        wrap("a\ud83d\ude00b");

        final MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[32]);

        assertEquals(5, view.transferTo(0, 3, dst, 0));
        assertEquals(1, view.transferTo(3, 4, dst, 5));
        assertThrows(IllegalArgumentException.class, () -> view.transferTo(0, 2, dst, 0));
        assertThrows(IllegalArgumentException.class, () -> view.transferTo(2, 4, dst, 0));
    }

    @Test
    void shouldTransferNothing()
    {
        wrap("abc");

        assertEquals(0, view.transferTo(1, 1, new UnsafeBufferEx(new byte[4]), 0));
        assertEquals(0, view.transferTo(3, 3, new UnsafeBufferEx(new byte[4]), 0));
    }

    @Test
    void shouldMatchTheDecodedValueForRandomValues()
    {
        final Random random = new Random(11L);

        for (int round = 0; round < 3000; round++)
        {
            final String value = random(random, random.nextInt(200));
            final boolean escapeNonAscii = random.nextBoolean();
            final String encoded = encode(value, escapeNonAscii);

            wrap(encoded);

            assertEquals(value.length(), view.length(), encoded);
            assertEquals(value, view.toString(), encoded);

            for (int probe = 0; probe < 10 && !value.isEmpty(); probe++)
            {
                final int index = random.nextInt(value.length());
                assertEquals(value.charAt(index), view.charAt(index), "index " + index + " of " + encoded);
            }

            final int cut = boundary(value, random);
            final byte[] expected = encoded.getBytes(UTF_8);
            final MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[expected.length]);
            final int head = view.transferTo(0, cut, dst, 0);
            final int tail = view.transferTo(cut, view.length(), dst, head);

            final byte[] actual = new byte[head + tail];
            dst.getBytes(0, actual);
            assertArrayEquals(expected, actual, encoded);
        }
    }

    @Test
    void shouldMatchTheDecodedValueForLongPlainStretches()
    {
        final Random random = new Random(23L);
        final String[] specials = {"\n", "\"", "\\", "\t", "\u00e9", "\u20ac", "\ud83d\ude00", "\u0001"};

        for (int round = 0; round < 3000; round++)
        {
            final StringBuilder value = new StringBuilder();
            final int stretches = random.nextInt(6);

            for (int stretch = 0; stretch <= stretches; stretch++)
            {
                final int length = random.nextInt(4) == 0 ? random.nextInt(20) : random.nextInt(300);

                for (int index = 0; index < length; index++)
                {
                    value.append((char) ('a' + random.nextInt(26)));
                }

                if (stretch < stretches)
                {
                    value.append(specials[random.nextInt(specials.length)]);
                }
            }

            final String decoded = value.toString();
            final String encoded = encode(decoded, random.nextBoolean());

            wrap(encoded, random.nextInt(9));

            assertEquals(decoded.length(), view.length(), encoded);
            assertEquals(decoded, view.toString(), encoded);

            for (int probe = 0; probe < 20 && !decoded.isEmpty(); probe++)
            {
                final int index = random.nextInt(decoded.length());
                assertEquals(decoded.charAt(index), view.charAt(index), "index " + index + " of " + encoded);
            }

            final int cut = boundary(decoded, random);
            final byte[] expected = encoded.getBytes(UTF_8);
            final MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[expected.length]);
            final int head = view.transferTo(0, cut, dst, 0);
            final int tail = view.transferTo(cut, view.length(), dst, head);

            final byte[] actual = new byte[head + tail];
            dst.getBytes(0, actual);
            assertArrayEquals(expected, actual, encoded);
        }
    }

    private void wrap(
        String encoded)
    {
        wrap(encoded, PADDING);
    }

    private void wrap(
        String encoded,
        int padding)
    {
        final byte[] value = encoded.getBytes(UTF_8);
        final byte[] padded = new byte[padding + value.length + PADDING];

        System.arraycopy(value, 0, padded, padding, value.length);

        view.wrap(new UnsafeBufferEx(padded), padding, value.length);
    }

    private static int boundary(
        String value,
        Random random)
    {
        int cut = value.isEmpty() ? 0 : random.nextInt(value.length() + 1);

        if (cut > 0 && cut < value.length() && Character.isLowSurrogate(value.charAt(cut)))
        {
            cut--;
        }

        return cut;
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
