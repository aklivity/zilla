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
package io.aklivity.zilla.runtime.common.lang;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.MemorySegment;
import java.util.Random;

import org.junit.jupiter.api.Test;

public class StringViewTest
{
    private final StringView view = StringView.of(UTF_8);
    private final SegmentTransferTarget target = new SegmentTransferTarget();

    @Test
    void shouldOnlySupportUtf8()
    {
        assertThrows(IllegalArgumentException.class, () -> StringView.of(ISO_8859_1));
    }

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
    void shouldTransferBytesWithoutEscapes()
    {
        wrap("a\"b\\c\n\u0001");

        assertEquals("a\"b\\c\n\u0001", transferred(0, 7));
        assertEquals(7, view.transferLength(0, 7));
        assertEquals("b\\c", transferred(2, 5));
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
        wrap("é€😀z");

        assertEquals('z', view.charAt(4));
        assertEquals('é', view.charAt(0));
        assertEquals('€', view.charAt(1));
    }

    @Test
    void shouldReadMalformedBytesAsReplacement()
    {
        final byte[] bytes = {'a', (byte) 0x80, 'b', (byte) 0xe2, (byte) 0x82, 'c', (byte) 0xff};

        view.wrap(MemorySegment.ofArray(bytes), 0, bytes.length);

        assertEquals("a�b��c�", view.toString());
    }

    @Test
    void shouldTransferTheBytesOfTheCharsItReads()
    {
        final Random random = new Random(7);
        final String alphabet = "abc \"\\\n\u0001é€😀";

        for (int round = 0; round < 500; round++)
        {
            final StringBuilder builder = new StringBuilder();

            for (int count = random.nextInt(12); count > 0; count--)
            {
                final int pick = random.nextInt(alphabet.length());
                final char c = alphabet.charAt(pick);

                if (Character.isHighSurrogate(c))
                {
                    builder.append(c).append(alphabet.charAt(pick + 1));
                }
                else if (!Character.isLowSurrogate(c))
                {
                    builder.append(c);
                }
            }

            final String text = builder.toString();

            wrap(text);

            assertEquals(text, view.toString());

            final int from = random.nextInt(text.length() + 1);
            final int to = from + random.nextInt(text.length() - from + 1);

            final boolean splits = from > 0 && from < text.length() && Character.isLowSurrogate(text.charAt(from)) ||
                to > 0 && to < text.length() && Character.isHighSurrogate(text.charAt(to - 1));

            if (!splits)
            {
                final String expected = text.substring(from, to);

                assertEquals(expected.getBytes(UTF_8).length, view.transferLength(from, to));
                assertEquals(expected, transferred(from, to));
            }
        }
    }

    @Test
    void shouldRejectARangeThatSplitsACharacter()
    {
        wrap("a😀b");

        assertThrows(IllegalArgumentException.class, () -> view.transferLength(0, 2));
        assertThrows(IllegalArgumentException.class, () -> view.transferTo(2, 3, target));
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
    void shouldWrapASliceOfTheMemory()
    {
        final byte[] bytes = "xxéyy".getBytes(UTF_8);

        view.wrap(MemorySegment.ofArray(bytes), 2, 2);

        assertEquals("é", view.toString());
        assertEquals("é", transferred(0, 1));
    }

    private void wrap(
        String text)
    {
        final byte[] bytes = text.getBytes(UTF_8);

        view.wrap(MemorySegment.ofArray(bytes), 0, bytes.length);
    }

    private String transferred(
        int from,
        int to)
    {
        final byte[] out = new byte[view.transferLength(from, to)];

        final int length = view.transferTo(from, to, target.wrap(MemorySegment.ofArray(out), 0));

        assertEquals(out.length, length);

        return new String(out, UTF_8);
    }
}
