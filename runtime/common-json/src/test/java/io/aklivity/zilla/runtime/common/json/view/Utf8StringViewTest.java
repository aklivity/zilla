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
package io.aklivity.zilla.runtime.common.json.view;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.MemorySegment;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.lang.CharSequenceEx;
import io.aklivity.zilla.runtime.common.lang.SegmentTransferTarget;

public class Utf8StringViewTest
{
    private final Utf8StringView view = new Utf8StringView();
    private final DecodedStringView decoded = new DecodedStringView();
    private final SegmentTransferTarget target = new SegmentTransferTarget();

    @Test
    void shouldReadAsciiInPlace()
    {
        wrap("a\"b c");

        assertEquals(5, view.length());
        assertEquals('"', view.charAt(1));
        assertEquals("\"b ", view.subSequence(1, 4).toString());
        assertEquals("a\"b c", view.toString());
    }

    @Test
    void shouldTransferPlainAsciiAsTheBytesItWraps()
    {
        wrap("plain text");

        assertEquals(5, view.transferLength(2, 7));
        assertEquals("ain t", transferred(2, 7));
    }

    @Test
    void shouldEscapeWhatAsciiNeedsEscaped()
    {
        wrap("a\"b\\c\n\u0001");

        assertEquals("a\\\"b\\\\c\\n\\u0001", transferred(0, 7));
        assertEquals(15, view.transferLength(0, 7));
    }

    @Test
    void shouldReadMultiByteChars()
    {
        final String text = "café € 😀!";

        wrap(text);

        assertEquals(text.length(), view.length());
        assertEquals(text, view.toString());
        assertEquals(text.charAt(3), view.charAt(3));
        assertEquals(text.charAt(7), view.charAt(7));
        assertEquals(text.substring(2, 10), view.subSequence(2, 10).toString());
    }

    @Test
    void shouldReadBackwardsFromTheStart()
    {
        final String text = "é€😀z";

        wrap(text);

        assertEquals('z', view.charAt(4));
        assertEquals('é', view.charAt(0));
        assertEquals('€', view.charAt(1));
    }

    @Test
    void shouldReadMalformedBytesAsReplacement()
    {
        final byte[] bytes = {'a', (byte) 0x80, 'b', (byte) 0xe2, (byte) 0x82, 'c', (byte) 0xff};

        view.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);

        assertEquals("a�b��c�", view.toString());
    }

    @Test
    void shouldTransferTheSameAsTheCharsItReads()
    {
        final Random random = new Random(7);

        for (int round = 0; round < 500; round++)
        {
            final String text = randomText(random);

            wrap(text);

            assertEquals(text, view.toString());

            final int from = text.isEmpty() ? 0 : random.nextInt(text.length() + 1);
            final int to = from + random.nextInt(text.length() - from + 1);

            if (from > 0 && from < text.length() && Character.isLowSurrogate(text.charAt(from)) &&
                Character.isHighSurrogate(text.charAt(from - 1)))
            {
                continue;
            }

            if (to > 0 && to < text.length() && Character.isHighSurrogate(text.charAt(to - 1)) &&
                Character.isLowSurrogate(text.charAt(to)))
            {
                continue;
            }

            decoded.wrap(text);

            assertEquals(decoded.transferLength(from, to), view.transferLength(from, to));
            assertEquals(transferred(decoded, from, to), transferred(view, from, to));
        }
    }

    @Test
    void shouldRejectARangeOutsideTheValue()
    {
        wrap("abc");

        assertThrows(IndexOutOfBoundsException.class, () -> view.charAt(3));
        assertThrows(IndexOutOfBoundsException.class, () -> view.subSequence(2, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> view.transferLength(1, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> view.transferTo(2, 1, target));
    }

    @Test
    void shouldWrapASliceOfTheBuffer()
    {
        final byte[] bytes = "xxéyy".getBytes(UTF_8);

        view.wrap(new UnsafeBufferEx(bytes), 2, 2);

        assertEquals("é", view.toString());
    }

    private void wrap(
        String text)
    {
        final byte[] bytes = text.getBytes(UTF_8);

        view.wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    private String transferred(
        int from,
        int to)
    {
        return transferred(view, from, to);
    }

    private String transferred(
        CharSequenceEx chars,
        int from,
        int to)
    {
        final byte[] out = new byte[chars.transferLength(from, to)];

        final int length = chars.transferTo(from, to, target.wrap(MemorySegment.ofArray(out), 0));

        assertEquals(out.length, length);

        return new String(out, UTF_8);
    }

    private static String randomText(
        Random random)
    {
        final String alphabet = "abc \"\\\n\u0001é€😀";
        final StringBuilder text = new StringBuilder();

        for (int count = random.nextInt(12); count > 0; count--)
        {
            final int pick = random.nextInt(alphabet.length());
            final char c = alphabet.charAt(pick);

            if (Character.isHighSurrogate(c))
            {
                text.append(c).append(alphabet.charAt(pick + 1));
            }
            else if (!Character.isLowSurrogate(c))
            {
                text.append(c);
            }
        }

        return text.toString();
    }
}
