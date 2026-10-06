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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

public class MemorySegmentsTest
{
    private static boolean decodes(
        byte[] bytes)
    {
        final CharsetDecoder decoder = UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        boolean valid;
        try
        {
            decoder.decode(ByteBuffer.wrap(bytes));
            valid = true;
        }
        catch (CharacterCodingException ex)
        {
            valid = false;
        }
        return valid;
    }

    private static boolean extendable(
        byte[] tail)
    {
        final int[] continuations = {0x80, 0x8f, 0x90, 0x9f, 0xa0, 0xbf};
        boolean found = false;

        for (int count = 1; !found && count <= 3; count++)
        {
            final int combinations = (int) Math.pow(continuations.length, count);

            for (int combination = 0; !found && combination < combinations; combination++)
            {
                final byte[] extended = Arrays.copyOf(tail, tail.length + count);
                int remaining = combination;

                for (int index = 0; index < count; index++)
                {
                    extended[tail.length + index] = (byte) continuations[remaining % continuations.length];
                    remaining /= continuations.length;
                }

                found = decodes(extended);
            }
        }

        return found;
    }

    private static int utf8Bytes(
        byte[] bytes)
    {
        return MemorySegments.utf8Bytes(MemorySegment.ofArray(bytes), 0, bytes.length);
    }

    private static int utf8Bytes(
        int... values)
    {
        final byte[] bytes = new byte[values.length];

        for (int index = 0; index < values.length; index++)
        {
            bytes[index] = (byte) values[index];
        }

        return utf8Bytes(bytes);
    }

    private static void assertMatchesDecoder(
        byte... bytes)
    {
        final int length = utf8Bytes(bytes);
        final String hex = HexFormat.of().formatHex(bytes);

        if (decodes(bytes))
        {
            assertEquals(bytes.length, length, () -> "length mismatch for " + hex);
        }
        else if (length >= 0)
        {
            final byte[] tail = Arrays.copyOfRange(bytes, length, bytes.length);

            assertTrue(decodes(Arrays.copyOf(bytes, length)), () -> "complete prefix invalid for " + hex);
            assertTrue(tail.length >= 1 && tail.length <= 3, () -> "tail length for " + hex);
            assertTrue(extendable(tail), () -> "tail is not a valid prefix for " + hex);
        }
        else
        {
            assertFalse(extendable(bytes), () -> "malformed but extendable for " + hex);
        }
    }

    @Test
    public void shouldMeasureEmptyAndAscii()
    {
        assertEquals(0, utf8Bytes());
        assertEquals(12, utf8Bytes("Hello, world".getBytes(UTF_8)));
    }

    @Test
    public void shouldMeasureMultiByteContent()
    {
        final byte[] bytes = "🎉-日本-café".getBytes(UTF_8);

        assertEquals(bytes.length, utf8Bytes(bytes));
    }

    @Test
    public void shouldReportCompleteCharactersAndValidTail()
    {
        final byte[] bytes = {'c', 'a', 'f', (byte) 0xc3};
        final MemorySegment segment = MemorySegment.ofArray(bytes);

        assertEquals(3, MemorySegments.utf8Bytes(segment, 0, 4));
        assertEquals(1, MemorySegments.utf8Bytes(segment, 0, 1));
    }

    @Test
    public void shouldMeasureSeamOfRetainedTailAndNextFragment()
    {
        assertEquals(2, utf8Bytes((byte) 0xc3, (byte) 0xa9));
    }

    @Test
    public void shouldRejectMalformedContinuation()
    {
        assertEquals(-1, utf8Bytes((byte) 0xc3, 0x28));
        assertEquals(-1, utf8Bytes((byte) 0xe2, 0x28));
        assertEquals(-1, utf8Bytes('a', (byte) 0xe2, (byte) 0x82, 0x28));
    }

    @Test
    public void shouldRejectOverlongSurrogateAndOutOfRange()
    {
        assertEquals(-1, utf8Bytes((byte) 0xc0, (byte) 0x80));
        assertEquals(-1, utf8Bytes((byte) 0xe0, (byte) 0x80, (byte) 0x80));
        assertEquals(-1, utf8Bytes((byte) 0xed, (byte) 0xa0, (byte) 0x80));
        assertEquals(-1, utf8Bytes((byte) 0xf0, (byte) 0x80, (byte) 0x80, (byte) 0x80));
        assertEquals(-1, utf8Bytes((byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80));
        assertEquals(-1, utf8Bytes((byte) 0xf5, (byte) 0x80, (byte) 0x80, (byte) 0x80));
    }

    @Test
    public void shouldMeasureOnlyWithinOffsetAndLength()
    {
        final MemorySegment segment = MemorySegment.ofArray(new byte[] {(byte) 0xff, 'o', 'k', (byte) 0xc3});

        assertEquals(2, MemorySegments.utf8Bytes(segment, 1, 2));
        assertEquals(-1, MemorySegments.utf8Bytes(segment, 0, 3));
        assertEquals(2, MemorySegments.utf8Bytes(segment, 1, 3));
    }

    @Test
    public void shouldMatchDecoderForEveryOneAndTwoByteSequence()
    {
        final int[] seconds = {0x00, 0x28, 0x7f, 0x80, 0x8f, 0x90, 0x9f, 0xa0, 0xbf, 0xc0, 0xc2, 0xe0, 0xf0, 0xff};

        for (int first = 0; first < 256; first++)
        {
            assertMatchesDecoder((byte) first);

            for (int second : seconds)
            {
                assertMatchesDecoder((byte) first, (byte) second);
            }
        }
    }

    @Test
    public void shouldMatchDecoderForThreeAndFourByteBoundaries()
    {
        final int[] trails = {0x00, 0x80, 0x8f, 0x90, 0x9f, 0xa0, 0xbf, 0xc0};

        for (int lead = 0xe0; lead <= 0xf4; lead++)
        {
            for (int second : trails)
            {
                for (int third : trails)
                {
                    assertMatchesDecoder((byte) lead, (byte) second, (byte) third);

                    for (int fourth : trails)
                    {
                        assertMatchesDecoder((byte) lead, (byte) second, (byte) third, (byte) fourth);
                    }
                }
            }
        }
    }
}
