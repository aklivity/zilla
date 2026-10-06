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
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

public class StringsTest
{
    private void assertUtf8Length(
        String value)
    {
        int expected = value.getBytes(UTF_8).length;
        int actual = Strings.utf8Length(value);
        assertEquals(expected, actual, () -> "utf8Length mismatch for \"" + value + "\"");
    }

    @Test
    public void shouldComputeUtf8LengthForEmptyString()
    {
        assertUtf8Length("");
    }

    @Test
    public void shouldComputeUtf8LengthForAsciiString()
    {
        assertUtf8Length("events");
    }

    @Test
    public void shouldComputeUtf8LengthForTwoByteCharacters()
    {
        assertUtf8Length("café");
    }

    @Test
    public void shouldComputeUtf8LengthForThreeByteCharacters()
    {
        assertUtf8Length("日本");
    }

    @Test
    public void shouldComputeUtf8LengthForSurrogatePair()
    {
        assertUtf8Length("🎉");
    }

    @Test
    public void shouldComputeUtf8LengthForMixedContent()
    {
        assertUtf8Length("🎉-日本-café");
    }

    @Test
    public void shouldComputeUtf8LengthForLoneHighSurrogate()
    {
        assertUtf8Length("a\uD83Db");
    }

    @Test
    public void shouldComputeUtf8LengthForLoneLowSurrogate()
    {
        assertUtf8Length("a\uDC00b");
    }

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

    private static void assertUtf8Valid(
        byte... bytes)
    {
        assertEquals(decodes(bytes), Strings.utf8Valid(MemorySegment.ofArray(bytes), 0, bytes.length),
            () -> "utf8Valid mismatch for " + HexFormat.of().formatHex(bytes));
    }

    @Test
    public void shouldAcceptEmptyAndAscii()
    {
        assertTrue(Strings.utf8Valid(MemorySegment.ofArray(new byte[0]), 0, 0));
        assertUtf8Valid("Hello, world".getBytes(UTF_8));
    }

    @Test
    public void shouldAcceptMultiByteContent()
    {
        assertUtf8Valid("🎉-日本-café".getBytes(UTF_8));
    }

    @Test
    public void shouldRejectInvalidContinuation()
    {
        assertFalse(Strings.utf8Valid(MemorySegment.ofArray(new byte[] {(byte) 0xc3, 0x28}), 0, 2));
    }

    @Test
    public void shouldRejectTruncatedSequence()
    {
        assertFalse(Strings.utf8Valid(MemorySegment.ofArray(new byte[] {'a', (byte) 0xe2, (byte) 0x82}), 0, 3));
    }

    @Test
    public void shouldRejectOverlongAndSurrogateAndOutOfRange()
    {
        assertUtf8Valid((byte) 0xc0, (byte) 0x80);
        assertUtf8Valid((byte) 0xe0, (byte) 0x80, (byte) 0x80);
        assertUtf8Valid((byte) 0xed, (byte) 0xa0, (byte) 0x80);
        assertUtf8Valid((byte) 0xf0, (byte) 0x80, (byte) 0x80, (byte) 0x80);
        assertUtf8Valid((byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80);
        assertUtf8Valid((byte) 0xf5, (byte) 0x80, (byte) 0x80, (byte) 0x80);
    }

    @Test
    public void shouldMatchDecoderForEveryOneAndTwoByteSequence()
    {
        for (int first = 0; first < 256; first++)
        {
            assertUtf8Valid((byte) first);

            for (int second = 0; second < 256; second++)
            {
                assertUtf8Valid((byte) first, (byte) second);
            }
        }
    }

    @Test
    public void shouldMatchDecoderForThreeAndFourByteSequenceBoundaries()
    {
        final int[] trails = {0x00, 0x7f, 0x80, 0x8f, 0x90, 0x9f, 0xa0, 0xbf, 0xc0};

        for (int lead = 0xe0; lead <= 0xf4; lead++)
        {
            for (int second : trails)
            {
                for (int third : trails)
                {
                    assertUtf8Valid((byte) lead, (byte) second, (byte) third);

                    for (int fourth : trails)
                    {
                        assertUtf8Valid((byte) lead, (byte) second, (byte) third, (byte) fourth);
                    }
                }
            }
        }
    }

    @Test
    public void shouldValidateOnlyWithinOffsetAndLimit()
    {
        final byte[] bytes = {(byte) 0xff, 'o', 'k', (byte) 0xc3};
        final MemorySegment segment = MemorySegment.ofArray(bytes);

        assertTrue(Strings.utf8Valid(segment, 1, 3));
        assertFalse(Strings.utf8Valid(segment, 0, 3));
        assertFalse(Strings.utf8Valid(segment, 1, 4));
    }
}
